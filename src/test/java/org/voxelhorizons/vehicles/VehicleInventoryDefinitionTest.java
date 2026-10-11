package org.voxelhorizons.vehicles;

import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import java.util.*;
import static org.junit.Assert.*;

public class VehicleInventoryDefinitionTest {
    private final ContentID tractor=ContentID.of("voxel","tractor");
    private VehicleInventoryDefinition parse(Map<String,Object> map){
        return VehicleInventoryDefinition.parse(tractor,map);
    }
    @Test public void noStorageUsesBaseLayoutByDefault(){
        VehicleInventoryDefinition spec=VehicleInventoryDefinition.parse(tractor,null);
        assertTrue(spec.enabled);
        assertFalse(spec.storageEnabled);
        assertEquals(0,spec.rows);
        assertEquals(0,spec.effectiveStorageRows());
        assertNull(spec.previewItem);
        assertNull(spec.title);
    }
    @Test public void overrideStorageAndPreviewWithoutHardcodedGlyphs(){
        Map<String,Object> values=new HashMap<String,Object>();
        values.put("enabled",true);
        values.put("rows",3);
        values.put("preview_item","voxel:tractor_inventory_preview");
        values.put("storage_title",":my_own_inventory_glyph:");
        VehicleInventoryDefinition spec=parse(values);
        assertTrue(spec.enabled);
        assertTrue(spec.storageEnabled);
        assertEquals(3,spec.rows);
        assertEquals(3,spec.effectiveStorageRows());
        assertEquals("voxel:tractor_inventory_preview",spec.previewItem.toString());
        assertEquals(":my_own_inventory_glyph:",spec.storageTitle);
    }
    @Test public void storageCanBeDisabledWithoutDisablingVehiclePreview(){
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("enabled",true);
        data.put("storage_enabled",false);
        data.put("rows",3);
        VehicleInventoryDefinition d=parse(data);
        assertTrue(d.enabled);
        assertEquals(3,d.rows);
        assertFalse(d.storageEnabled);
        assertEquals(0,d.effectiveStorageRows());
    }

    @Test public void storageCanBeReenabledWithoutLosingConfiguredRowCount(){
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("storage_enabled",true);
        data.put("rows",5);
        assertEquals(5,parse(data).effectiveStorageRows());
    }

    @Test(expected=IllegalArgumentException.class)
    public void storageToggleMustBeBoolean(){
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("storage_enabled","false");
        parse(data);
    }

    @Test public void allSixRowsSupported(){
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("rows",6);
        assertEquals(6,parse(data).rows);
    }
    @Test public void disabledVehicleCannotOpen(){
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("enabled",false);
        assertFalse(parse(data).enabled);
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsExcessStorage(){
        Map<String,Object> values=new HashMap<String,Object>();
        values.put("rows",7);
        parse(values);
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsUnsupportedKeys(){
        Map<String,Object> values=new HashMap<String,Object>();
        values.put("hardcoded_inventory_alias",true);
        parse(values);
    }
}
