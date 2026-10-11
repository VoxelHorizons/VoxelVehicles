package org.voxelhorizons.vehicles;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** One in-memory registry and one server-thread tick loop. No per-vehicle tasks. */
public final class VehicleManager {
    private final VoxelVehicles plugin;
    private final VoxelCore core;
    private final VehicleStore store;
    private final VehicleRenderer renderer;
    private final VehicleParticles particles;
    private final VehiclePerformance performance=new VehiclePerformance();
    private VehicleInventories inventories;
    private final Map<UUID,VehicleInstance> instances=new LinkedHashMap<UUID,VehicleInstance>();
    private final Map<UUID,UUID> entityIndex=new HashMap<UUID,UUID>();
    // Dedicated hot path: never scan unloaded or parked instances each server tick.
    private final Map<UUID,VehicleInstance> occupied=new LinkedHashMap<UUID,VehicleInstance>();
    // Parked/unloaded vehicles without emitters never enter the hot effect loop.
    private final Map<UUID,VehicleInstance> particleSources=new LinkedHashMap<UUID,VehicleInstance>();
    private Map<ContentID,VehicleDefinition> definitions=Collections.emptyMap();
    private long revision=-1;
    private long ticks;
    private boolean dirty;

    VehicleManager(VoxelVehicles plugin,VoxelCore core){
        this.plugin=plugin;this.core=core;
        this.renderer=new VehicleRenderer(plugin);
        this.particles=new VehicleParticles(plugin);
        this.store=new VehicleStore(new File(plugin.getDataFolder(),"vehicles.yml"));
    }

    void start(){
        refreshDefinitions();
        store.load(instances);
        for(World world:Bukkit.getWorlds())for(Chunk chunk:world.getLoadedChunks())loadChunk(chunk);
        plugin.getServer().getScheduler().runTaskTimer(plugin,new Runnable(){
            @Override public void run(){tick();}
        },1L,1L);
        int cleanupTicks=Math.max(100,plugin.getConfig().getInt("maintenance.cleanup-ticks",600));
        plugin.getServer().getScheduler().runTaskTimer(plugin,new Runnable(){
            @Override public void run(){
                // Maintenance only repairs missing entities. Do NOT rebuild
                // every healthy mounted ItemDisplay or recreate all item
                // models every 30 seconds. Explicit /vv sync and VoxelCore
                // content revision changes still request a full refresh.
                repairLoaded();
                cleanupLoadedOrphans();
            }
        },cleanupTicks,cleanupTicks);
    }
    void attachInventories(VehicleInventories inventories){this.inventories=inventories;}
    VehicleInventories inventories(){return inventories;}
    public Map<ContentID,VehicleDefinition> definitions(){return Collections.unmodifiableMap(definitions);}
    public Collection<VehicleInstance> instances(){return Collections.unmodifiableCollection(instances.values());}
    public Optional<VehicleDefinition> definition(ContentID id){return Optional.ofNullable(definitions.get(id));}
    public Optional<VehicleInstance> byEntity(Entity e){
        UUID id=entityIndex.get(e.getUniqueId());
        return Optional.ofNullable(id==null?null:instances.get(id));
    }
    public Optional<VehicleInstance> byId(UUID id){return Optional.ofNullable(instances.get(id));}

    public void refreshDefinitions(){
        Map<ContentID,VehicleDefinition> next=new LinkedHashMap<ContentID,VehicleDefinition>();
        for(ItemDefinition item:core.getItemRegistry().entries().values()){
            if(item.abstractDefinition())continue;
            Optional<VehicleDefinition> found=VehicleDefinition.parse(item);
            if(found.isPresent())next.put(item.id(),found.get());
        }
        definitions=next;
        revision=core.getContentRuntime().current().revision();
        synchronizeLoaded();
    }

    /** Explicit sync/content reload: reapply models and refresh all rendered vehicles. */
    public void synchronizeLoaded(){reconcileLoaded(true);}

    /** Periodic maintenance: repair only missing renderers, never refresh healthy mounts. */
    private void repairLoaded(){reconcileLoaded(false);}

