package org.voxelhorizons.vehicles;

import org.voxelhorizons.content.ContentID;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-vehicle inventory capabilities. UI glyph titles and chest slot layout
 * deliberately live in plugin config, never in hardcoded Java strings.
 */
final class VehicleInventoryDefinition {
    final boolean enabled;
    final int storageSize;
    final ContentID previewItem;
    final String title,storageTitle;

    private VehicleInventoryDefinition(boolean enabled,int storageSize,ContentID previewItem,
            String title,String storageTitle){
        this.enabled=enabled;this.storageSize=storageSize;this.previewItem=previewItem;
        this.title=title;this.storageTitle=storageTitle;
    }

    static VehicleInventoryDefinition parse(ContentID id,Object raw){
        if(raw==null)return new VehicleInventoryDefinition(true,0,null,null,null);
        if(!(raw instanceof Map))throw bad(id,"inventory must be a mapping");
        Map<?,?> data=(Map<?,?>)raw;
        Set<String> keys=new HashSet<String>();
        Collections.addAll(keys,"enabled","storage_size","preview_item","title","storage_title");
        for(Object key:data.keySet())
            if(!keys.contains(key))throw bad(id,"unknown inventory property "+key);
        Object enabled=data.get("enabled");
        if(enabled!=null&&!(enabled instanceof Boolean))
            throw bad(id,"inventory.enabled must be a boolean");
        Object size=data.get("storage_size");
        int capacity=0;
        if(size!=null){
            if(!(size instanceof Number)||!Double.isFinite(((Number)size).doubleValue())
                    ||((Number)size).doubleValue()!=Math.rint(((Number)size).doubleValue())
                    ||((Number)size).intValue()<0||((Number)size).intValue()>54)
                throw bad(id,"inventory.storage_size must be an integer between 0 and 54");
            capacity=((Number)size).intValue();
        }
        ContentID preview=null;
        if(data.containsKey("preview_item")){
            if(!(data.get("preview_item") instanceof String))
                throw bad(id,"inventory.preview_item must be a VoxelCore item ID");
            preview=ContentID.parse((String)data.get("preview_item"),id.namespace());
        }
        String title=title(id,data.get("title"),"title");
        String withStorage=title(id,data.get("storage_title"),"storage_title");
        return new VehicleInventoryDefinition(!Boolean.FALSE.equals(enabled),
                capacity,preview,title,withStorage);
    }
    private static String title(ContentID id,Object raw,String field){
        if(raw==null)return null;
        if(!(raw instanceof String))throw bad(id,"inventory."+field+" must be text");
        return (String)raw;
    }
    private static IllegalArgumentException bad(ContentID id,String reason){
        return new IllegalArgumentException(id+": "+reason);
    }
}
