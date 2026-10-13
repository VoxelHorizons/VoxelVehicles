package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;
import java.util.UUID;

/**
 * Optional visual-only installable chest. The display shares the exact
 * seat-anchored render ROOT with the tractor's ItemDisplay.
 * It now rides that display as a passenger: only the tractor receives
 * world-position teleports. This avoids independent client interpolation
 * that could make the chest lag, wobble or reverse its apparent movement.
 * Its matrix offsets the actual chest in vehicle-local space.
 * A separate Interaction remains at the chest's world-space position so
 * the accessory can still be clicked without changing the driver mount.
 */
final class VehicleChestRenderer {
    // The Java 8 / Bukkit 1.12.2 compile target does not expose this method,
    // but modern Paper ItemDisplay entities do. Resolve once, not per tick.
    private static final java.lang.reflect.Method SET_ROTATION=findRotationMethod();
    private static java.lang.reflect.Method findRotationMethod(){
        try{return Entity.class.getMethod("setRotation",float.class,float.class);}
        catch(ReflectiveOperationException ignored){return null;}
    }

    private final VoxelVehicles plugin;
    VehicleChestRenderer(VoxelVehicles plugin){this.plugin=plugin;}

    void refresh(VehicleInstance v,VehicleDefinition d)throws Exception{
        VehicleChestDefinition c=d.inventory.chest;
        if(!c.enabled||!v.chestInstalled()||c.mode!=VehicleChestDefinition.VisualMode.ITEM_DISPLAY
                ||v.display==null||!v.display.isValid()){
            remove(v);return;
        }
        if(v.chestDisplay==null||!v.chestDisplay.isValid()){
            remove(v);
            Location root=renderRoot(v,d);
            v.chestDisplay=v.location.getWorld().spawnEntity(root,
                    EntityType.valueOf("ITEM_DISPLAY"));
            tag(v.chestDisplay,v.id);
            v.teleports.spawned(v.chestDisplay,root);
            fixed(v.chestDisplay);
        }
        ItemStack item=c.visualItem==null?new ItemStack(c.item):
                plugin.core().getItemManager().createRenderItem(c.visualItem);
        if(v.dyeColor!=null&&plugin.core().getItemManager().isDyeable(item))
            item=plugin.core().getItemManager().setDyeColor(item,v.dyeColor.intValue());
        invoke(v.chestDisplay,"setItemStack",ItemStack.class,item);
        invokeOptional(v.chestDisplay,"setViewRange",float.class,d.viewDistance/64.0f);
        syncInterpolation(v,teleportDuration(v));
        matrix(v,d);
        if(c.interaction){
            if(v.chestHitbox==null||!v.chestHitbox.isValid()){
                if(v.chestHitbox!=null){
                    v.teleports.forget(v.chestHitbox);
                    v.chestHitbox.remove();
                }
                Location location=position(v,d).add(0,c.clickHeight/2,0);
                v.chestHitbox=v.location.getWorld().spawnEntity(location,
                        EntityType.valueOf("INTERACTION"));
                tag(v.chestHitbox,v.id);
                v.teleports.spawned(v.chestHitbox,location);
            }
            invoke(v.chestHitbox,"setInteractionWidth",float.class,(float)c.clickWidth);
            invoke(v.chestHitbox,"setInteractionHeight",float.class,(float)c.clickHeight);
            invokeOptional(v.chestHitbox,"setResponsive",boolean.class,true);
        }else if(v.chestHitbox!=null){
            v.teleports.forget(v.chestHitbox);
            v.chestHitbox.remove();v.chestHitbox=null;
        }
        VehicleChestMount.allowRetry(v);
        move(v,d);
    }

    void move(VehicleInstance v,VehicleDefinition d){
        if(v.chestDisplay==null||!v.chestDisplay.isValid())return;
        boolean previouslyLinked=v.chestPassengerLinked;
        boolean linked=VehicleChestMount.ensure(v,d.inventory.chest.attachToVehicle);
        if(linked!=previouslyLinked)syncInterpolation(v,teleportDuration(v));
        if(linked){
            // The chest follows the mounted tractor's movement client-side,
            // including interpolation. Only align its yaw; no chest teleport.
            // It still needs its own heading for the local model matrix.
            Location root=renderRoot(v,d);
            try{
                if(SET_ROTATION!=null
                        &&Float.compare(v.chestDisplay.getLocation().getYaw(),root.getYaw())!=0)
                    SET_ROTATION.invoke(v.chestDisplay,root.getYaw(),root.getPitch());
            }catch(Exception error){
                plugin.getLogger().fine("Cannot rotate vehicle chest passenger "+v.id
                        +": "+error.getMessage());
            }
        }else{
            // Compatibility path for servers rejecting another passenger.
            // The original independent renderer remains fully supported.
            v.teleports.ensure(v.chestDisplay,renderRoot(v,d),true);
        }
        moveHitbox(v,d);
    }

    private void moveHitbox(VehicleInstance v,VehicleDefinition d){
        if(v.chestHitbox!=null&&v.chestHitbox.isValid()){
            VehicleChestDefinition c=d.inventory.chest;
            // A pure tilt rotates the accessory's local matrix. The click
            // target may move, but the display's root must NOT teleport again.
            v.teleports.ensure(v.chestHitbox,
                    position(v,d).add(0,c.clickHeight/2,0),false);
        }
    }

    void syncInterpolation(VehicleInstance v,int ticks){
        if(v.chestDisplay!=null&&v.chestDisplay.isValid())
            invokeOptional(v.chestDisplay,"setTeleportDuration",int.class,
                    VehicleChestMount.attached(v)?0:ticks);
    }

