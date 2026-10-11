package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.World;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Shape-accurate, loaded-chunks-only vehicle collision.
 * Resolves slab/stair surfaces and climbs up to ONE block, never two.
 */
final class VehicleCollision {
    private VehicleCollision(){}
    private static final java.util.Map<World,Integer> minHeights=new java.util.WeakHashMap<World,Integer>();

    private static int minHeight(World world){
        Integer cached=minHeights.get(world);
        if(cached!=null)return cached.intValue();
        int value=0;
        try{value=((Number)World.class.getMethod("getMinHeight").invoke(world)).intValue();}
        catch(ReflectiveOperationException ignored){/* older worlds start at y=0 */}
        minHeights.put(world,value);
        return value;
    }

    static final class Body {
        final double x0,y0,z0,x1,y1,z1;
        Body(Location at,VehicleDefinition d){
            Location base=VehicleTransform.collisionBase(at,d);
            // Interaction and model dimensions must NEVER influence terrain physics.
            double half=d.collisionWidth*0.5;
            x0=base.getX()-half;z0=base.getZ()-half;
            x1=base.getX()+half;z1=base.getZ()+half;
            y0=base.getY();y1=base.getY()+d.collisionHeight;
        }
    }

    private static boolean boundsValid(World world,Body body){
        return body.y0>=minHeight(world) && body.y1<=world.getMaxHeight();
    }

    static boolean clear(Location where,VehicleDefinition d){
        if(where==null||where.getWorld()==null)return false;
        return clear(where,d,new VehicleTerrain.Space(where.getWorld()));
    }

    static boolean clear(Location where,VehicleDefinition d,VehicleTerrain.Space space){
        Body body=new Body(where,d);
        if(!boundsValid(where.getWorld(),body))return false;
        List<VehicleTerrain.Box> overlaps=new ArrayList<VehicleTerrain.Box>();
        return space.collisions(body.x0,body.y0,body.z0,body.x1,body.y1,body.z1,overlaps)
                &&overlaps.isEmpty();
    }

    static String blockedReason(Location where,VehicleDefinition d){
        if(where==null||where.getWorld()==null)return "There is no valid world at this location.";
        World world=where.getWorld();
        Body body=new Body(where,d);
        if(!boundsValid(world,body))
            return "Hitbox outside world build height (bottom="+body.y0+", top="+body.y1+").";
        List<VehicleTerrain.Box> overlaps=new ArrayList<VehicleTerrain.Box>();
        if(!new VehicleTerrain.Space(world).collisions(
                body.x0,body.y0,body.z0,body.x1,body.y1,body.z1,overlaps))
            return "Vehicle would overlap an unloaded chunk; no chunks were loaded.";
        if(!overlaps.isEmpty()){
            VehicleTerrain.Box b=overlaps.get(0);
            return "Vehicle hitbox intersects "+world.getBlockAt(b.blockX,b.blockY,b.blockZ).getType()
                    +" at "+b.blockX+","+b.blockY+","+b.blockZ+".";
        }
        return "Vehicle placement was rejected; try clear, loaded terrain.";
    }

    static Location step(VehicleInstance v,VehicleDefinition d,double dx,double dy,double dz,
            double maxSubstep){
        return step(v,d,dx,dy,dz,maxSubstep,
                new VehicleTerrain.Space(v.location.getWorld()));
    }

    static Location step(VehicleInstance v,VehicleDefinition d,double dx,double dy,double dz,
            double maxSubstep,VehicleTerrain.Space space){
        Location pos=v.location.clone();
        pos.setYaw(v.motion.yaw);
        int n=(int)Math.max(1,Math.ceil(Math.max(Math.abs(dx),
                Math.max(Math.abs(dy),Math.abs(dz)))/maxSubstep));
        double sx=dx/n,sy=dy/n,sz=dz/n;
        for(int i=0;i<n;i++){
            Location next=pos.clone().add(sx,sy,sz);
            if(clear(next,d,space)){pos=next;continue;}
            if(d.type==VehicleDefinition.Type.LAND&&d.stepHeight>0) {
                Location raised=stepUp(pos,next,d,space);
                if(raised!=null){pos=raised;continue;}
            }
            v.motion.speed=0;
            break;
        }
        return pos;
    }

    /**
     * Find the lowest collision-free foothold, not a fixed jump by stepHeight.
     * A 0.5-block slab raises the tractor by 0.5; a full block by 1.0.
     * No iteration may cross the original position + allowed step height.
     */
    private static Location stepUp(Location previous,Location target,VehicleDefinition d,
            VehicleTerrain.Space space){
        final double limit=Math.min(1.0,d.stepHeight);
        double originalBottom=new Body(previous,d).y0;
        Location candidate=target.clone();
        for(int iteration=0;iteration<6;iteration++){
            Body box=new Body(candidate,d);
            if(!boundsValid(candidate.getWorld(),box))return null;
            List<VehicleTerrain.Box> overlaps=new ArrayList<VehicleTerrain.Box>();
            if(!space.collisions(box.x0,box.y0,box.z0,box.x1,box.y1,box.z1,overlaps))
                return null; // Never load another chunk while probing the ledge.
            if(overlaps.isEmpty())return candidate;
            double rise=VehicleTerrain.riseForObstructions(
                    originalBottom,box.y0,limit,overlaps);
            if(!Double.isFinite(rise)||rise<=0)return null;
            candidate.add(0,rise,0);
        }
        return null;
    }
}
