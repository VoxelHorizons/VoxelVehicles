package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;
import java.util.UUID;

/**
 * The ItemDisplay entity is permanently anchored at the configured seat.
 * Its geometry is shifted back to the visual/model origin by a LOCAL matrix.
 * Mounting never changes the display's world location or model transform.
 *
 * On modern servers a player rides the ItemDisplay directly: one movement
 * root, no extra seat entity and no mount-induced vertical jump.
 * An invisible ArmorStand is a compatibility fallback when a direct rider
 * is not accepted. The clickable Interaction entity remains independent.
 */
final class VehicleRenderer {
    private final VoxelVehicles plugin;
    private final VehicleChestRenderer chest;
    VehicleRenderer(VoxelVehicles plugin){this.plugin=plugin;this.chest=new VehicleChestRenderer(plugin);}

    void activate(VehicleInstance vehicle, VehicleDefinition definition){
        Location at=VehicleTransform.renderRoot(vehicle.location,definition);
        Entity display=null,hitbox=null;
        try{
            display=at.getWorld().spawnEntity(at,EntityType.valueOf("ITEM_DISPLAY"));
            applyFixedTransform(display);
            invokeOptional(display,"setViewRange",float.class,definition.viewDistance/64.0f);
            invokeOptional(display,"setTeleportDuration",int.class,interpolation());
            tag(display,vehicle.id);
            hitbox=at.getWorld().spawnEntity(VehicleTransform.hitbox(vehicle.location,definition),
                    EntityType.valueOf("INTERACTION"));
            tag(hitbox,vehicle.id);
            vehicle.display=display;
            vehicle.hitbox=hitbox;
            refresh(vehicle,definition);
        }catch(Exception error){
            if(display!=null)display.remove();
            if(hitbox!=null)hitbox.remove();
            chest.remove(vehicle);
            vehicle.display=null;vehicle.hitbox=null;
            throw new IllegalStateException("Could not spawn modern vehicle entities",error);
        }
    }

    /** Re-apply model, tint, offsets and interaction hitbox after content reload. */
    void refresh(VehicleInstance vehicle,VehicleDefinition definition) throws Exception{
        if(vehicle.display==null||!vehicle.display.isValid()
                ||vehicle.hitbox==null||!vehicle.hitbox.isValid())return;
        ContentID model=definition.model;
        if(definition.inventory.chest.enabled&&vehicle.chestInstalled()
                &&definition.inventory.chest.mode==VehicleChestDefinition.VisualMode.MODEL_SWAP)
            model=definition.inventory.chest.alternateModel;
        ItemStack item=plugin.core().getItemManager().createRenderItem(model);
        if(vehicle.dyeColor!=null&&plugin.core().getItemManager().isDyeable(item))
            item=plugin.core().getItemManager().setDyeColor(item,vehicle.dyeColor.intValue());
        invoke(vehicle.display,"setItemStack",ItemStack.class,item);
        applyFixedTransform(vehicle.display);
        invokeOptional(vehicle.display,"setViewRange",float.class,definition.viewDistance/64.0f);
        invokeOptional(vehicle.display,"setTeleportDuration",int.class,
                vehicle.seat!=null?mountedInterpolation():interpolation());
        invoke(vehicle.hitbox,"setInteractionWidth",float.class,(float)definition.width);
        invoke(vehicle.hitbox,"setInteractionHeight",float.class,(float)definition.height);
        invokeOptional(vehicle.hitbox,"setResponsive",boolean.class,true);
        applyMatrix(vehicle.display,definition,vehicle.visualPitch,vehicle.visualRoll);
        chest.refresh(vehicle,definition);
        move(vehicle,definition);
    }

    /** Visual suspension changes only the model matrix, never the passenger. */
    void updateTilt(VehicleInstance vehicle,VehicleDefinition definition){
        if(vehicle.display==null||!vehicle.display.isValid())return;
        try{
            invokeOptional(vehicle.display,"setInterpolationDuration",int.class,
                    Math.max(0,Math.min(5,plugin.getConfig().getInt("render.tilt-interpolation-ticks",2))));
            invokeOptional(vehicle.display,"setInterpolationDelay",int.class,-1);
            applyMatrix(vehicle.display,definition,vehicle.visualPitch,vehicle.visualRoll);
            chest.tilt(vehicle,definition);
        }catch(Exception error){
            plugin.getLogger().warning("Could not tilt vehicle model "+vehicle.id+": "+error.getMessage());
        }
    }

    /**
     * The same root coordinates are used parked, mounted and immediately after
     * a definition reload. There is no 'seat origin' correction on mounting.
     */
    void move(VehicleInstance vehicle,VehicleDefinition definition){
        Location root=VehicleTransform.renderRoot(vehicle.location,definition);
        if(vehicle.display!=null&&vehicle.display.isValid())
            vehicle.display.teleport(root);
        if(vehicle.seat!=null&&vehicle.seat!=vehicle.display
                &&vehicle.seat.isValid()&&!vehicle.linkedSeat){
            // Compatibility-only fallback for servers that cannot attach a
            // seat to an ItemDisplay.
            if(!vehicle.seat.teleport(root)){
                plugin.getLogger().warning("Seat teleport failed for "+vehicle.id);
                vehicle.seat.eject();
                vehicle.stop();
            }
        }
        if(vehicle.hitbox!=null&&vehicle.hitbox.isValid())
            vehicle.hitbox.teleport(VehicleTransform.hitbox(vehicle.location,definition));
        chest.move(vehicle,definition);
    }

