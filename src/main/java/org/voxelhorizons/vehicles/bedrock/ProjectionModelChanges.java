package org.voxelhorizons.vehicles.bedrock;

import java.util.*;

/**
 * Finds Java display entities whose projected Bedrock model changed between two
 * schema-v1 snapshots. Geyser fixes an entity's custom definition at spawn, so
 * these entities must be re-sent to Bedrock viewers after the new feed is on disk.
 * Mirrors the identities read by VoxelCore's Geyser EntityProjectionIndex.
 */
final class ProjectionModelChanges {
    private ProjectionModelChanges() {}

    @SuppressWarnings("unchecked")
    static Map<UUID,String> visualModels(Map<String,Object> snapshot,String kind) {
        Map<UUID,String> models=new HashMap<UUID,String>();
        Object rows=snapshot==null?null:snapshot.get(kind);
        if(!(rows instanceof List))return models;
        for(Object rowObject:(List<Object>)rows) {
            if(!(rowObject instanceof Map))continue;
            Map<String,Object> row=(Map<String,Object>)rowObject;
            Object links=row.get("entity_links");
            if(!(links instanceof Map))continue;
            Map<String,Object> link=(Map<String,Object>)links;
            put(models,link.get("display_entity_uuid"),row.get("model_item"));
            put(models,link.get("visual"),row.get("model_item"));
            Object parts=link.get("parts");
            if(!(parts instanceof List))continue;
            for(Object partObject:(List<Object>)parts) {
                if(!(partObject instanceof Map))continue;
                Map<String,Object> part=(Map<String,Object>)partObject;
                if("ITEM".equals(part.get("type")))
                    put(models,part.get("java_entity_uuid"),part.get("model_item"));
            }
        }
        return models;
    }

    /** Entities present in both snapshots whose model differs. New or removed entities
     * need no re-send: Paper spawns/removes them and Geyser translates that itself. */
    static Set<UUID> changed(Map<UUID,String> previous,Map<UUID,String> next) {
        Set<UUID> result=new HashSet<UUID>();
        for(Map.Entry<UUID,String> entry:next.entrySet()) {
            String before=previous.get(entry.getKey());
            if(before!=null && !before.equals(entry.getValue()))result.add(entry.getKey());
        }
        return result;
    }

    private static void put(Map<UUID,String> models,Object uuid,Object model) {
        if(!(uuid instanceof String) || !(model instanceof String))return;
        try { models.put(UUID.fromString((String)uuid),(String)model); }
        catch(IllegalArgumentException ignored) { /* malformed ids are not projected */ }
    }
}
