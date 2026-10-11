package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;

/**
 * Parked: one ItemDisplay + one Interaction.
 * Driven: ItemDisplay is the vehicle root, carrying an invisible armor-stand
 * seat (which carries the player). Only the root is teleported each tick:
 * vanilla passenger packets keep the rider and model in the same movement chain.
 */
final class VehicleRenderer {
    private final VoxelVehicles plugin;
    VehicleRenderer(VoxelVehicles plugin){this.plugin=plugin;}

    void activate(VehicleInstance vehicle, VehicleDefinition definition) {
        Location at=VehicleTransform.visual(vehicle.location,definition);
        Entity display=null,hitbox=null;
        try {
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
        }catch(Exception error) {
            if(display!=null)display.remove();
            if(hitbox!=null)hitbox.remove();
            vehicle.display=null;
            vehicle.hitbox=null;
            throw new IllegalStateException("Could not spawn modern vehicle entities",error);
        }
    }

    /** Re-apply the current VoxelCore model, hitbox and offsets after content reload. */
    void refresh(VehicleInstance vehicle,VehicleDefinition definition) throws Exception {
        if(vehicle.display==null||!vehicle.display.isValid()
                ||vehicle.hitbox==null||!vehicle.hitbox.isValid())return;
        ItemStack item=plugin.core().getItemManager().createRenderItem(definition.model);
        // Per-stack color support is also present in the dyeability PR.
        if(vehicle.dyeColor!=null&&plugin.core().getItemManager().isDyeable(item))
            item=plugin.core().getItemManager().setDyeColor(item,vehicle.dyeColor.intValue());
        invoke(vehicle.display,"setItemStack",ItemStack.class,item);
        applyFixedTransform(vehicle.display);
        invokeOptional(vehicle.display,"setViewRange",float.class,definition.viewDistance/64.0f);
        // Always restore the appropriate interpolation after a VoxelCore content reload.
        // The player and model must be interpolated on the same tick cadence.
        invokeOptional(vehicle.display,"setTeleportDuration",int.class,
                vehicle.linkedSeat?mountedInterpolation():interpolation());
        invoke(vehicle.hitbox,"setInteractionWidth",float.class,(float)definition.width);
        invoke(vehicle.hitbox,"setInteractionHeight",float.class,(float)definition.height);
        invokeOptional(vehicle.hitbox,"setResponsive",boolean.class,true);
        applyMatrix(vehicle.display,definition,vehicle.linkedSeat,vehicle.passengerOffsetY);
        move(vehicle,definition);
    }

    void move(VehicleInstance vehicle,VehicleDefinition definition){
        if(vehicle.display!=null&&vehicle.display.isValid()){
            if(vehicle.linkedSeat&&vehicle.seat!=null&&vehicle.seat.isValid()){
                // Seat is a passenger of ItemDisplay, player is a passenger of seat.
                // Correct for the seat's vanilla passenger height.
                Location root=VehicleTransform.seat(vehicle.location,definition)
                        .subtract(0,vehicle.passengerOffsetY,0);
                vehicle.display.teleport(root);
            }else{
                // If the server rejects display-entity passengers, keep functional
                // independent transport without silently throwing the rider away.
                if(vehicle.seat!=null&&vehicle.seat.isValid()&&!vehicle.seat.teleport(
                        VehicleTransform.seat(vehicle.location,definition))){
                    plugin.getLogger().warning("Vehicle seat teleport rejected: "+vehicle.id);
                    vehicle.seat.eject();
                    vehicle.stop();
                }
                vehicle.display.teleport(VehicleTransform.visual(vehicle.location,definition));
            }
        }
        if(vehicle.hitbox!=null&&vehicle.hitbox.isValid())
            vehicle.hitbox.teleport(VehicleTransform.hitbox(vehicle.location,definition));
    }

