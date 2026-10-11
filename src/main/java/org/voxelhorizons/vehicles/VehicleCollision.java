package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.World;

/** Bounded collision sampling with zero forced chunk loads. */
final class VehicleCollision {
    private VehicleCollision(){}
    private static final java.util.Map<World,Integer> minHeights=new java.util.WeakHashMap<World,Integer>();

    private static int minHeight(World world){
        Integer cached=minHeights.get(world);
        if(cached!=null)return cached.intValue();
        int value=0;
        try{value=((Number)World.class.getMethod("getMinHeight").invoke(world)).intValue();}
        catch(ReflectiveOperationException ignored){/* pre-1.17 worlds start at y=0 */}
        minHeights.put(world,Integer.valueOf(value));
        return value;
    }

    static boolean clear(Location where,VehicleDefinition d){
        World world=where.getWorld();
        double half=d.width*0.5;
        double angle=Math.toRadians(where.getYaw());
        double sin=Math.sin(angle),cos=Math.cos(angle);
        double x=where.getX()+d.hitboxX*cos-d.hitboxZ*sin;
        double y=where.getY()+d.hitboxY;
        double z=where.getZ()+d.hitboxX*sin+d.hitboxZ*cos;
        int minX=(int)Math.floor(x-half+0.001),maxX=(int)Math.floor(x+half-0.001);
        int minZ=(int)Math.floor(z-half+0.001),maxZ=(int)Math.floor(z+half-0.001);
        int minY=(int)Math.floor(y+0.01),maxY=(int)Math.floor(y+d.height-0.01);
        if(minY<minHeight(world)||maxY>=world.getMaxHeight())return false;
        for(int px=minX;px<=maxX;px++)for(int pz=minZ;pz<=maxZ;pz++){
            // Check before querying blocks: getBlockAt() must not force a chunk load.
            if(!world.isChunkLoaded(px>>4,pz>>4))return false;
            for(int py=minY;py<=maxY;py++){
                if(world.getBlockAt(px,py,pz).getType().isSolid())return false;
            }
        }
        return true;
    }

    static Location step(VehicleInstance v,VehicleDefinition d,double dx,double dy,double dz,
                         double maxStep){
        Location pos=v.location.clone();
        pos.setYaw(v.motion.yaw);
        int n=(int)Math.max(1,Math.ceil(Math.max(Math.abs(dx),Math.max(Math.abs(dy),Math.abs(dz)))/maxStep));
        double sx=dx/n,sy=dy/n,sz=dz/n;
        boolean stepped=false;
        for(int i=0;i<n;i++){
            Location next=pos.clone().add(sx,sy,sz);
            if(clear(next,d)){pos=next;continue;}
            if(d.type==VehicleDefinition.Type.LAND&&d.stepHeight>0&&!stepped){
                Location raised=next.clone().add(0,d.stepHeight,0);
                if(clear(raised,d)){pos=raised;stepped=true;continue;}
            }
            v.motion.speed=0;
            break;
        }
        return pos;
    }
}
