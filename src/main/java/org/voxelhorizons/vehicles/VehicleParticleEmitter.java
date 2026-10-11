package org.voxelhorizons.vehicles;

import org.bukkit.Particle;
import org.voxelhorizons.content.ContentID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Validated, immutable exhaust emitter definition from properties.vehicle.particles. */
final class VehicleParticleEmitter {
    enum When { ALWAYS, OCCUPIED, MOVING, IDLE }

    final Particle particle;
    final String name;
    final double x,y,z;
    final double spreadX,spreadY,spreadZ,speed,minSpeed;
    final int count,intervalTicks;
    final boolean followTilt;
    final When when;

    private VehicleParticleEmitter(Particle particle,String name,double x,double y,double z,
            double spreadX,double spreadY,double spreadZ,double speed,double minSpeed,
            int count,int intervalTicks,boolean followTilt,When when){
        this.particle=particle;this.name=name;
        this.x=x;this.y=y;this.z=z;
        this.spreadX=spreadX;this.spreadY=spreadY;this.spreadZ=spreadZ;
        this.speed=speed;this.minSpeed=minSpeed;
        this.count=count;this.intervalTicks=intervalTicks;
        this.followTilt=followTilt;this.when=when;
    }

    static List<VehicleParticleEmitter> parse(ContentID vehicle,Object value){
        if(value==null)return Collections.emptyList();
        if(!(value instanceof List))throw bad(vehicle,"particles must be a list");
        List<?> input=(List<?>)value;
        if(input.size()>8)throw bad(vehicle,"particles supports at most 8 emitters");
        List<VehicleParticleEmitter> emitters=new ArrayList<VehicleParticleEmitter>(input.size());
        for(int i=0;i<input.size();i++){
            String path="particles["+i+"]";
            Map<?,?> m=map(vehicle,input.get(i),path);
            keys(vehicle,m,path,"particle","offset","count","interval_ticks","spread",
                    "speed","when","min_speed","follow_tilt");
            Object rawName=m.get("particle");
            if(!(rawName instanceof String)||((String)rawName).trim().isEmpty())
                throw bad(vehicle,path+".particle must be a particle name");
            String name=((String)rawName).trim().toUpperCase(Locale.ROOT);
            Particle particle=resolve(vehicle,path,name);
            Map<?,?> offset=map(vehicle,m.get("offset"),path+".offset");
            keys(vehicle,offset,path+".offset","x","y","z");
            Map<?,?> spread=map(vehicle,m.get("spread"),path+".spread");
            keys(vehicle,spread,path+".spread","x","y","z");
            String condition=String.valueOf(m.containsKey("when")?m.get("when"):"OCCUPIED");
            When when;
            try{when=When.valueOf(condition.toUpperCase(Locale.ROOT));}
            catch(IllegalArgumentException e){
                throw bad(vehicle,path+".when must be ALWAYS, OCCUPIED, MOVING or IDLE");
            }
            Object tilt=m.get("follow_tilt");
            if(tilt!=null&&!(tilt instanceof Boolean))
                throw bad(vehicle,path+".follow_tilt must be boolean");
            emitters.add(new VehicleParticleEmitter(
                    particle,name,
                    num(vehicle,offset,path+".offset","x",0,-16,16),
                    num(vehicle,offset,path+".offset","y",0,-16,16),
                    num(vehicle,offset,path+".offset","z",0,-16,16),
                    num(vehicle,spread,path+".spread","x",0.03,0,2),
                    num(vehicle,spread,path+".spread","y",0.04,0,2),
                    num(vehicle,spread,path+".spread","z",0.03,0,2),
                    num(vehicle,m,path,"speed",0.015,0,2),
                    num(vehicle,m,path,"min_speed",0.025,0,2),
                    whole(vehicle,m,path,"count",1,1,16),
                    whole(vehicle,m,path,"interval_ticks",4,2,200),
                    tilt==null||Boolean.TRUE.equals(tilt),when));
        }
        return Collections.unmodifiableList(emitters);
    }

    boolean shouldEmit(boolean occupied,double horizontalSpeed){
        switch(when){
            case ALWAYS: return true;
            case OCCUPIED: return occupied;
            case MOVING: return occupied&&Math.abs(horizontalSpeed)>minSpeed;
            case IDLE: return occupied&&Math.abs(horizontalSpeed)<=minSpeed;
            default: return false;
        }
    }

    private static Particle resolve(ContentID id,String key,String name){
        String[] options="SMOKE".equals(name)?new String[]{"SMOKE","SMOKE_NORMAL"}:
                "SMOKE_NORMAL".equals(name)?new String[]{"SMOKE_NORMAL","SMOKE"}:
                new String[]{name};
        for(String option:options){
            try{
                Particle particle=Particle.valueOf(option);
                // Block/item/dust particles require payloads; don't allow a
                // configuration that would throw every tick during rendering.
                if(particle.getDataType()!=Void.class)
                    throw bad(id,key+".particle "+name+" requires particle data; "
                            +"use a data-free particle such as SMOKE, FLAME or CLOUD");
                return particle;
            }catch(IllegalArgumentException e){
                if(e.getMessage()!=null&&e.getMessage().contains("requires particle data"))throw e;
            }
        }
        throw bad(id,key+".particle '"+name+"' is unavailable on this server version");
    }

    private static Map<?,?> map(ContentID id,Object value,String path){
        if(value==null)return Collections.emptyMap();
        if(!(value instanceof Map))throw bad(id,path+" must be a mapping");
        return (Map<?,?>)value;
    }
    private static void keys(ContentID id,Map<?,?> map,String path,String... accepted){
        Set<String> allowed=new HashSet<String>(Arrays.asList(accepted));
        for(Object k:map.keySet())if(!(k instanceof String)||!allowed.contains(k))
            throw bad(id,"unsupported key "+path+"."+k);
    }
    private static double num(ContentID id,Map<?,?> map,String path,String field,
            double defaultValue,double min,double max){
        Object raw=map.get(field);
        if(raw==null)return defaultValue;
        if(!(raw instanceof Number))throw bad(id,path+"."+field+" must be numeric");
        double number=((Number)raw).doubleValue();
        if(!Double.isFinite(number)||number<min||number>max)
            throw bad(id,path+"."+field+" must be within "+min+".."+max);
        return number;
    }
    private static int whole(ContentID id,Map<?,?> map,String path,String field,
            int defaultValue,int min,int max){
        double number=num(id,map,path,field,defaultValue,min,max);
        if(number!=Math.rint(number))throw bad(id,path+"."+field+" must be an integer");
        return (int)number;
    }
    private static IllegalArgumentException bad(ContentID id,String msg){
        return new IllegalArgumentException(id+": vehicle."+msg);
    }
}
