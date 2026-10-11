package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;
import java.util.UUID;

/**
 * Optional visual-only installable chest. The display is not a passenger
 * or part of the physical collision body. Its Interaction is separately
 * indexed so chest clicks can open the vehicle inventory.
 */
final class VehicleChestRenderer {
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
            v.chestDisplay=v.location.getWorld().spawnEntity(position(v,d),
                    EntityType.valueOf("ITEM_DISPLAY"));
            tag(v.chestDisplay,v.id);
            fixed(v.chestDisplay);
        }
        ItemStack item=c.visualItem==null?new ItemStack(c.item):
                plugin.core().getItemManager().createRenderItem(c.visualItem);
        if(v.dyeColor!=null&&plugin.core().getItemManager().isDyeable(item))
            item=plugin.core().getItemManager().setDyeColor(item,v.dyeColor.intValue());
        invoke(v.chestDisplay,"setItemStack",ItemStack.class,item);
        invokeOptional(v.chestDisplay,"setViewRange",float.class,d.viewDistance/64.0f);
        invokeOptional(v.chestDisplay,"setTeleportDuration",int.class,1);
        matrix(v,d);
        if(c.interaction){
            if(v.chestHitbox==null||!v.chestHitbox.isValid()){
                if(v.chestHitbox!=null)v.chestHitbox.remove();
                Location location=position(v,d).add(0,c.clickHeight/2,0);
                v.chestHitbox=v.location.getWorld().spawnEntity(location,
                        EntityType.valueOf("INTERACTION"));
                tag(v.chestHitbox,v.id);
            }
            invoke(v.chestHitbox,"setInteractionWidth",float.class,(float)c.clickWidth);
            invoke(v.chestHitbox,"setInteractionHeight",float.class,(float)c.clickHeight);
            invokeOptional(v.chestHitbox,"setResponsive",boolean.class,true);
        }else if(v.chestHitbox!=null){v.chestHitbox.remove();v.chestHitbox=null;}
        move(v,d);
    }

    void move(VehicleInstance v,VehicleDefinition d){
        if(v.chestDisplay==null||!v.chestDisplay.isValid())return;
        VehicleChestDefinition c=d.inventory.chest;
        Location at=position(v,d);
        v.chestDisplay.teleport(at);
        if(v.chestHitbox!=null&&v.chestHitbox.isValid())
            v.chestHitbox.teleport(at.clone().add(0,c.clickHeight/2,0));
    }

    void tilt(VehicleInstance v,VehicleDefinition d){
        if(v.chestDisplay==null||!v.chestDisplay.isValid())return;
        try{
            invokeOptional(v.chestDisplay,"setInterpolationDuration",int.class,
                    Math.max(0,Math.min(5,plugin.getConfig().getInt("render.tilt-interpolation-ticks",2))));
            invokeOptional(v.chestDisplay,"setInterpolationDelay",int.class,-1);
            matrix(v,d);
            move(v,d);
        }catch(Exception e){
            plugin.getLogger().warning("Cannot update chest accessory tilt "+v.id+": "+e.getMessage());
        }
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

    private void matrix(VehicleInstance v,VehicleDefinition d)throws Exception{
        VehicleChestDefinition c=d.inventory.chest;
        Class<?> matrix=Class.forName("org.joml.Matrix4f");
        Object value=matrix.getConstructor().newInstance();
        if(c.followTilt){
            if(Math.abs(v.visualPitch)>0.001)
                matrix.getMethod("rotateX",float.class).invoke(value,(float)Math.toRadians(-v.visualPitch));
            if(Math.abs(v.visualRoll)>0.001)
                matrix.getMethod("rotateZ",float.class).invoke(value,(float)Math.toRadians(v.visualRoll));
        }
        matrix.getMethod("scale",float.class,float.class,float.class).invoke(
                value,(float)c.scale,(float)c.scale,(float)c.scale);
        v.chestDisplay.getClass().getMethod("setTransformationMatrix",matrix).invoke(v.chestDisplay,value);
    }
    void remove(VehicleInstance v){
        if(v.chestHitbox!=null){v.chestHitbox.remove();v.chestHitbox=null;}
        if(v.chestDisplay!=null){v.chestDisplay.remove();v.chestDisplay=null;}
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
