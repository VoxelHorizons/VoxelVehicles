package org.voxelhorizons.vehicles;

import org.bukkit.Material;
import org.voxelhorizons.content.ContentID;
import java.util.*;

/** Optional donkey-like installable chest; never enabled for ordinary vehicles. */
final class VehicleChestDefinition {
    enum VisualMode { NONE, ITEM_DISPLAY, MODEL_SWAP }

    final boolean enabled;
    final Material item;
    final VisualMode mode;
    final ContentID visualItem,alternateModel,previewItem;
    final double x,y,z,scale,yaw;
    final boolean followTilt,interaction,attachToVehicle;
    final double clickWidth,clickHeight;

    private VehicleChestDefinition(boolean enabled,Material item,VisualMode mode,
            ContentID visualItem,ContentID alternateModel,ContentID previewItem,
            double x,double y,double z,double scale,double yaw,boolean followTilt,
            boolean interaction,boolean attachToVehicle,double clickWidth,double clickHeight){
        this.enabled=enabled;this.item=item;this.mode=mode;
        this.visualItem=visualItem;this.alternateModel=alternateModel;this.previewItem=previewItem;
        this.x=x;this.y=y;this.z=z;this.scale=scale;this.yaw=yaw;
        this.followTilt=followTilt;this.interaction=interaction;
        this.attachToVehicle=attachToVehicle;
        this.clickWidth=clickWidth;this.clickHeight=clickHeight;
    }

    static VehicleChestDefinition parse(ContentID owner,Object raw){
        if(raw==null)return new VehicleChestDefinition(false,Material.CHEST,
                VisualMode.NONE,null,null,null,0,0,0,1,0,false,false,false,0.8,0.8);
        Map<?,?> m=mapping(owner,raw,"chest");
        // Legacy chest.slot is intentionally ignored: there is no GUI button.
        // Accept older YAML so existing worlds don't fail plugin startup.
        keys(owner,m,"chest","enabled","item","slot","preview_item","visual");
        Object enable=m.get("enabled");
        if(enable!=null&&!(enable instanceof Boolean))
            throw bad(owner,"chest.enabled must be boolean");
        boolean enabled=Boolean.TRUE.equals(enable);
        String materialName=String.valueOf(m.containsKey("item")?m.get("item"):"CHEST");
        if(materialName.startsWith("minecraft:"))materialName=materialName.substring(10);
        Material material=Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
        if(material==null||!material.isItem())
            throw bad(owner,"chest.item is not a valid Minecraft item material: "+materialName);
        if(m.containsKey("slot"))integer(owner,m,"slot",0,0,53);
        Map<?,?> visual=mapping(owner,m.get("visual"),"chest.visual");
        keys(owner,visual,"chest.visual","mode","item","model_item","offset","scale",
                "yaw","follow_tilt","interaction","attach_to_vehicle","interaction_width","interaction_height");
        VisualMode mode;
        try{mode=VisualMode.valueOf(String.valueOf(visual.containsKey("mode")
                ?visual.get("mode"):"ITEM_DISPLAY").toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException e){throw bad(owner,"chest.visual.mode must be NONE, ITEM_DISPLAY or MODEL_SWAP");}
        ContentID itemId=optionalId(owner,visual.get("item"),"chest.visual.item");
        ContentID alternative=optionalId(owner,visual.get("model_item"),"chest.visual.model_item");
        ContentID preview=optionalId(owner,m.get("preview_item"),"chest.preview_item");
        if(enabled&&mode==VisualMode.MODEL_SWAP&&alternative==null)
            throw bad(owner,"chest.visual.model_item is required for MODEL_SWAP");
        Map<?,?> offset=mapping(owner,visual.get("offset"),"chest.visual.offset");
        keys(owner,offset,"chest.visual.offset","x","y","z");
        boolean tilt=bool(owner,visual,"follow_tilt",true);
        boolean interaction=bool(owner,visual,"interaction",true);
        boolean attach=bool(owner,visual,"attach_to_vehicle",true);
        return new VehicleChestDefinition(enabled,material,mode,itemId,alternative,preview,
                number(owner,offset,"x",0,-16,16),
                number(owner,offset,"y",0,-16,16),
                number(owner,offset,"z",0,-16,16),
                number(owner,visual,"scale",1,0.01,16),
                number(owner,visual,"yaw",0,-360,360),
                tilt,interaction&&mode==VisualMode.ITEM_DISPLAY,attach,
                number(owner,visual,"interaction_width",0.8,0.1,5),
                number(owner,visual,"interaction_height",0.8,0.1,5));
    }

    private static ContentID optionalId(ContentID owner,Object raw,String path){
        if(raw==null)return null;
        if(!(raw instanceof String))throw bad(owner,path+" must be a VoxelCore item ID");
        try{return ContentID.parse((String)raw,owner.namespace());}
        catch(IllegalArgumentException e){throw bad(owner,path+": "+e.getMessage());}
    }
    private static Map<?,?> mapping(ContentID id,Object raw,String path){
        if(raw==null)return Collections.emptyMap();
        if(!(raw instanceof Map))throw bad(id,path+" must be a mapping");
        return (Map<?,?>)raw;
    }
    private static void keys(ContentID id,Map<?,?> m,String section,String... allowed){
        Set<String> keys=new HashSet<String>(Arrays.asList(allowed));
        for(Object key:m.keySet())if(!keys.contains(key))
            throw bad(id,"unknown "+section+" property "+key);
    }
    private static boolean bool(ContentID id,Map<?,?> m,String name,boolean fallback){
        Object raw=m.get(name);
        if(raw==null)return fallback;
        if(!(raw instanceof Boolean))throw bad(id,"chest.visual."+name+" must be boolean");
        return ((Boolean)raw).booleanValue();
    }
    private static int integer(ContentID id,Map<?,?> m,String key,int fallback,int min,int max){
        Object raw=m.get(key);
        if(raw==null)return fallback;
        if(!(raw instanceof Number)||!Double.isFinite(((Number)raw).doubleValue())
                ||((Number)raw).doubleValue()!=Math.rint(((Number)raw).doubleValue())
                ||((Number)raw).intValue()<min||((Number)raw).intValue()>max)
            throw bad(id,"chest."+key+" must be an integer between "+min+" and "+max);
        return ((Number)raw).intValue();
    }
    private static double number(ContentID id,Map<?,?> m,String key,
            double fallback,double min,double max){
        Object raw=m.get(key);
        if(raw==null)return fallback;
        if(!(raw instanceof Number))throw bad(id,"chest.visual."+key+" must be numeric");
        double value=((Number)raw).doubleValue();
        if(!Double.isFinite(value)||value<min||value>max)
            throw bad(id,"chest.visual."+key+" must be between "+min+" and "+max);
        return value;
    }
    private static IllegalArgumentException bad(ContentID id,String error){
        return new IllegalArgumentException(id+": vehicle.inventory."+error);
    }
}