    private void reconcileLoaded(boolean fullRefresh){
        for(VehicleInstance v:instances.values()){
            VehicleDefinition d=definitions.get(v.type);
            if(d==null){
                if(v.display!=null){occupied.remove(v.id);deindex(v);renderer.deactivate(v);}
                continue;
            }
            if(v.location.getWorld()==null||!v.location.getWorld().isChunkLoaded(
                    v.location.getBlockX()>>4,v.location.getBlockZ()>>4))continue;
            if(v.display==null||!v.display.isValid()||v.hitbox==null||!v.hitbox.isValid()){
                occupied.remove(v.id);
                deindex(v);
                renderer.deactivate(v);
                try{activate(v,d);}catch(RuntimeException e){
                    plugin.getLogger().warning("Could not repair "+v.id+": "+e.getMessage());
                }
            }else if(fullRefresh){
                try{
                    VehicleSuspension.initialize(v,d);
                    deindex(v);
                    renderer.refresh(v,d);
                }catch(Exception e){
                    plugin.getLogger().warning("Could not refresh "+v.id+": "+e.getMessage());
                }finally{
                    index(v);
                }
            }
        }
    }

    /** A success or an actionable placement failure; used by item clicks and admin commands. */
    public static final class SpawnResult {
        public final VehicleInstance vehicle;
        public final String failure;
        private SpawnResult(VehicleInstance vehicle,String failure){
            this.vehicle=vehicle;this.failure=failure;
        }
        public boolean successful(){return vehicle!=null;}
        public Optional<VehicleInstance> optional(){return Optional.ofNullable(vehicle);}
        private static SpawnResult success(VehicleInstance v){return new SpawnResult(v,null);}
        private static SpawnResult failure(String message){return new SpawnResult(null,message);}
    }

    public Optional<VehicleInstance> spawn(ContentID type,UUID owner,Location at){
        return trySpawn(type,owner,at,null).optional();
    }

    public Optional<VehicleInstance> spawn(ContentID type,UUID owner,Location at,ItemStack source){
        return trySpawn(type,owner,at,source).optional();
    }

