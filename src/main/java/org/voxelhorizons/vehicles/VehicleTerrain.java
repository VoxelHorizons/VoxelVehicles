package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import java.lang.reflect.Method;
import java.util.*;

/**
 * World-space collision boxes, read from modern Minecraft block VoxelShapes.
 * Never forces a chunk load. Older Bukkit APIs fall back to full solid cubes.
 * Cache is deliberately per physics calculation so block edits are seen next tick.
 */
final class VehicleTerrain {
    private VehicleTerrain(){}
    static final double EPS=0.0001;
    private static final Method GET_SHAPE,GET_BOXES,MIN_X,MIN_Y,MIN_Z,MAX_X,MAX_Y,MAX_Z;
    static {
        Method shape=null,boxes=null,x0=null,y0=null,z0=null,x1=null,y1=null,z1=null;
        try {
            Class<?> voxel=Class.forName("org.bukkit.util.VoxelShape");
            Class<?> bbox=Class.forName("org.bukkit.util.BoundingBox");
            shape=Block.class.getMethod("getCollisionShape");
            boxes=voxel.getMethod("getBoundingBoxes");
            x0=bbox.getMethod("getMinX");y0=bbox.getMethod("getMinY");z0=bbox.getMethod("getMinZ");
            x1=bbox.getMethod("getMaxX");y1=bbox.getMethod("getMaxY");z1=bbox.getMethod("getMaxZ");
        }catch(ReflectiveOperationException ignored){}
        GET_SHAPE=shape;GET_BOXES=boxes;MIN_X=x0;MIN_Y=y0;MIN_Z=z0;
        MAX_X=x1;MAX_Y=y1;MAX_Z=z1;
    }

    static final class Box {
        final double minX,minY,minZ,maxX,maxY,maxZ;
        final int blockX,blockY,blockZ;
        Box(double x0,double y0,double z0,double x1,double y1,double z1,
                int bx,int by,int bz){
            this.minX=x0;this.minY=y0;this.minZ=z0;
            this.maxX=x1;this.maxY=y1;this.maxZ=z1;
            this.blockX=bx;this.blockY=by;this.blockZ=bz;
        }
        boolean intersects(double x0,double y0,double z0,double x1,double y1,double z1){
            return x1>minX+EPS&&x0<maxX-EPS
                    &&y1>minY+EPS&&y0<maxY-EPS
                    &&z1>minZ+EPS&&z0<maxZ-EPS;
        }
        boolean over(double x,double z){
            return x>minX+EPS&&x<maxX-EPS&&z>minZ+EPS&&z<maxZ-EPS;
        }
    }

    static final class Space {
        private final World world;
        private final Map<String,List<Box>> cache=new HashMap<String,List<Box>>();
        Space(World world){this.world=world;}

        boolean loaded(int x,int z){return world.isChunkLoaded(x>>4,z>>4);}

        List<Box> block(int x,int y,int z){
            String key=x+":"+y+":"+z;
            List<Box> old=cache.get(key);
            if(old!=null)return old;
            if(!loaded(x,z))return Collections.emptyList();
            Block b=world.getBlockAt(x,y,z);
            List<Box> result=new ArrayList<Box>();
            String material=b.getType().name();
            if(!material.equals("AIR")&&!material.endsWith("_AIR")
                    &&!material.equals("WATER")&&!material.equals("LAVA")) {
                if(GET_SHAPE!=null&&GET_BOXES!=null){
                    try{
                        Object shape=GET_SHAPE.invoke(b);
                        @SuppressWarnings("unchecked")
                        Collection<Object> boxes=(Collection<Object>)GET_BOXES.invoke(shape);
                        for(Object raw:boxes){
                            // Bukkit VoxelShape boxes are already in block-local coordinates.
                            result.add(new Box(x+n(MIN_X,raw),y+n(MIN_Y,raw),z+n(MIN_Z,raw),
                                    x+n(MAX_X,raw),y+n(MAX_Y,raw),z+n(MAX_Z,raw),x,y,z));
                        }
                    }catch(ReflectiveOperationException|RuntimeException failure){
                        // Never silently let a solid block become intangible.
                        if(b.getType().isSolid())result.add(new Box(x,y,z,x+1,y+1,z+1,x,y,z));
                    }
                }else if(b.getType().isSolid()){
                    result.add(new Box(x,y,z,x+1,y+1,z+1,x,y,z));
                }
            }
            cache.put(key,result);
            return result;
        }
        private static double n(Method m,Object b)throws ReflectiveOperationException{
            return ((Number)m.invoke(b)).doubleValue();
        }

        /** Finds colliding shapes; returns false if ANY required chunk is unloaded. */
        boolean collisions(double x0,double y0,double z0,double x1,double y1,double z1,List<Box> into){
            int minX=(int)Math.floor(x0+EPS),maxX=(int)Math.floor(x1-EPS);
            int minY=(int)Math.floor(y0+EPS),maxY=(int)Math.floor(y1-EPS);
            int minZ=(int)Math.floor(z0+EPS),maxZ=(int)Math.floor(z1-EPS);
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
                if(!loaded(x,z))return false;
                for(int y=minY;y<=maxY;y++)
                    for(Box box:block(x,y,z))
                        if(box.intersects(x0,y0,z0,x1,y1,z1))into.add(box);
            }
            return true;
        }

        /**
         * Highest actual collision surface at a point near the vehicle's wheels.
         * Bounds suppress an overhead ceiling or two-block wall masquerading as ground.
         */
        double surface(double x,double z,double referenceY,double minY,double maxY){
            int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
            if(!loaded(bx,bz))return Double.NaN;
            double height=Double.NaN;
            for(int y=(int)Math.floor(referenceY+minY)-1;
                    y<=(int)Math.floor(referenceY+maxY);y++){
                for(Box box:block(bx,y,bz)){
                    if(box.over(x,z)&&box.maxY<=referenceY+maxY+EPS
                            &&box.maxY>=referenceY+minY-EPS
                            &&(Double.isNaN(height)||box.maxY>height))
                        height=box.maxY;
                }
            }
            return height;
        }
    }

    static double riseForObstructions(double originalBottom,double candidateBottom,
            double maximumStep,List<Box> collisions){
        double rise=0;
        for(Box box:collisions){
            double needed=box.maxY-candidateBottom;
            if(needed>rise)rise=needed;
        }
        return candidateBottom+rise<=originalBottom+Math.min(1.0,maximumStep)+EPS
                ?Math.max(0,rise):Double.NaN;
    }

    /** Degrees for the model only. Entity hitbox and passenger yaw stay upright. */
    static double inclination(double front,double rear,double distance,double limit){
        if(!Double.isFinite(front)||!Double.isFinite(rear))return 0;
        return clamp(Math.toDegrees(Math.atan2(front-rear,distance)),-limit,limit);
    }
    static double smooth(double current,double target,double factor){
        return current+(target-current)*factor;
    }
    static double clamp(double value,double min,double max){
        return Math.min(max,Math.max(min,value));
    }
}
