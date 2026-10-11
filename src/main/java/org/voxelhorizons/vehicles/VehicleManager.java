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
    private final Map<UUID,VehicleInstance> instances=new LinkedHashMap<UUID,VehicleInstance>();
    private final Map<UUID,UUID> entityIndex=new HashMap<UUID,UUID>();
    // Dedicated hot path: never scan unloaded or parked instances each server tick.
    private final Map<UUID,VehicleInstance> occupied=new LinkedHashMap<UUID,VehicleInstance>();
    private Map<ContentID,VehicleDefinition> definitions=Collections.emptyMap();
    private long revision=-1;
    private long ticks;
    private boolean dirty;

    VehicleManager(VoxelVehicles plugin,VoxelCore core){
        this.plugin=plugin;this.core=core;
        this.renderer=new VehicleRenderer(plugin);
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
                synchronizeLoaded();
                cleanupLoadedOrphans();
            }
        },cleanupTicks,cleanupTicks);
    }
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

    /** Reconciles all currently rendered vehicles with the latest VoxelCore models. */
    public void synchronizeLoaded(){
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
            }else{
                try{renderer.refresh(v,d);}catch(Exception e){
                    plugin.getLogger().warning("Could not refresh "+v.id+": "+e.getMessage());
                }
            }
        }
    }

    public Optional<VehicleInstance> spawn(ContentID type,UUID owner,Location at){
        return spawn(type,owner,at,null);
    }

    /** Copies VoxelCore's mutable per-stack color before consuming the source item. */
    public Optional<VehicleInstance> spawn(ContentID type,UUID owner,Location at,ItemStack source){
        VehicleDefinition d=definitions.get(type);
        if(d==null||at.getWorld()==null||!at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))
            return Optional.empty();
        Location where=at.clone();where.setPitch(0);
        if(!VehicleCollision.clear(where,d))return Optional.empty();
        Integer dyeColor=null;
        if(source!=null&&core.getItemManager().isDyeable(source))
            dyeColor=core.getItemManager().getDyeColor(source).orElse(null);
        VehicleInstance v=new VehicleInstance(UUID.randomUUID(),type,owner,where,dyeColor);
        try{activate(v,d);}catch(RuntimeException e){
            plugin.getLogger().warning("Vehicle spawn failed: "+e.getMessage());return Optional.empty();
        }
        instances.put(v.id,v);
        save();
        return Optional.of(v);
    }

    public boolean remove(VehicleInstance v){
        if(instances.remove(v.id)==null)return false;
        occupied.remove(v.id);
        deindex(v);renderer.deactivate(v);save();return true;
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
        renderer.activate(v,d);
        index(v);
    }
    private void index(VehicleInstance v){
        if(v.display!=null)entityIndex.put(v.display.getUniqueId(),v.id);
        if(v.hitbox!=null)entityIndex.put(v.hitbox.getUniqueId(),v.id);
        if(v.seat!=null)entityIndex.put(v.seat.getUniqueId(),v.id);
    }
    private void deindex(VehicleInstance v){
        if(v.display!=null)entityIndex.remove(v.display.getUniqueId());
        if(v.hitbox!=null)entityIndex.remove(v.hitbox.getUniqueId());
        if(v.seat!=null)entityIndex.remove(v.seat.getUniqueId());
    }
    private boolean inChunk(Location loc,Chunk chunk){
        return loc.getWorld().equals(chunk.getWorld())&&(loc.getBlockX()>>4)==chunk.getX()
                &&(loc.getBlockZ()>>4)==chunk.getZ();
    }

    private void tick(){
        if(core.getContentRuntime().current().revision()!=revision) {
            try{refreshDefinitions();}catch(RuntimeException e){
                plugin.getLogger().warning("Vehicle definitions reload rejected: "+e.getMessage());
                revision=core.getContentRuntime().current().revision();
            }
        }
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
            VehiclePhysics.advance(v.motion,d,v.input);
            double dx=VehiclePhysics.dx(v.motion.yaw)*v.motion.speed;
            double dz=VehiclePhysics.dz(v.motion.yaw)*v.motion.speed;
            double dy=0;
            if(d.type==VehicleDefinition.Type.PLANE) {
                dy=v.input.jump?d.climbSpeed:v.input.sneak?-d.climbSpeed:0;
                dx*=d.flySpeed/d.speed;dz*=d.flySpeed/d.speed;
            }else{
                // Gravity only when unsupported. Avoid loading chunks under the vehicle.
                Location below=v.location.clone().add(0,-0.12,0);
                if(VehicleCollision.clear(below,d))dy=-0.12;
            }
            Location old=v.location;
            Location next=VehicleCollision.step(v,d,dx,dy,dz,
                    Math.max(0.05,plugin.getConfig().getDouble("physics.max-substep",0.25)));
            next.setYaw(v.motion.yaw);
            if(!old.equals(next)){
                v.location=next;
                dirty=true;
                renderer.move(v,d);
                if(v.driver==null){
                    deindex(v);renderer.unseat(v);index(v);iter.remove();
                }
            }
        }
        int interval=Math.max(20,plugin.getConfig().getInt("physics.autosave-ticks",200));
        if(++ticks%interval==0&&dirty)save();
    }
    public void save(){
        try{store.save(instances);dirty=false;}catch(IOException e){plugin.getLogger().severe("Vehicle save failed: "+e.getMessage());}
    }
    void shutdown(){
        save();
        for(VehicleInstance v:instances.values())renderer.deactivate(v);
        occupied.clear();entityIndex.clear();
    }
}
