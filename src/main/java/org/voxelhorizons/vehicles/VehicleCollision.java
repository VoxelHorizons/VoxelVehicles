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
        // Hot path: no temporary List allocation or boxing of collisions.
        // Tri-state preserves unloaded-chunk rejection.
        return space.contact(body.x0,body.y0,body.z0,
                body.x1,body.y1,body.z1)==0;
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

    /** A support contact is a collision surface less than 0.06 blocks below us. */
    static boolean supported(Location where,VehicleDefinition d,VehicleTerrain.Space space){
        Location probe=where.clone().add(0,-0.06,0);
        Body body=new Body(probe,d);
        if(!boundsValid(where.getWorld(),body))return false;
        // A missing chunk is NOT a support surface; 1 means a real contact.
        return space.contact(body.x0,body.y0,body.z0,
                body.x1,body.y1,body.z1)==1;
    }

    static final class FallResult {
        final Location location;
        final boolean landed;
        FallResult(Location location,boolean landed){
            this.location=location;this.landed=landed;
        }
    }

    /**
     * Resolve a continuous fall in bounded substeps and land exactly on a
     * VoxelShape top surface. Previous fixed -0.12 movement stopped up to
     * 0.12 blocks above the ground and created hovering/hover oscillations.
     */
    static FallResult descend(Location from,VehicleDefinition d,double down,
            double maxSubstep,VehicleTerrain.Space space){
        if(down>=0)return new FallResult(from.clone(),false);
        int count=(int)Math.max(1,Math.ceil(-down/Math.max(0.05,maxSubstep)));
        double delta=down/count;
        Location current=from.clone();
        for(int i=0;i<count;i++){
            Location candidate=current.clone().add(0,delta,0);
            Body previous=new Body(current,d);
            Body next=new Body(candidate,d);
            if(!boundsValid(candidate.getWorld(),next))
                return new FallResult(current,true);
            List<VehicleTerrain.Box> collisions=new ArrayList<VehicleTerrain.Box>();
            if(!space.collisions(next.x0,next.y0,next.z0,
                    next.x1,next.y1,next.z1,collisions))
                return new FallResult(current,false); // unloaded chunk: never force-load
            if(collisions.isEmpty()){current=candidate;continue;}
            double top=Double.NEGATIVE_INFINITY;
            for(VehicleTerrain.Box box:collisions){
                // Only a surface crossed by our downward-moving bottom is a
                // landing platform; never snap up to a ceiling or two-block wall.
                if(box.maxY<=previous.y0+VehicleTerrain.EPS)
                    top=Math.max(top,box.maxY);
            }
            if(Double.isFinite(top)){
                Location contact=current.clone();
                contact.setY(top-d.collisionY);
                if(clear(contact,d,space))current=contact;
            }
            return new FallResult(current,true);
        }
        return new FallResult(current,false);
    }

    static Location step(VehicleInstance v,VehicleDefinition d,double dx,double dy,double dz,
            double maxSubstep){
        return step(v,d,dx,dy,dz,maxSubstep,
                new VehicleTerrain.Space(v.location.getWorld()),true);
    }

    static Location step(VehicleInstance v,VehicleDefinition d,double dx,double dy,double dz,
            double maxSubstep,VehicleTerrain.Space space){
        return step(v,d,dx,dy,dz,maxSubstep,space,true);
    }

    static Location step(VehicleInstance v,VehicleDefinition d,double dx,double dy,double dz,
            double maxSubstep,VehicleTerrain.Space space,boolean allowStepping){
        Location pos=v.location.clone();
        pos.setYaw(v.motion.yaw);
        int n=(int)Math.max(1,Math.ceil(Math.max(Math.abs(dx),
                Math.max(Math.abs(dy),Math.abs(dz)))/maxSubstep));
        double sx=dx/n,sy=dy/n,sz=dz/n;
        for(int i=0;i<n;i++){
            Location next=pos.clone().add(sx,sy,sz);
            if(clear(next,d,space)){pos=next;continue;}
            if(allowStepping&&d.type==VehicleDefinition.Type.LAND&&d.stepHeight>0) {
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