    boolean seat(VehicleInstance v,VehicleDefinition d,Player player){
        if(v.seat!=null&&v.seat.isValid()||v.display==null||!v.display.isValid())return false;
        try{
            // Prefer the single-entity setup: the display is both the model
            // root and the mount, without an independent armor stand chasing it.
            if(v.display.addPassenger(player)){
                v.seat=v.display;
                v.linkedSeat=true;
                v.driver=player.getUniqueId();
                invokeOptional(v.display,"setTeleportDuration",int.class,mountedInterpolation());
                move(v,d);
                return true;
            }
        }catch(RuntimeException ex){
            plugin.getLogger().fine("Direct ItemDisplay seating rejected: "+ex.getMessage());
        }

        // Compatibility fallback: nested seat is still carried by the fixed
        // display root and never changes that root's position.
        ArmorStand stand=null;
        boolean linked=false;
        try{
            stand=(ArmorStand)v.location.getWorld().spawnEntity(
                    VehicleTransform.renderRoot(v.location,d),EntityType.ARMOR_STAND);
            stand.setVisible(false);stand.setGravity(false);stand.setSmall(true);
            stand.setMarker(true);tag(stand,v.id);
            linked=v.display.addPassenger(stand);
            if(!stand.addPassenger(player)){
                if(linked)v.display.removePassenger(stand);
                stand.remove();
                return false;
            }
            v.seat=stand;v.linkedSeat=linked;
            v.driver=player.getUniqueId();
            invokeOptional(v.display,"setTeleportDuration",int.class,mountedInterpolation());
            move(v,d);
            if(!linked)plugin.getLogger().warning("Vehicle "+v.id+
                    " is using independent fallback seating (display passengers unavailable).");
            return true;
        }catch(Exception ex){
            if(stand!=null){
                if(linked&&v.display!=null&&v.display.isValid())
                    v.display.removePassenger(stand);
                stand.eject();stand.remove();
            }
            v.seat=null;v.linkedSeat=false;v.stop();
            plugin.getLogger().warning("Unable to mount "+v.id+": "+ex.getMessage());
            return false;
        }
    }

    void unseat(VehicleInstance v,VehicleDefinition definition){
        if(v.seat!=null){
            if(v.seat==v.display){
                if(v.display!=null&&v.display.isValid())v.display.eject();
            }else{
                if(v.linkedSeat&&v.display!=null&&v.display.isValid())
                    v.display.removePassenger(v.seat);
                v.seat.eject();v.seat.remove();
            }
            v.seat=null;
        }
        v.linkedSeat=false;
        v.stop();
        if(definition!=null&&v.display!=null&&v.display.isValid()){
            invokeOptional(v.display,"setTeleportDuration",int.class,interpolation());
            move(v,definition);
        }
    }

    void deactivate(VehicleInstance v){
        unseat(v,null);
        chest.remove(v);
        if(v.display!=null){v.display.remove();v.display=null;}
        if(v.hitbox!=null){v.hitbox.remove();v.hitbox=null;}
    }

    private int interpolation(){
        return Math.max(0,Math.min(59,plugin.getConfig().getInt("render.interpolation-ticks",1)));
    }
    private int mountedInterpolation(){
        return clampMountedInterpolation(plugin.getConfig().getInt("render.mounted-interpolation-ticks",1));
    }
    static int clampMountedInterpolation(int ticks){return Math.max(1,Math.min(3,ticks));}

    private void tag(Entity e,UUID id){
        e.addScoreboardTag("voxelvehicles");e.addScoreboardTag("vv:"+id);
        e.setInvulnerable(true);e.setGravity(false);
        invokeOptional(e,"setPersistent",boolean.class,false);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void applyFixedTransform(Entity display)throws Exception{
        Class<?> kind=Class.forName("org.bukkit.entity.ItemDisplay$ItemDisplayTransform");
        Object fixed=Enum.valueOf((Class)kind.asSubclass(Enum.class),"FIXED");
        display.getClass().getMethod("setItemDisplayTransform",kind).invoke(display,fixed);
    }

    /**
     * Translate in display LOCAL coordinates from permanent seat anchor to
     * visual center; this is identical whether the tractor is occupied or not.
     * The translation is applied before scale so offsets remain world-block units.
     */
    private static void applyMatrix(Entity entity,VehicleDefinition d,double pitch,double roll)
            throws Exception{
        Class<?> matrix=Class.forName("org.joml.Matrix4f");
        Object value=matrix.getConstructor().newInstance();
        double[] translate=VehicleTransform.modelTranslation(d);
        matrix.getMethod("translate",float.class,float.class,float.class).invoke(
                value,(float)translate[0],(float)translate[1],(float)translate[2]);
        if(Math.abs(pitch)>0.001)
            matrix.getMethod("rotateX",float.class).invoke(value,(float)Math.toRadians(-pitch));
        if(Math.abs(roll)>0.001)
            matrix.getMethod("rotateZ",float.class).invoke(value,(float)Math.toRadians(roll));
        matrix.getMethod("scale",float.class,float.class,float.class).invoke(
                value,(float)d.scale,(float)d.scale,(float)d.scale);
        entity.getClass().getMethod("setTransformationMatrix",matrix).invoke(entity,value);
    }
    private static void invoke(Object entity,String name,Class<?> type,Object value)throws Exception{
        entity.getClass().getMethod(name,type).invoke(entity,value);
    }
    private static void invokeOptional(Object entity,String name,Class<?> type,Object value){
        try{invoke(entity,name,type,value);}catch(Exception ignored){}
    }
}
