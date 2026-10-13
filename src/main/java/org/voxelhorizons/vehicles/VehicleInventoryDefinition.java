package org.voxelhorizons.vehicles;

import org.voxelhorizons.content.ContentID;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * Per-vehicle inventory capabilities. UI glyph titles and chest slot layout
 * deliberately live in plugin config, never in hardcoded Java strings.
 */
final class VehicleInventoryDefinition {
    final boolean enabled;
    final boolean storageEnabled;
    final int rows;
    final ContentID previewItem;
    /** null means inherit the global plugin config value. */
    final Integer previewSlot;
    /** null means inherit globally; empty string disables fillers for this vehicle. */
    final String fillItem;
    /** null means inherit the plugin-wide slot mapping. */
    final List<Integer> storageSlots;
    /** null means inherit the plugin-wide fill metadata setting. */
    final Boolean copyFillPreviewMeta;
    final String title,storageTitle;
    final VehicleChestDefinition chest;

    private VehicleInventoryDefinition(boolean enabled,boolean storageEnabled,int rows,ContentID previewItem,
            Integer previewSlot,String fillItem,List<Integer> storageSlots,Boolean copyFillPreviewMeta,
            String title,String storageTitle,VehicleChestDefinition chest){
        this.enabled=enabled;this.storageEnabled=storageEnabled;this.rows=rows;this.previewItem=previewItem;
        this.previewSlot=previewSlot;this.fillItem=fillItem;
        this.storageSlots=storageSlots==null?null:Collections.unmodifiableList(new ArrayList<Integer>(storageSlots));
        this.copyFillPreviewMeta=copyFillPreviewMeta;
        this.title=title;this.storageTitle=storageTitle;this.chest=chest;
    }

    static VehicleInventoryDefinition parse(ContentID id,Object raw){
        if(raw==null)return new VehicleInventoryDefinition(true,false,0,null,null,null,null,null,null,null,
                VehicleChestDefinition.parse(id,null));
        if(!(raw instanceof Map))throw bad(id,"inventory must be a mapping");
        Map<?,?> data=(Map<?,?>)raw;
        Set<String> keys=new HashSet<String>();
        Collections.addAll(keys,"enabled","storage_enabled","rows","preview_item","preview-slot","fill-item","storage-slots","fill-copy-preview-meta","title","storage_title","chest");
        for(Object key:data.keySet())
            if(!keys.contains(key))throw bad(id,"unknown inventory property "+key);
        Object enabled=data.get("enabled");
        if(enabled!=null&&!(enabled instanceof Boolean))
            throw bad(id,"inventory.enabled must be a boolean");
        Object storageEnabled=data.get("storage_enabled");
        if(storageEnabled!=null&&!(storageEnabled instanceof Boolean))
            throw bad(id,"inventory.storage_enabled must be a boolean");
        Object size=data.get("rows");
        int storageRows=0;
        if(size!=null){
            if(!(size instanceof Number)||!Double.isFinite(((Number)size).doubleValue())
                    ||((Number)size).doubleValue()!=Math.rint(((Number)size).doubleValue())
                    ||((Number)size).intValue()<0||((Number)size).intValue()>6)
                throw bad(id,"inventory.rows must be an integer between 0 and 6");
            storageRows=((Number)size).intValue();
        }
        ContentID preview=null;
        if(data.containsKey("preview_item")){
            if(!(data.get("preview_item") instanceof String))
                throw bad(id,"inventory.preview_item must be a VoxelCore item ID");
            preview=ContentID.parse((String)data.get("preview_item"),id.namespace());
        }
        Integer previewSlot=null;
        if(data.containsKey("preview-slot")){
            Object slot=data.get("preview-slot");
            if(!(slot instanceof Number)
                    ||!Double.isFinite(((Number)slot).doubleValue())
                    ||((Number)slot).doubleValue()!=Math.rint(((Number)slot).doubleValue())
                    ||((Number)slot).intValue()<0||((Number)slot).intValue()>53)
                throw bad(id,"inventory.preview-slot must be an integer between 0 and 53");
            previewSlot=Integer.valueOf(((Number)slot).intValue());
        }
        String fillItem=title(id,data.get("fill-item"),"fill-item");
        Boolean copyFillPreviewMeta=null;
        if(data.containsKey("fill-copy-preview-meta")){
            Object copy=data.get("fill-copy-preview-meta");
            if(!(copy instanceof Boolean))
                throw bad(id,"inventory.fill-copy-preview-meta must be a boolean");
            copyFillPreviewMeta=(Boolean)copy;
        }
        List<Integer> customSlots=null;
        if(data.containsKey("storage-slots")){
            Object configured=data.get("storage-slots");
            if(!(configured instanceof List))throw bad(id,"inventory.storage-slots must be a list");
            List<?> input=(List<?>)configured;
            customSlots=new ArrayList<Integer>(input.size());
            Set<Integer> unique=new HashSet<Integer>();
            for(Object rawSlot:input){
                if(!(rawSlot instanceof Number)
                        ||!Double.isFinite(((Number)rawSlot).doubleValue())
                        ||((Number)rawSlot).doubleValue()!=Math.rint(((Number)rawSlot).doubleValue())
                        ||((Number)rawSlot).intValue()<0||((Number)rawSlot).intValue()>53)
                    throw bad(id,"inventory.storage-slots must contain only integer slots 0..53");
                int index=((Number)rawSlot).intValue();
                if(!unique.add(index))throw bad(id,"inventory.storage-slots contains duplicate slot "+index);
                customSlots.add(index);
            }
        }
        String title=title(id,data.get("title"),"title");
        String withStorage=title(id,data.get("storage_title"),"storage_title");
        return new VehicleInventoryDefinition(!Boolean.FALSE.equals(enabled),
                !Boolean.FALSE.equals(storageEnabled)&&storageRows>0,
                storageRows,preview,previewSlot,fillItem,customSlots,copyFillPreviewMeta,title,withStorage,
                VehicleChestDefinition.parse(id,data.get("chest")));
    }
    int effectiveStorageRows(){return storageEnabled?rows:0;}
    int effectiveStorageRows(VehicleInstance vehicle){
        return storageEnabled&&(!chest.enabled||vehicle.chestInstalled())?rows:0;
    }

    /** Switch only the inventory preview when an optional chest upgrade is installed. */
    ContentID previewFor(VehicleInstance vehicle,ContentID defaultModel){
        if(chest.enabled&&vehicle.chestInstalled()&&chest.previewItem!=null)
            return chest.previewItem;
        return previewItem!=null?previewItem:defaultModel;
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
