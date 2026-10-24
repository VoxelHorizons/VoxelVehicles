package org.voxelhorizons.vehicles.bedrock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Small, opt-in local/mounted Geyser data-file transport for render-only state.
 * Snapshot capture + JSON encode happen on Paper's main thread. Actual disk I/O
 * is asynchronous and single-sequence, so a late write cannot rewind the state.
 *
 * No gameplay data is sent and no Bedrock client action is trusted as authority.
 */
public final class BedrockProjectionPublisher {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    private final JavaPlugin plugin;
    private final Path output;
    private final String kind;
    private final BedrockEntityResender resender;
    private Map<UUID,String> lastModels=Collections.emptyMap();
    /** Model changes not yet re-sent; guarded by ioLock. */
    private final Set<UUID> pendingResend=new HashSet<UUID>();
    private final Object ioLock = new Object();
    private final AtomicLong sequence = new AtomicLong();
    private volatile boolean closed;
    private byte[] lastRequested;
    private long lastRequestTime;

    private BedrockProjectionPublisher(JavaPlugin plugin,Path output,String kind,BedrockEntityResender resender) {
        this.plugin=plugin;this.output=output;this.kind=kind;this.resender=resender;
    }

    public static BedrockProjectionPublisher start(JavaPlugin plugin,String kind,
                                                    Supplier<Map<String,Object>> snapshot) {
        if (!plugin.getConfig().getBoolean("bedrock.projection.enabled", true)) return null;
        String configured=plugin.getConfig().getString("bedrock.projection.geyser-data-directory","");
        Path geyser;
        if (configured!=null && !configured.trim().isEmpty()) {
            geyser=Paths.get(configured.trim()).toAbsolutePath().normalize();
        } else {
            Plugin local=plugin.getServer().getPluginManager().getPlugin("Geyser-Spigot");
            if (local==null || !local.isEnabled()) {
                plugin.getLogger().info("Bedrock state projection disabled: no local Geyser-Spigot "
                        +"(set bedrock.projection.geyser-data-directory to a mounted proxy folder).");
                return null;
            }
            geyser=local.getDataFolder().toPath().toAbsolutePath().normalize();
        }
        if (!Files.isDirectory(geyser) || Files.isSymbolicLink(geyser)) {
            plugin.getLogger().warning("Bedrock state projection not enabled: invalid Geyser directory "+geyser);
            return null;
        }
        if (!"furniture".equals(kind) && !"vehicles".equals(kind))
            throw new IllegalArgumentException("Unexpected projection category "+kind);
        BedrockProjectionPublisher publisher = new BedrockProjectionPublisher(plugin,
                geyser.resolve("voxelcore-projection").resolve(kind+".json"),kind,
                plugin.getConfig().getBoolean("bedrock.projection.resend-on-model-change",true)
                        ? BedrockEntityResender.create(plugin) : null);
        int ticks=Math.max(1,Math.min(200,plugin.getConfig().getInt("bedrock.projection.interval-ticks",5)));
        plugin.getServer().getScheduler().runTaskTimer(plugin,()->{
            try { publisher.submit(snapshot.get()); }
            catch (RuntimeException failure) {
                plugin.getLogger().warning("Bedrock "+kind+" snapshot failed: "+failure.getMessage());
            }
        },1L,ticks);
        plugin.getLogger().info("Bedrock "+kind+" visual state publishing every "+ticks
                +" ticks to "+publisher.output);
        return publisher;
    }

    private void submit(Map<String,Object> snapshot) {
        if (closed) return;
        byte[] bytes=JSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
        if (bytes.length>5_000_000) {
            plugin.getLogger().warning("Bedrock projection exceeds 5 MB, rejecting publication: "+output);
            return;
        }
        long now=System.currentTimeMillis();
        if (Arrays.equals(lastRequested,bytes) && now-lastRequestTime<10000L) return;
        lastRequested=bytes; lastRequestTime=now;
        Map<UUID,String> models=ProjectionModelChanges.visualModels(snapshot,kind);
        Set<UUID> changed=ProjectionModelChanges.changed(lastModels,models);
        lastModels=models;
        if (resender!=null && !changed.isEmpty())
            synchronized(ioLock) { pendingResend.addAll(changed); }
        long revision=sequence.incrementAndGet();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,()->{
            synchronized(ioLock) {
                if (closed || revision!=sequence.get()) return;
                try {
                    publishAtomically(output,bytes);
                    scheduleResend();
                }
                catch (IOException failure) {
                    plugin.getLogger().warning("Bedrock projection file update failed: "+failure.getMessage());
                }
            }
        });
    }

    /** Under ioLock, after the feed holding the new model is on disk, so Geyser's
     * spawn handler reads the new definition when the entity is re-sent. */
    private void scheduleResend() {
        if (pendingResend.isEmpty() || closed) return;
        final Set<UUID> batch=new HashSet<UUID>(pendingResend);
        pendingResend.clear();
        plugin.getServer().getScheduler().runTask(plugin,()->{
            if (closed) return;
            int sent=resender.resend(batch);
            if (sent>0) plugin.getLogger().fine("Re-sent "+sent+" Bedrock display views after model change");
        });
    }

    static void publishAtomically(Path output,byte[] data) throws IOException {
        Path directory=output.getParent();
        if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(output))
            throw new IOException("Unsafe Bedrock projection symlink "+output);
        Files.createDirectories(directory);
        Path staging=Files.createTempFile(directory,".voxelcore-",".tmp");
        try {
            Files.write(staging,data);
            try { Files.move(staging,output,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException notSupported) {
                Files.move(staging,output,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(staging); }
    }

    /** Remove the feed on a clean shutdown; a crashed server is handled by reader freshness. */
    public void close() {
        closed=true;
        sequence.incrementAndGet();
        synchronized(ioLock) {
            try { Files.deleteIfExists(output); }
            catch(IOException failure) {
                plugin.getLogger().warning("Could not remove stale Bedrock projection "+output+": "+failure.getMessage());
            }
        }
    }
}
