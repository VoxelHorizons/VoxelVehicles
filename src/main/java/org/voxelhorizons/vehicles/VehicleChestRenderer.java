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
    /**
     * Resolve JOML reflection only once instead of rediscovering Matrix4f
     * methods for every turn of every installed chest.
     */
    private static final class MatrixOps {
        static final MatrixOps INSTANCE=new MatrixOps();
        final Class<?> type;
        final java.lang.reflect.Constructor<?> constructor;
        final java.lang.reflect.Method translate,rotateX,rotateY,rotateZ,scale;
        MatrixOps(){
            try{
                type=Class.forName("org.joml.Matrix4f");
                constructor=type.getConstructor();
                translate=type.getMethod("translate",float.class,float.class,float.class);
                rotateX=type.getMethod("rotateX",float.class);
                rotateY=type.getMethod("rotateY",float.class);
                rotateZ=type.getMethod("rotateZ",float.class);
                scale=type.getMethod("scale",float.class,float.class,float.class);
            }catch(ReflectiveOperationException error){
                throw new IllegalStateException("Modern Paper/JOML matrices unavailable",error);
            }
        }
    }

    private final VoxelVehicles plugin;
    private Class<?> setterOwner;
    private java.lang.reflect.Method matrixSetter;
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
        // A mounted ItemDisplay inherits the tractor's movement/orientation.
        // Never apply a second yaw correction around the seat pivot.
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
        boolean wasAttached=VehicleChestMount.attached(v);
        boolean linked=VehicleChestMount.ensure(v,d.inventory.chest.attachToVehicle);
        if(linked!=wasAttached)syncInterpolation(v,teleportDuration(v));
        if(!linked){
            // Fallback only: independently move accessory if the platform
            // cannot attach an ItemDisplay passenger.
            v.teleports.ensure(v.chestDisplay,renderRoot(v,d),true);
        }
        // The chest is mounted at the parent's seat root with the SAME
        // entity yaw and static local offset as the tractor. Sending a
        // separate entity rotation or rotating its offset around the seat
        // causes double-rotation/orbiting during left/right steering.
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
            // Match the tractor's cosmetic tilt interpolation exactly.
            // Its parent handles yaw/position; only the local tilt changes.
            invokeOptional(v.chestDisplay,"setInterpolationDuration",int.class,
                    Math.max(0,Math.min(5,plugin.getConfig().getInt(
                            "render.tilt-interpolation-ticks",2))));
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
        MatrixOps ops=MatrixOps.INSTANCE;
        Object value=ops.constructor.newInstance();
        // Translate first, matching VehicleRenderer.applyMatrix: all local
        // model offsets remain fixed relative to the tractor body. The
        // parent entity is the single owner of world yaw on steering.
        ops.translate.invoke(value,(float)-d.seatX,(float)-d.seatY,(float)-d.seatZ);
        if(c.followTilt){
            if(Math.abs(v.visualPitch)>0.001)
                ops.rotateX.invoke(value,(float)Math.toRadians(-v.visualPitch));
            if(Math.abs(v.visualRoll)>0.001)
                ops.rotateZ.invoke(value,(float)Math.toRadians(v.visualRoll));
        }
        ops.translate.invoke(value,(float)c.x,(float)c.y,(float)c.z);
        // Bukkit positive yaw and JOML positive Y-rotation are opposite.
        if(Math.abs(c.yaw)>0.001)
            ops.rotateY.invoke(value,(float)Math.toRadians(-c.yaw));
        ops.scale.invoke(value,(float)c.scale,(float)c.scale,(float)c.scale);
        Class<?> entityClass=v.chestDisplay.getClass();
        if(matrixSetter==null||setterOwner!=entityClass){
            matrixSetter=entityClass.getMethod("setTransformationMatrix",ops.type);
            setterOwner=entityClass;
        }
        matrixSetter.invoke(v.chestDisplay,value);
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
