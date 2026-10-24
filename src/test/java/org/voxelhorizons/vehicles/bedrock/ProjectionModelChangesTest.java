package org.voxelhorizons.vehicles.bedrock;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public final class ProjectionModelChangesTest {
    private static Map<String,Object> feed(UUID visual,String model) {
        Map<String,Object> links=new HashMap<String,Object>();
        links.put("visual",visual.toString());
        Map<String,Object> row=new HashMap<String,Object>();
        row.put("model_item",model);
        row.put("entity_links",links);
        Map<String,Object> root=new HashMap<String,Object>();
        root.put("schema",1);
        root.put("vehicles",Collections.singletonList(row));
        return root;
    }

    @Test public void reportsRootVisualModelSwapOnly() {
        UUID visual=UUID.randomUUID();
        Map<UUID,String> before=ProjectionModelChanges.visualModels(feed(visual,"voxel:tractor"),"vehicles");
        assertEquals("voxel:tractor",before.get(visual));
        Map<UUID,String> after=ProjectionModelChanges.visualModels(feed(visual,"voxel:tractor_red"),"vehicles");
        assertEquals(Collections.singleton(visual),ProjectionModelChanges.changed(before,after));
        assertTrue(ProjectionModelChanges.changed(before,before).isEmpty());
        assertTrue(ProjectionModelChanges.changed(Collections.<UUID,String>emptyMap(),after).isEmpty());
        UUID spawned=UUID.randomUUID();
        Map<UUID,String> withNew=new HashMap<UUID,String>(before);
        withNew.putAll(ProjectionModelChanges.visualModels(feed(spawned,"voxel:boat"),"vehicles"));
        assertEquals("New vehicles are re-sent once the feed lists them",
                Collections.singleton(spawned),ProjectionModelChanges.changed(before,withNew));
    }
}
