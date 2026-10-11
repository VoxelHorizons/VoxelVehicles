package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Vanilla Interaction hitboxes are always world-axis-aligned squares. A small
 * rotated lattice of square Interaction entities approximates an oriented
 * rectangle without packet interception or permanent animation entities.
 */
final class VehicleHitboxes {
    private VehicleHitboxes(){}

    static final class Cell {
        final double x,z,size;
        Cell(double x,double z,double size){this.x=x;this.z=z;this.size=size;}
    }

    static List<Cell> layout(VehicleDefinition d){
        if(!d.rotatingHitbox)
            return Collections.singletonList(new Cell(0,0,d.width));
        int across=d.hitboxResolution;
        // Limit entity cost: at most 3 columns and 5 rows (15 Interaction
        // entities in the highest precision mode, generally 4-6 by default).
        int along=Math.max(1,Math.min(5,
                (int)Math.round(d.depth/(d.width/across))));
        double cellSize=Math.max(d.width/across,d.depth/along);
        List<Cell> cells=new ArrayList<Cell>(across*along);
        for(int ix=0;ix<across;ix++){
            double x=(ix+0.5)*d.width/across-d.width/2;
            for(int iz=0;iz<along;iz++){
                double z=(iz+0.5)*d.depth/along-d.depth/2;
                cells.add(new Cell(x,z,cellSize));
            }
        }
        return cells;
    }

    static Location location(Location vehicle,VehicleDefinition definition,Cell cell){
        Location center=VehicleTransform.hitbox(vehicle,definition);
        return VehicleTransform.local(center,center.getYaw(),cell.x,0,cell.z);
    }

    /** Precise intended rotated rectangle for manual punch targeting. */
    static boolean rayIntersects(Location eye,Location origin,VehicleDefinition d,double reach){
        if(eye==null||origin==null||!eye.getWorld().equals(origin.getWorld()))return false;
        Location center=VehicleTransform.hitbox(origin,d);
        double yaw=Math.toRadians(center.getYaw());
        double sin=Math.sin(yaw),cos=Math.cos(yaw);
        org.bukkit.util.Vector direction=eye.getDirection().normalize();
        double px=eye.getX()-center.getX(),pz=eye.getZ()-center.getZ();
        double dx=direction.getX(),dz=direction.getZ();
        double[] position={
                px*cos+pz*sin,eye.getY()-center.getY(),-px*sin+pz*cos};
        double[] ray={dx*cos+dz*sin,direction.getY(),-dx*sin+dz*cos};
        double halfDepth=(d.rotatingHitbox?d.depth:d.width)*0.5;
        double[] lo={-d.width*0.5,-d.height*0.5,-halfDepth};
        double[] hi={d.width*0.5,d.height*0.5,halfDepth};
        double near=0,far=reach;
        for(int axis=0;axis<3;axis++){
            if(Math.abs(ray[axis])<1e-8){
                if(position[axis]<lo[axis]||position[axis]>hi[axis])return false;
                continue;
            }
            double a=(lo[axis]-position[axis])/ray[axis];
            double b=(hi[axis]-position[axis])/ray[axis];
            if(a>b){double swap=a;a=b;b=swap;}
            near=Math.max(near,a);far=Math.min(far,b);
            if(near>far)return false;
        }
        return near<=reach;
    }
}
