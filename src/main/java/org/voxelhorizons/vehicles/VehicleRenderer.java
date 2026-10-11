package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * One visual ItemDisplay plus one Interaction entity, regardless of vehicle size.
 * A temporary invisible armor-stand seat is created only while driven.
 * No NMS and no ProtocolLib.
 */
final class VehicleRenderer {
    private final VoxelVehicles plugin;
    VehicleRenderer(VoxelVehicles plugin){this.plugin=plugin;}

    void activate(VehicleInstance vehicle, VehicleDefinition definition) {
        Location at=vehicle.location;
        ItemStack item=plugin.core().getItemManager().createRenderItem(definition.model);
        if (vehicle.dyeColor != null) {
            if (!plugin.core().getItemManager().isDyeable(item)) {
                throw new IllegalArgumentException("Model item " + definition.model
                        + " is not dyeable, but vehicle " + vehicle.type + " has a saved color");
            }
            item=plugin.core().getItemManager().setDyeColor(item,vehicle.dyeColor.intValue());
        }
        Entity display=null,hitbox=null;
        try {
            display=at.getWorld().spawnEntity(at,EntityType.valueOf("ITEM_DISPLAY"));
            invoke(display,"setItemStack",ItemStack.class,item);
            applyFixedTransform(display);
            invokeOptional(display,"setTeleportDuration",int.class,plugin.getConfig().getInt("render.interpolation-ticks",1));
            invokeOptional(display,"setViewRange",float.class,definition.viewDistance/64.0f);
            applyScale(display,definition.scale);
            tag(display,vehicle.id);
            hitbox=at.getWorld().spawnEntity(hitLocation(at,definition),EntityType.valueOf("INTERACTION"));
            invoke(hitbox,"setInteractionWidth",float.class,(float)definition.width);
            invoke(hitbox,"setInteractionHeight",float.class,(float)definition.height);
            invokeOptional(hitbox,"setResponsive",boolean.class,true);
            tag(hitbox,vehicle.id);
            vehicle.display=display;vehicle.hitbox=hitbox;
        }catch(Exception error){
            if(display!=null)display.remove();
            if(hitbox!=null)hitbox.remove();
            throw new IllegalStateException("Could not spawn modern vehicle entities",error);
        }
    }

    void move(VehicleInstance v,VehicleDefinition d) {
        if(v.display!=null&&v.display.isValid())v.display.teleport(v.location);
        if(v.hitbox!=null&&v.hitbox.isValid())v.hitbox.teleport(hitLocation(v.location,d));
        if(v.seat!=null&&v.seat.isValid()) {
            Location at=seatLocation(v.location,d);
            if(!v.seat.teleport(at)) {
                // On older Paper builds teleport() can refuse passenger vehicles.
                plugin.getLogger().warning("Vehicle seat teleport rejected; stopping "+v.id);
                v.seat.eject();v.stop();
            }
        }
    }

    boolean seat(VehicleInstance v,VehicleDefinition d,org.bukkit.entity.Player player) {
        if(v.seat!=null&&v.seat.isValid())return false;
        ArmorStand seat=(ArmorStand)v.location.getWorld().spawnEntity(seatLocation(v.location,d),EntityType.ARMOR_STAND);
        seat.setVisible(false);seat.setGravity(false);seat.setSmall(true);
        seat.setInvulnerable(true);seat.setMarker(true);
        tag(seat,v.id);
        if(!seat.addPassenger(player)){seat.remove();return false;}
        v.seat=seat;v.driver=player.getUniqueId();
        return true;
    }

    void unseat(VehicleInstance v){
        if(v.seat!=null){v.seat.eject();v.seat.remove();v.seat=null;}
        v.stop();
    }
    void deactivate(VehicleInstance v){
        unseat(v);
        if(v.display!=null){v.display.remove();v.display=null;}
        if(v.hitbox!=null){v.hitbox.remove();v.hitbox=null;}
    }

    private void tag(Entity e, UUID id){
        e.addScoreboardTag("voxelvehicles");e.addScoreboardTag("vv:"+id);
        e.setInvulnerable(true);e.setGravity(false);
    }

    private Location hitLocation(Location at,VehicleDefinition d){
        double sin=Math.sin(Math.toRadians(at.getYaw())),cos=Math.cos(Math.toRadians(at.getYaw()));
        double x=d.hitboxX*cos-d.hitboxZ*sin;
        double z=d.hitboxX*sin+d.hitboxZ*cos;
        return at.clone().add(x,d.hitboxY+d.height*0.5,z);
    }
    private Location seatLocation(Location at,VehicleDefinition d){
        double sin=Math.sin(Math.toRadians(at.getYaw())),cos=Math.cos(Math.toRadians(at.getYaw()));
        return at.clone().add(d.seatX*cos-d.seatZ*sin,d.seatY,
                d.seatX*sin+d.seatZ*cos);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void applyFixedTransform(Entity display) throws Exception {
        Class<?> kind=Class.forName("org.bukkit.entity.ItemDisplay$ItemDisplayTransform");
        Object fixed=Enum.valueOf((Class)kind.asSubclass(Enum.class),"FIXED");
        display.getClass().getMethod("setItemDisplayTransform",kind).invoke(display,fixed);
    }

    private static void applyScale(Entity display,double size) throws Exception{
        if(size==1)return;
        Class<?> matrix=Class.forName("org.joml.Matrix4f");
        Object value=matrix.getConstructor().newInstance();
        matrix.getMethod("scale",float.class,float.class,float.class)
                .invoke(value,(float)size,(float)size,(float)size);
        display.getClass().getMethod("setTransformationMatrix",matrix).invoke(display,value);
    }
    private static void invoke(Object entity,String name,Class<?> type,Object value) throws Exception{
        entity.getClass().getMethod(name,type).invoke(entity,value);
    }
    private static void invokeOptional(Object entity,String name,Class<?> type,Object value){
        try{invoke(entity,name,type,value);}catch(Exception ignored){}
    }
}
