package org.voxelhorizons.vehicles.bedrock;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

/**
 * Re-sends display entities so Geyser raises a fresh spawn event and applies the
 * current projected model. Uses Player#hideEntity/showEntity(Plugin,Entity)
 * (1.18+) by reflection because this plugin compiles against an older API;
 * item displays only exist on 1.19.4+. Hiding is keyed by this plugin, so other
 * plugins' visibility rules are preserved. Only Bedrock players are re-sent when
 * a local Geyser API is present; otherwise all viewers are (one-frame Java flicker).
 */
final class BedrockEntityResender {
    private final Plugin plugin;
    private final Method hide, show;
    private final Object geyserApi;
    private final Method isBedrock;

    private BedrockEntityResender(Plugin plugin,Method hide,Method show,Object geyserApi,Method isBedrock) {
        this.plugin=plugin;this.hide=hide;this.show=show;this.geyserApi=geyserApi;this.isBedrock=isBedrock;
    }

    static BedrockEntityResender create(Plugin plugin) {
        Method hide,show;
        try {
            hide=Player.class.getMethod("hideEntity",Plugin.class,Entity.class);
            show=Player.class.getMethod("showEntity",Plugin.class,Entity.class);
        } catch(NoSuchMethodException unsupported) {
            return null;
        }
        Object api=null; Method bedrock=null;
        try {
            Class<?> type=Class.forName("org.geysermc.geyser.api.GeyserApi",true,plugin.getClass().getClassLoader());
            api=type.getMethod("api").invoke(null);
            bedrock=type.getMethod("isBedrockPlayer",UUID.class);
        } catch(ReflectiveOperationException | LinkageError | RuntimeException absent) {
            api=null; bedrock=null; // proxy-hosted Geyser: re-send to every viewer
        }
        return new BedrockEntityResender(plugin,hide,show,api,bedrock);
    }

    /** Main thread only. */
    int resend(Collection<UUID> entities) {
        int sent=0;
        for(UUID id:entities) {
            Entity entity=plugin.getServer().getEntity(id);
            if(entity==null || !entity.isValid())continue;
            for(Player viewer:entity.getWorld().getPlayers()) {
                if(!bedrock(viewer))continue;
                try {
                    hide.invoke(viewer,plugin,entity);
                    show.invoke(viewer,plugin,entity);
                    sent++;
                } catch(ReflectiveOperationException failure) {
                    plugin.getLogger().warning("Bedrock model re-send failed for "+id+": "+failure);
                    return sent;
                }
            }
        }
        return sent;
    }

    private boolean bedrock(Player player) {
        if(isBedrock==null)return true;
        try { return Boolean.TRUE.equals(isBedrock.invoke(geyserApi,player.getUniqueId())); }
        catch(ReflectiveOperationException | RuntimeException failure) { return true; }
    }
}
