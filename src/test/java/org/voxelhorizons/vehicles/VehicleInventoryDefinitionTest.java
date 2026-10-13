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

    @Test public void tractorItemAcceptsPreviewSlotAndInvisibleFillItem(){
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("enabled",true);
        data.put("storage_enabled",true);
        data.put("rows",3);
        data.put("preview-slot",10);
        data.put("fill-item","voxel:ui/blank");
        data.put("preview_item","voxel:tractor_icon");
        data.put("storage_title","&f:offset_-8::vehicle_inventory_storage:");
        VehicleInventoryDefinition tractor=parse(data);
        // VoxelCore extends already resolves inherited properties before
        // passing them to the vehicle parser.
        VehicleInventoryDefinition yellow=VehicleInventoryDefinition.parse(
                ContentID.of("voxel","tractor-yellow"),data);
        assertEquals(Integer.valueOf(10),tractor.previewSlot);
        assertEquals("voxel:ui/blank",tractor.fillItem);
        assertEquals(3,tractor.effectiveStorageRows());
        assertEquals(tractor.previewSlot,yellow.previewSlot);
        assertEquals(tractor.fillItem,yellow.fillItem);
        assertEquals("voxel:tractor_icon",yellow.previewItem.toString());
    }

    @Test public void unsetOptionsInheritGlobalDefaults(){
        VehicleInventoryDefinition spec=parse(Collections.<String,Object>emptyMap());
        assertNull(spec.previewSlot);
        assertNull(spec.fillItem);
    }

    @Test public void blankFillItemCanDisableGlobalFiller(){
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("fill-item","");
        assertEquals("",parse(data).fillItem);
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectPreviewOutsideInventoryBounds(){
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("preview-slot",54);
        parse(data);
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectNonIntegerPreviewSlot(){
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("preview-slot",10.5);
        parse(data);
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectNonTextFiller(){
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("fill-item",true);
        parse(data);
    }

    @Test public void tractorCanOverrideAllSixStorageSlotsPerRow(){
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("rows",3);
        data.put("preview-slot",10);
        List<Integer> desired=Arrays.asList(
                3,4,5,6,7,8,12,13,14,15,16,17,21,22,23,24,25,26);
        data.put("storage-slots",desired);
        data.put("fill-copy-preview-meta",true);
        VehicleInventoryDefinition d=parse(data);
        assertEquals(desired,d.storageSlots);
        assertEquals(Boolean.TRUE,d.copyFillPreviewMeta);
        assertEquals(18,VehicleInventories.storageSlots(d.storageSlots,
                d.effectiveStorageRows(),d.previewSlot,27).size());
    }

    @Test public void storageSlotsDefaultToGlobalConfigWhenUnset(){
        VehicleInventoryDefinition d=parse(Collections.<String,Object>emptyMap());
        assertNull(d.storageSlots);
        assertNull(d.copyFillPreviewMeta);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void perVehicleStorageSlotsCannotMutateAfterParsing(){
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("storage-slots",Arrays.asList(3,4,5));
        parse(data).storageSlots.add(6);
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectDuplicateVehicleStorageSlots(){
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("storage-slots",Arrays.asList(3,3,4));
        parse(data);
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectInvalidStorageSlotTypes(){
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("storage-slots",Arrays.asList(3,"23",4));
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
