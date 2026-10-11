package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.World;

/** Bounded collision sampling with zero forced chunk loads. */
final class VehicleCollision {
    private VehicleCollision(){}

    static boolean clear(Location where,VehicleDefinition d){
        World world=where.getWorld();
        double half=d.width*0.5;
        double angle=Math.toRadians(where.getYaw());
        double sin=Math.sin(angle),cos=Math.cos(angle);
        double x=where.getX()+d.hitboxX*cos-d.hitboxZ*sin;
        double y=where.getY()+d.hitboxY;
        double z=where.getZ()+d.hitboxX*sin+d.hitboxZ*cos;
        double[] xs={x-half,x+half};
        double[] zs={z-half,z+half};
        double[] ys={y+0.05,y+d.height*0.5,y+d.height-0.05};
        for(double px:xs)for(double pz:zs) {
            int cx=((int)Math.floor(px))>>4,cz=((int)Math.floor(pz))>>4;
            if(!world.isChunkLoaded(cx,cz))return false;
            for(double py:ys) {
                if(py<0||py>=world.getMaxHeight())return false;
                if(world.getBlockAt((int)Math.floor(px),(int)Math.floor(py),(int)Math.floor(pz)).getType().isSolid())
                    return false;
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
        for(int i=0;i<n;i++){
            Location next=pos.clone().add(sx,sy,sz);
            if(clear(next,d)){pos=next;continue;}
            if(d.type==VehicleDefinition.Type.LAND&&d.stepHeight>0){
                Location raised=next.clone().add(0,d.stepHeight,0);
                if(clear(raised,d)){pos=raised;continue;}
            }
            v.motion.speed=0;
            break;
        }
        return pos;
    }
}
