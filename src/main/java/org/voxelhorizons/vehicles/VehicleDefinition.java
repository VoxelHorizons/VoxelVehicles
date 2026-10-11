package org.voxelhorizons.vehicles;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import java.util.Map;
import java.util.Optional;
import java.util.Arrays;
import java.util.HashSet;

/** Vehicle properties are inherited from VoxelCore items; no second item registry. */
public final class VehicleDefinition {
    public enum Type { LAND, PLANE }
    public final ContentID id, model, drop;
    public final Type type;
    public final boolean publicAccess;
    public final double width, height, hitboxX, hitboxY, hitboxZ;
    public final double scale, seatX, seatY, seatZ;
    public final double speed, reverse, acceleration, braking, drag, turnRate, stepHeight, flySpeed, climbSpeed;
    public final float viewDistance;

    private VehicleDefinition(ContentID id, ContentID model, ContentID drop, Type type, boolean publicAccess,
            double width, double height, double hitboxX, double hitboxY, double hitboxZ,
            double scale, double seatX, double seatY, double seatZ, double speed, double reverse,
            double acceleration, double braking, double drag, double turnRate, double stepHeight,
            double flySpeed, double climbSpeed, float viewDistance) {
        this.id=id; this.model=model; this.drop=drop; this.type=type; this.publicAccess=publicAccess;
        this.width=width; this.height=height; this.hitboxX=hitboxX; this.hitboxY=hitboxY; this.hitboxZ=hitboxZ;
        this.scale=scale; this.seatX=seatX; this.seatY=seatY; this.seatZ=seatZ;
        this.speed=speed; this.reverse=reverse; this.acceleration=acceleration; this.braking=braking;
        this.drag=drag; this.turnRate=turnRate; this.stepHeight=stepHeight;
        this.flySpeed=flySpeed; this.climbSpeed=climbSpeed; this.viewDistance=viewDistance;
    }
    public static Optional<VehicleDefinition> parse(ItemDefinition item) {
        Object raw = item.properties().get("vehicle");
        if (raw == null) return Optional.empty();
        Map<?,?> m = map(item,raw,"vehicle");
        keys(item,m,"vehicle","type","public","model_item","drop","hitbox","scale","seat","physics","view_distance");
        Type type;
        try { type=Type.valueOf(String.valueOf(m.containsKey("type")?m.get("type"):"LAND").toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw bad(item,"unknown vehicle type"); }
        Map<?,?> h=map(item,m.get("hitbox"),"hitbox");
        keys(item,h,"hitbox","width","height","offset");
        Map<?,?> o=map(item,h.get("offset"),"hitbox.offset");
        keys(item,o,"hitbox.offset","x","y","z");
        Map<?,?> seat=map(item,m.get("seat"),"seat");
        keys(item,seat,"seat","x","y","z");
        Map<?,?> p=map(item,m.get("physics"),"physics");
        keys(item,p,"physics","speed","reverse","acceleration","braking","drag","turn_rate","step_height","fly_speed","climb_speed");
        if (m.containsKey("public") && !(m.get("public") instanceof Boolean)) throw bad(item,"public must be boolean");
        return Optional.of(new VehicleDefinition(item.id(),ref(item,m.get("model_item")),ref(item,m.get("drop")),
                type,Boolean.TRUE.equals(m.get("public")),num(item,h,"width",1.4,0.1,16),
                num(item,h,"height",1.5,0.1,16),num(item,o,"x",0,-16,16),
                num(item,o,"y",0,-16,16),num(item,o,"z",0,-16,16),
                num(item,m,"scale",1,0.01,16),num(item,seat,"x",0,-16,16),
                num(item,seat,"y",-0.7,-16,16),num(item,seat,"z",0,-16,16),
                num(item,p,"speed",0.35,0.01,2),num(item,p,"reverse",0.15,0,2),
                num(item,p,"acceleration",0.025,0.0001,1),num(item,p,"braking",0.045,0.0001,1),
                num(item,p,"drag",0.94,0,1),num(item,p,"turn_rate",4,0,90),
                num(item,p,"step_height",0.6,0,1.5),num(item,p,"fly_speed",0.4,0.01,2),
                num(item,p,"climb_speed",0.22,0,2),(float)num(item,m,"view_distance",64,1,256)));
    }
    private static ContentID ref(ItemDefinition item,Object value) {
        if(value==null)return item.id();
        if(!(value instanceof String))throw bad(item,"model_item/drop must be a string");
        return ContentID.parse((String)value,item.id().namespace());
    }
    private static Map<?,?> map(ItemDefinition item,Object value,String key) {
        if(value==null)return java.util.Collections.emptyMap();
        if(!(value instanceof Map))throw bad(item,key+" must be a mapping");
        return (Map<?,?>)value;
    }
    private static double num(ItemDefinition item,Map<?,?> map,String key,double def,double min,double max) {
        Object value=map.get(key); if(value==null)return def;
        if(!(value instanceof Number))throw bad(item,key+" must be numeric");
        double d=((Number)value).doubleValue();
        if(!Double.isFinite(d)||d<min||d>max)throw bad(item,key+" outside "+min+".."+max);
        return d;
    }
    private static void keys(ItemDefinition item,Map<?,?> map,String name,String... values) {
        HashSet<String> allowed=new HashSet<String>(Arrays.asList(values));
        for(Object key:map.keySet())if(!allowed.contains(key))throw bad(item,"unknown property "+name+"."+key);
    }
    private static IllegalArgumentException bad(ItemDefinition item,String message) {
        return new IllegalArgumentException(item.id()+": "+message);
    }
}