    boolean seat(VehicleInstance vehicle,VehicleDefinition definition,Player player){
        if(vehicle.seat!=null&&vehicle.seat.isValid()||vehicle.display==null||!vehicle.display.isValid())return false;
        ArmorStand stand=(ArmorStand)vehicle.location.getWorld().spawnEntity(
                VehicleTransform.seat(vehicle.location,definition),EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setMarker(true);
        tag(stand,vehicle.id);
        boolean linked=false;
        try {
            // On supported Java clients this uses ONE root movement update for the
            // visual model and the driver, eliminating independent seat chasing.
            linked=vehicle.display.addPassenger(stand);
            if(!stand.addPassenger(player)){
                if(linked)vehicle.display.removePassenger(stand);
                stand.remove();
                return false;
            }
            vehicle.linkedSeat=linked;
            vehicle.passengerOffsetY=linked
                    ?stand.getLocation().getY()-vehicle.display.getLocation().getY():0;
            vehicle.seat=stand;
            vehicle.driver=player.getUniqueId();
            // Never force 0-tick teleports for an occupied ItemDisplay.
            // The rider is smoothly interpolated by the client, and a snapping
            // model root will visibly oscillate relative to that player.
            invokeOptional(vehicle.display,"setTeleportDuration",int.class,
                    linked?mountedInterpolation():interpolation());
            applyMatrix(vehicle.display,definition,linked,vehicle.passengerOffsetY);
            move(vehicle,definition);
            if(!linked)plugin.getLogger().warning("Display passenger link not supported; vehicle "
                    +vehicle.id+" is using independent seat teleports.");
            return true;
        }catch(Exception error){
            if(linked)vehicle.display.removePassenger(stand);
            stand.eject();
            stand.remove();
            vehicle.seat=null;
            vehicle.linkedSeat=false;
            vehicle.passengerOffsetY=0;
            vehicle.stop();
            plugin.getLogger().warning("Unable to mount vehicle "+vehicle.id+": "+error.getMessage());
            return false;
        }
    }

    void unseat(VehicleInstance vehicle,VehicleDefinition definition){
        if(vehicle.seat!=null){
            if(vehicle.linkedSeat&&vehicle.display!=null&&vehicle.display.isValid())
                vehicle.display.removePassenger(vehicle.seat);
            vehicle.seat.eject();
            vehicle.seat.remove();
            vehicle.seat=null;
        }
        vehicle.linkedSeat=false;
        vehicle.passengerOffsetY=0;
        vehicle.stop();
        if(definition!=null&&vehicle.display!=null&&vehicle.display.isValid()){
            try {
                applyMatrix(vehicle.display,definition,false,0);
                invokeOptional(vehicle.display,"setTeleportDuration",int.class,interpolation());
                move(vehicle,definition);
            }catch(Exception ex){plugin.getLogger().warning("Seat reset failed: "+ex.getMessage());}
        }
    }

    void deactivate(VehicleInstance vehicle){
        unseat(vehicle,null);
        if(vehicle.display!=null){vehicle.display.remove();vehicle.display=null;}
        if(vehicle.hitbox!=null){vehicle.hitbox.remove();vehicle.hitbox=null;}
    }

    private int interpolation(){
        return Math.max(0,Math.min(59,plugin.getConfig().getInt("render.interpolation-ticks",1)));
    }

    /**
     * The mounted display is a movement root for a client-interpolated player.
     * A teleport duration of zero creates visible model snap/jitter relative
     * to the seat. Limit the setting to 1-3 ticks to avoid obvious model drag.
     */
    private int mountedInterpolation(){
        return clampMountedInterpolation(plugin.getConfig()
                .getInt("render.mounted-interpolation-ticks",1));
    }

    static int clampMountedInterpolation(int ticks){
        return Math.max(1,Math.min(3,ticks));
    }

    private void tag(Entity e,UUID id){
        e.addScoreboardTag("voxelvehicles");
        e.addScoreboardTag("vv:"+id);
        e.setInvulnerable(true);
        e.setGravity(false);
        invokeOptional(e,"setPersistent",boolean.class,false);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void applyFixedTransform(Entity display) throws Exception {
        Class<?> kind=Class.forName("org.bukkit.entity.ItemDisplay$ItemDisplayTransform");
        Object fixed=Enum.valueOf((Class)kind.asSubclass(Enum.class),"FIXED");
        display.getClass().getMethod("setItemDisplayTransform",kind).invoke(display,fixed);
    }

    /** On a mounted vehicle the model offsets back from the seat-root origin. */
    private static void applyMatrix(Entity entity,VehicleDefinition definition,boolean mounted,double passengerOffsetY)
            throws Exception {
        Class<?> matrix=Class.forName("org.joml.Matrix4f");
        Object value=matrix.getConstructor().newInstance();
        if(mounted){
            // Model at display origin, seat at display origin + seat XYZ.
            // The root follows the seat and the model is translated back.
            matrix.getMethod("translate",float.class,float.class,float.class).invoke(
                    value,(float)-definition.seatX,
                    (float)(passengerOffsetY-definition.seatY),(float)-definition.seatZ);
        }
        matrix.getMethod("scale",float.class,float.class,float.class).invoke(
                value,(float)definition.scale,(float)definition.scale,(float)definition.scale);
        entity.getClass().getMethod("setTransformationMatrix",matrix).invoke(entity,value);
    }

    private static void invoke(Object entity,String name,Class<?> type,Object value) throws Exception{
        entity.getClass().getMethod(name,type).invoke(entity,value);
    }
    private static void invokeOptional(Object entity,String name,Class<?> type,Object value){
        try{invoke(entity,name,type,value);}catch(Exception ignored){}
    }
}