    private int teleportDuration(VehicleInstance v){
        return v.seat!=null
                ?VehicleRenderer.clampMountedInterpolation(plugin.getConfig().getInt(
                        "render.mounted-interpolation-ticks",1))
                :Math.max(0,Math.min(59,plugin.getConfig().getInt(
                        "render.interpolation-ticks",1)));
    }

    void tilt(VehicleInstance v,VehicleDefinition d){
        if(v.chestDisplay==null||!v.chestDisplay.isValid())return;
        try{
            invokeOptional(v.chestDisplay,"setInterpolationDuration",int.class,
                    Math.max(0,Math.min(5,plugin.getConfig().getInt("render.tilt-interpolation-ticks",2))));
            invokeOptional(v.chestDisplay,"setInterpolationDelay",int.class,-1);
            matrix(v,d);
            // Only the clickable attachment centre may change on tilt.
            // The visual ItemDisplay stays at the permanent vehicle root.
            if(d.inventory.chest.followTilt)moveHitbox(v,d);
        }catch(Exception e){
            plugin.getLogger().warning("Cannot update chest accessory tilt "+v.id+": "+e.getMessage());
        }
    }

    /** Same world anchor, yaw and update tick as the mounted tractor display. */
    static Location renderRoot(VehicleInstance v,VehicleDefinition d){
        return VehicleTransform.renderRoot(v.location,d);
    }

    static Location position(VehicleInstance v,VehicleDefinition d){
        VehicleChestDefinition c=d.inventory.chest;
        Location model=VehicleTransform.visual(v.location,d);
        double x=c.x,y=c.y,z=c.z;
        if(c.followTilt){
            double roll=Math.toRadians(v.visualRoll),cr=Math.cos(roll),sr=Math.sin(roll);
            double rotatedX=x*cr-y*sr,rotatedY=x*sr+y*cr;
            double pitch=Math.toRadians(v.visualPitch),cp=Math.cos(pitch),sp=Math.sin(pitch);
            x=rotatedX;y=rotatedY*cp+z*sp;z=-rotatedY*sp+z*cp;
        }
        Location at=VehicleTransform.local(model,model.getYaw(),x,y,z);
        at.setYaw(model.getYaw()+(float)c.yaw);
        return at;
    }

    /**
     * Mirror VehicleRenderer.applyMatrix exactly:
     * translate from the permanent seat root to the visual model origin,
     * apply the same tilt, then translate to the chest attachment point.
     *
     * The chest offset is specified in block units, not the tractor model's
     * scaled model coordinates. It is therefore translated BEFORE its own
     * scale and deliberately does not multiply by d.scale.
     */
    private void matrix(VehicleInstance v,VehicleDefinition d)throws Exception{
        VehicleChestDefinition c=d.inventory.chest;
        Class<?> matrix=Class.forName("org.joml.Matrix4f");
        Object value=matrix.getConstructor().newInstance();
        double[] seatToModel=VehicleTransform.modelTranslation(d);
        matrix.getMethod("translate",float.class,float.class,float.class).invoke(
                value,(float)seatToModel[0],(float)seatToModel[1],(float)seatToModel[2]);
        if(c.followTilt){
            if(Math.abs(v.visualPitch)>0.001)
                matrix.getMethod("rotateX",float.class).invoke(value,(float)Math.toRadians(-v.visualPitch));
            if(Math.abs(v.visualRoll)>0.001)
                matrix.getMethod("rotateZ",float.class).invoke(value,(float)Math.toRadians(v.visualRoll));
        }
        matrix.getMethod("translate",float.class,float.class,float.class).invoke(
                value,(float)c.x,(float)c.y,(float)c.z);
        // Bukkit positive yaw and JOML positive Y-rotation are opposite.
        if(Math.abs(c.yaw)>0.001)
            matrix.getMethod("rotateY",float.class).invoke(value,(float)Math.toRadians(-c.yaw));
        matrix.getMethod("scale",float.class,float.class,float.class).invoke(
                value,(float)c.scale,(float)c.scale,(float)c.scale);
        v.chestDisplay.getClass().getMethod("setTransformationMatrix",matrix).invoke(v.chestDisplay,value);
    }

    void remove(VehicleInstance v){
        VehicleChestMount.reset(v);
        if(v.chestHitbox!=null){
            v.teleports.forget(v.chestHitbox);
            v.chestHitbox.remove();v.chestHitbox=null;
        }
        if(v.chestDisplay!=null){
            v.teleports.forget(v.chestDisplay);
            v.chestDisplay.remove();v.chestDisplay=null;
        }
    }
    private static void tag(Entity e,UUID id){
        e.addScoreboardTag("voxelvehicles");
        e.addScoreboardTag("vv:"+id);
        e.setInvulnerable(true);
        e.setGravity(false);
        invokeOptional(e,"setPersistent",boolean.class,false);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void fixed(Entity e)throws Exception{
        Class<?> klass=Class.forName("org.bukkit.entity.ItemDisplay$ItemDisplayTransform");
        Object transform=Enum.valueOf((Class)klass.asSubclass(Enum.class),"FIXED");
        e.getClass().getMethod("setItemDisplayTransform",klass).invoke(e,transform);
    }
    private static void invoke(Object e,String name,Class<?> klass,Object value)throws Exception{
        e.getClass().getMethod(name,klass).invoke(e,value);
    }
    private static void invokeOptional(Object e,String name,Class<?> klass,Object value){
        try{invoke(e,name,klass,value);}catch(Exception ignored){}
    }
}