    /** Captures mutable VoxelCore color and reports the exact reason a spawn fails. */
    public SpawnResult trySpawn(ContentID type,UUID owner,Location at,ItemStack source){
        VehicleDefinition d=definitions.get(type);
        if(d==null)return SpawnResult.failure("Unknown vehicle "+type
                +". Check /vv list and verify /vc admin content reload succeeded.");
        if(at==null||at.getWorld()==null)
            return SpawnResult.failure("The placement position has no loaded world.");
        if(!at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))
            return SpawnResult.failure("The vehicle origin chunk is not loaded.");
        Location where=at.clone();where.setPitch(0);
        if(!VehicleCollision.clear(where,d))
            return SpawnResult.failure(VehicleCollision.blockedReason(where,d));
        Integer dyeColor=null;
        try{
            if(source!=null&&core.getItemManager().isDyeable(source))
                dyeColor=core.getItemManager().getDyeColor(source).orElse(null);
            VehicleInstance v=new VehicleInstance(UUID.randomUUID(),type,owner,where,dyeColor);
            activate(v,d);
            instances.put(v.id,v);
            save();
            return SpawnResult.success(v);
        }catch(RuntimeException e){
            Throwable cause=e;
            while(cause.getCause()!=null&&cause.getCause()!=cause)cause=cause.getCause();
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Vehicle "+type+" could not spawn at "+where+"; cause: "+cause,e);
            return SpawnResult.failure("Vehicle renderer failed: "+
                    (cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage())
                    +". See server logs.");
        }
    }

    public boolean remove(VehicleInstance v){
        if(inventories!=null)inventories.captureOpen();
        // Prevent dropping a vehicle item while deleting its cargo.
        if(v.hasStoredItems()||v.chestInstalled())return false;
        if(instances.remove(v.id)==null)return false;
        if(inventories!=null)inventories.closeFor(v);
        occupied.remove(v.id);
        deindex(v);renderer.deactivate(v);save();return true;
    }
    /** Install only one chest on vehicles explicitly configured to accept upgrades. */
    boolean installChest(VehicleInstance v,ItemStack source){
        VehicleDefinition d=definitions.get(v.type);
        if(d==null||!d.inventory.enabled||!d.inventory.storageEnabled
                ||d.inventory.rows==0||!d.inventory.chest.enabled
                ||v.chestInstalled()||source==null||source.getType()!=d.inventory.chest.item
                ||source.getAmount()<1||!v.active())return false;
        ItemStack original=source.clone();original.setAmount(1);
        if(inventories!=null)inventories.closeFor(v);
        v.installedChest=original;
        if(!updateChestVisual(v,d)){
            v.installedChest=null;
            updateChestVisual(v,d);
            return false;
        }
        save();
        return true;
    }

    /** Never detach while cargo is present, including hidden/inactive slots. */
    ItemStack detachChest(VehicleInstance v){
        VehicleDefinition d=definitions.get(v.type);
        if(d==null||!d.inventory.chest.enabled||!v.chestInstalled())return null;
        if(inventories!=null)inventories.captureOpen();
        if(v.hasStoredItems())return null;
        if(inventories!=null)inventories.closeFor(v);
        ItemStack original=v.installedChest.clone();
        v.installedChest=null;
        if(!updateChestVisual(v,d)){
            v.installedChest=original;
            updateChestVisual(v,d);
            return null;
        }
        save();
        return original;
    }

    private boolean updateChestVisual(VehicleInstance v,VehicleDefinition d){
        if(!v.active())return true;
        try{
            deindex(v);
            renderer.refresh(v,d);
            return true;
        }catch(Exception error){
            plugin.getLogger().warning("Cannot update chest for "+v.id+": "+error.getMessage());
            return false;
        }finally{
            index(v);
        }
    }

    public boolean mount(VehicleInstance v,Player p){
        VehicleDefinition d=definitions.get(v.type);
        if(d==null||!v.active()||v.driver!=null||p.isInsideVehicle())return false;
        if(!d.publicAccess&&!v.owner.equals(p.getUniqueId())&&!p.hasPermission("voxelvehicles.admin"))return false;
        if(!renderer.seat(v,d,p))return false;
        index(v);
        occupied.put(v.id,v);
        return true;
    }
    public void input(Player p,VehiclePhysics.Input input){
        Entity seat=p.getVehicle();
        if(seat==null)return;
        VehicleInstance v=byEntity(seat).orElse(null);
        if(v!=null&&p.getUniqueId().equals(v.driver))v.input=input;
    }
    public void dismount(Player p){
        Entity seat=p.getVehicle();
        VehicleInstance v=seat==null?null:byEntity(seat).orElse(null);
        if(v==null)return;
        occupied.remove(v.id);
        deindex(v);
        renderer.unseat(v,definitions.get(v.type));
        index(v);
    }
    void loadChunk(Chunk chunk){
        // Clean only stale tagged entities; entityIndex protects our live entities.
        cleanupOrphans(chunk);
        for(VehicleInstance v:instances.values())if(inChunk(v.location,chunk)){
            VehicleDefinition d=definitions.get(v.type);
            if(d==null)continue;
            if(v.display==null||!v.display.isValid()||v.hitbox==null||!v.hitbox.isValid()){
                occupied.remove(v.id);
                deindex(v);
                renderer.deactivate(v);
                try{activate(v,d);}
                catch(RuntimeException error){plugin.getLogger().warning("Cannot restore "+v.id+": "+error.getMessage());}
            }
        }
    }
    void unloadChunk(Chunk chunk){
        for(VehicleInstance v:instances.values())if(v.active()&&inChunk(v.location,chunk)){
            occupied.remove(v.id);
            deindex(v);renderer.deactivate(v);
        }
        if(dirty)save();
    }

    /**
     * Remove only tagged entities not referenced by the current live renderer
     * index. Never load a chunk to perform maintenance.
     */
    public int cleanupLoadedOrphans(){
        int removed=0;
        for(World world:Bukkit.getWorlds())
            for(Chunk chunk:world.getLoadedChunks())removed+=cleanupOrphans(chunk);
        if(removed>0)plugin.getLogger().info("Removed "+removed+" orphaned vehicle entities.");
        return removed;
    }

    int cleanupOrphans(Chunk chunk){
        int removed=0;
        for(Entity entity:chunk.getEntities()){
            if(entity.getScoreboardTags().contains("voxelvehicles")
                    &&!entityIndex.containsKey(entity.getUniqueId())){
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Finds a click target even when an Interaction entity suppresses Bukkit damage events. */
    public Optional<VehicleInstance> targeted(Player player,double reach){
        Location eye=player.getEyeLocation();
        Vector ray=eye.getDirection().normalize();
        VehicleInstance nearest=null;
        double best=reach;
        for(VehicleInstance v:instances.values()){
            if(!v.active()||v.driver!=null||!v.location.getWorld().equals(eye.getWorld()))continue;
            VehicleDefinition d=definitions.get(v.type);
            if(d==null)continue;
            Location center=VehicleTransform.hitbox(v.location,d);
            double half=d.width*0.5;
            double[] min={center.getX()-half,center.getY()-d.height*0.5,center.getZ()-half};
            double[] max={center.getX()+half,center.getY()+d.height*0.5,center.getZ()+half};
            double[] pos={eye.getX(),eye.getY(),eye.getZ()};
            double[] dir={ray.getX(),ray.getY(),ray.getZ()};
            double lo=0,hi=best;
            boolean intersect=true;
            for(int axis=0;axis<3;axis++){
                if(Math.abs(dir[axis])<1.0e-8){
                    if(pos[axis]<min[axis]||pos[axis]>max[axis]){intersect=false;break;}
                }else{
                    double a=(min[axis]-pos[axis])/dir[axis];
                    double b=(max[axis]-pos[axis])/dir[axis];
                    if(a>b){double t=a;a=b;b=t;}
                    lo=Math.max(lo,a);
                    hi=Math.min(hi,b);
                    if(hi<lo){intersect=false;break;}
                }
            }
            if(intersect&&lo<best){nearest=v;best=lo;}
        }
        return Optional.ofNullable(nearest);
    }

    private void activate(VehicleInstance v,VehicleDefinition d){
        VehicleSuspension.initialize(v,d);
        renderer.activate(v,d);
        index(v);
    }
    private void index(VehicleInstance v){
        VehicleDefinition definition=definitions.get(v.type);
        if(v.active()&&definition!=null&&!definition.particles.isEmpty())
            particleSources.put(v.id,v);
        else particleSources.remove(v.id);
        if(v.display!=null)entityIndex.put(v.display.getUniqueId(),v.id);
        if(v.hitbox!=null)entityIndex.put(v.hitbox.getUniqueId(),v.id);
        if(v.seat!=null)entityIndex.put(v.seat.getUniqueId(),v.id);
        if(v.chestDisplay!=null)entityIndex.put(v.chestDisplay.getUniqueId(),v.id);
        if(v.chestHitbox!=null)entityIndex.put(v.chestHitbox.getUniqueId(),v.id);
    }
    private void deindex(VehicleInstance v){
        particleSources.remove(v.id);
        if(v.display!=null)entityIndex.remove(v.display.getUniqueId());
        if(v.hitbox!=null)entityIndex.remove(v.hitbox.getUniqueId());
        if(v.seat!=null)entityIndex.remove(v.seat.getUniqueId());
        if(v.chestDisplay!=null)entityIndex.remove(v.chestDisplay.getUniqueId());
        if(v.chestHitbox!=null)entityIndex.remove(v.chestHitbox.getUniqueId());
    }
    private boolean inChunk(Location loc,Chunk chunk){
        return loc.getWorld().equals(chunk.getWorld())&&(loc.getBlockX()>>4)==chunk.getX()
                &&(loc.getBlockZ()>>4)==chunk.getZ();
    }

    private void tick(){
        long started=System.nanoTime();
        int sent=0,avoided=0;
        if(core.getContentRuntime().current().revision()!=revision) {
            try{refreshDefinitions();}catch(RuntimeException e){
                plugin.getLogger().warning("Vehicle definitions reload rejected: "+e.getMessage());
                revision=core.getContentRuntime().current().revision();
            }
        }
        // One shape snapshot per world and server tick, shared between all
        // riders. Previously each vehicle reparsed the same terrain blocks.
        VehicleTerrain.Frame terrainFrame=new VehicleTerrain.Frame();
        double maxStep=Math.max(0.05,plugin.getConfig().getDouble("physics.max-substep",0.25));
        for(Iterator<VehicleInstance> iter=occupied.values().iterator();iter.hasNext();){
            VehicleInstance v=iter.next();
            if(!v.active()||v.driver==null){iter.remove();continue;}
            if(v.seat!=null&&v.driver!=null) {
                Player p=Bukkit.getPlayer(v.driver);
                if(p==null||!v.seat.isValid()||!v.seat.equals(p.getVehicle())){
                    deindex(v);renderer.unseat(v,definitions.get(v.type));index(v);
                    iter.remove();
                    continue;
                }
            }
            VehicleDefinition d=definitions.get(v.type);
            if(d==null){deindex(v);renderer.unseat(v,d);index(v);iter.remove();continue;}
            // All vehicles in this world reuse decoded shapes within the
            // current tick only. Edits become visible on the following tick.
            VehicleTerrain.Space terrain=terrainFrame.space(v.location.getWorld());
            Location old=v.location;
            Location next;
            if(d.type==VehicleDefinition.Type.PLANE){
                VehiclePhysics.advance(v.motion,d,v.input);
                double dx=VehiclePhysics.dx(v.motion.yaw)*v.motion.speed*d.flySpeed/d.speed;
                double dz=VehiclePhysics.dz(v.motion.yaw)*v.motion.speed*d.flySpeed/d.speed;
                double dy=v.input.jump?d.climbSpeed:v.input.sneak?-d.climbSpeed:0;
                next=VehicleCollision.step(v,d,dx,dy,dz,maxStep,terrain,false);
            }else{
                // While grounded: accelerate/steer and step up obstacles as before.
                // In the air: preserve horizontal momentum and let gravity
                // accumulate naturally, with no engine-driven air steering.
                boolean onGround=VehicleCollision.supported(v.location,d,terrain);
                if(onGround){
                    v.motion.grounded=true;
                    v.motion.verticalSpeed=0;
                    VehiclePhysics.advance(v.motion,d,v.input);
                }else{
                    v.motion.grounded=false;
                    VehiclePhysics.advanceAirborne(v.motion,d);
                }
                double dx=VehiclePhysics.dx(v.motion.yaw)*v.motion.speed;
                double dz=VehiclePhysics.dz(v.motion.yaw)*v.motion.speed;
                next=VehicleCollision.step(v,d,dx,0,dz,maxStep,terrain,onGround);
                boolean stillSupported=VehicleCollision.supported(next,d,terrain);
                if(stillSupported){
                    v.motion.grounded=true;
                    v.motion.verticalSpeed=0;
                }else{
                    v.motion.grounded=false;
                    v.motion.verticalSpeed=VehiclePhysics.fallSpeed(
                            v.motion.verticalSpeed,d.gravity,d.terminalVelocity);
                    VehicleCollision.FallResult fall=VehicleCollision.descend(next,d,
                            v.motion.verticalSpeed,maxStep,terrain);
                    next=fall.location;
                    if(fall.landed){
                        v.motion.grounded=true;
                        v.motion.verticalSpeed=0;
                    }
                }
            }
            next.setYaw(v.motion.yaw);
            if(!old.equals(next)){
                v.location=next;
                dirty=true;
                renderer.move(v,d);
                if(v.driver==null){
                    deindex(v);renderer.unseat(v,d);index(v);iter.remove();
                }
            }
            // Sample the four wheel positions each occupied tick. Only the
            // visual model changes, never the passenger or hitbox orientation.
            if(v.driver!=null&&VehicleSuspension.update(v,d,terrain))
                renderer.updateTilt(v,d);
            long teleportCounts=v.teleports.drainCounts();
            sent+=(int)(teleportCounts>>>32);
            avoided+=(int)teleportCounts;
        }
        // Effects use the same single server tick: no per-vehicle timers or entities.
        particles.tick(particleSources.values(),definitions,ticks);
        int interval=Math.max(20,plugin.getConfig().getInt("physics.autosave-ticks",200));
        if(++ticks%interval==0&&dirty)save();
        performance.record(System.nanoTime()-started,occupied.size(),
                terrainFrame.decodedBlocks(),terrainFrame.reusedBlocks(),sent,avoided);
    }

    String[] performanceReport(){
        int active=0;
        for(VehicleInstance v:instances.values())if(v.active())active++;
        return performance.lines(active,instances.size());
    }

    public void save(){
        if(inventories!=null)inventories.captureOpen();
        try{store.save(instances);dirty=false;}catch(IOException e){plugin.getLogger().severe("Vehicle save failed: "+e.getMessage());}
    }
    void shutdown(){
        if(inventories!=null)inventories.shutdown();
        save();
        for(VehicleInstance v:instances.values())renderer.deactivate(v);
        occupied.clear();particleSources.clear();entityIndex.clear();
    }
}
