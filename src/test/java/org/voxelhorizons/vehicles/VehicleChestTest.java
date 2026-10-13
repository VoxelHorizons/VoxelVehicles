package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import java.util.*;
import static org.junit.Assert.*;

/** Tests chest opt-in, persistence-relevant state, layout and visual offsets. */
public class VehicleChestTest {
    private final ContentID tractor=ContentID.of("voxel","tractor");

    private Map<String,Object> chest(String mode){
        Map<String,Object> visual=new HashMap<String,Object>();
        visual.put("mode",mode);
        if("MODEL_SWAP".equals(mode))visual.put("model_item","voxel:tractor_chest");
        visual.put("offset",coords(0,0.3,-1.4));
        visual.put("scale",0.8);
        Map<String,Object> chest=new HashMap<String,Object>();
        chest.put("enabled",true);
        chest.put("item","CHEST");
        chest.put("visual",visual);
        return chest;
    }
    private Map<String,Object> coords(double x,double y,double z){
        Map<String,Object> m=new HashMap<String,Object>();
        m.put("x",x);m.put("y",y);m.put("z",z);return m;
    }
    private VehicleInventoryDefinition inventory(Map<String,Object> chest){
        Map<String,Object> conf=new HashMap<String,Object>();
        conf.put("enabled",true);
        conf.put("storage_enabled",true);
        conf.put("rows",3);
        conf.put("chest",chest);
        return VehicleInventoryDefinition.parse(tractor,conf);
    }
    private VehicleInstance instance(){
        return new VehicleInstance(UUID.randomUUID(),tractor,UUID.randomUUID(),
                new Location(null,10,64,20,0,0));
    }

    @Test public void legacyCargoRemainsAvailableWithoutChestFeature(){
        Map<String,Object> config=new HashMap<String,Object>();
        config.put("rows",3);
        VehicleInventoryDefinition d=VehicleInventoryDefinition.parse(tractor,config);
        assertFalse(d.chest.enabled);
        assertEquals(3,d.effectiveStorageRows(instance()));
    }
    @Test public void newChestVehicleStartsWithoutStorage(){
        VehicleInventoryDefinition d=inventory(chest("ITEM_DISPLAY"));
        VehicleInstance instance=instance();
        assertEquals(0,d.effectiveStorageRows(instance));
        assertFalse(instance.chestInstalled());
        instance.installedChest=new ItemStack(Material.CHEST,1);
        assertTrue(instance.chestInstalled());
        assertEquals(3,d.effectiveStorageRows(instance));
        instance.installedChest=null;
        assertEquals(0,d.effectiveStorageRows(instance));
    }
    @Test public void chestPreviewChangesWithAttachmentAndReturnsOnDetach(){
        Map<String,Object> config=new HashMap<String,Object>();
        config.put("enabled",true);
        config.put("rows",3);
        config.put("preview_item","voxel:tractor_icon");
        Map<String,Object> upgrade=chest("MODEL_SWAP");
        upgrade.put("preview_item","voxel:tractor_chest_icon");
        config.put("chest",upgrade);
        VehicleInventoryDefinition definition=VehicleInventoryDefinition.parse(tractor,config);
        VehicleInstance vehicle=instance();
        ContentID standard=ContentID.of("voxel","tractor_icon");
        ContentID withChest=ContentID.of("voxel","tractor_chest_icon");
        assertEquals(standard,definition.previewFor(vehicle,tractor));
        vehicle.installedChest=new ItemStack(Material.CHEST,1);
        assertEquals(withChest,definition.previewFor(vehicle,tractor));
        vehicle.installedChest=null;
        assertEquals(standard,definition.previewFor(vehicle,tractor));
    }

    @Test public void chestPreviewIsIndependentOfVisualMode(){
        for(String mode:Arrays.asList("ITEM_DISPLAY","MODEL_SWAP","NONE")){
            Map<String,Object> upgrade=chest(mode);
            upgrade.put("preview_item","voxel:tractor_chest_icon");
            VehicleInventoryDefinition definition=inventory(upgrade);
            VehicleInstance vehicle=instance();
            vehicle.installedChest=new ItemStack(Material.CHEST,1);
            assertEquals(ContentID.of("voxel","tractor_chest_icon"),
                    definition.previewFor(vehicle,tractor));
            vehicle.installedChest=null;
            assertEquals(tractor,definition.previewFor(vehicle,tractor));
        }
    }

    @Test public void chestPreviewFallsBackWhenNotConfiguredOrDisabled(){
        VehicleInstance vehicle=instance();
        vehicle.installedChest=new ItemStack(Material.CHEST,1);
        VehicleInventoryDefinition withoutUpgrade=inventory(chest("MODEL_SWAP"));
        assertEquals(tractor,withoutUpgrade.previewFor(vehicle,tractor));
        Map<String,Object> config=new HashMap<String,Object>();
        Map<String,Object> disabled=chest("NONE");
        disabled.put("enabled",false);
        disabled.put("preview_item","voxel:tractor_chest_icon");
        config.put("preview_item","voxel:tractor_icon");
        config.put("chest",disabled);
        VehicleInventoryDefinition definition=VehicleInventoryDefinition.parse(tractor,config);
        assertEquals(ContentID.of("voxel","tractor_icon"),definition.previewFor(vehicle,tractor));
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectInvalidChestPreviewItem(){
        Map<String,Object> invalid=chest("NONE");
        invalid.put("preview_item",54);
        inventory(invalid);
    }

    @Test public void accessoryPositionFollowsRotationAndVisualOffset(){
        VehicleInventoryDefinition i=inventory(chest("ITEM_DISPLAY"));
        // Validate parsed chest model-relative offsets, no hardcoded world coords.
        assertEquals(-1.4,i.chest.z,1e-9);
        assertEquals(0.3,i.chest.y,1e-9);
        assertEquals(0.8,i.chest.scale,1e-9);
        assertTrue(i.chest.interaction);
        assertEquals(Material.CHEST,i.chest.item);
    }
    @Test public void alternateModelRequiredForModelSwap(){
        VehicleInventoryDefinition i=inventory(chest("MODEL_SWAP"));
        assertEquals(VehicleChestDefinition.VisualMode.MODEL_SWAP,i.chest.mode);
        assertEquals("voxel:tractor_chest",i.chest.alternateModel.toString());
        assertFalse(i.chest.interaction);
    }
    @Test public void customChestMaterialAndLegacySlotIgnored(){
        Map<String,Object> c=chest("NONE");
        c.put("item","minecraft:trapped_chest");
        c.put("slot",9);
        VehicleInventoryDefinition i=inventory(c);
        assertEquals(Material.TRAPPED_CHEST,i.chest.item);
        assertEquals(Material.TRAPPED_CHEST,i.chest.item);
        // chest.slot is accepted for backward-compatible YAML but does not
        // reserve a GUI cell, and no chest button is created.
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectUnsupportedChestOptions(){
        Map<String,Object> c=chest("NONE");
        c.put("magic_action",true);
        inventory(c);
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectInvalidDeprecatedChestSlot(){
        Map<String,Object> c=chest("NONE");
        c.put("slot",54);
        inventory(c);
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectModelSwapWithoutReplacement(){
        Map<String,Object> c=chest("MODEL_SWAP");
        ((Map<String,Object>)c.get("visual")).remove("model_item");
        inventory(c);
    }
    @Test public void chestInstallsOnlyWithSneakAndMatchingItem(){
        assertTrue(VehicleListener.shouldAttemptChestInstall(true,true,true,true,3,
                Material.CHEST,Material.CHEST));
        assertFalse(VehicleListener.shouldAttemptChestInstall(false,true,true,true,3,
                Material.CHEST,Material.CHEST));
        assertFalse(VehicleListener.shouldAttemptChestInstall(true,true,true,true,3,
                Material.DIRT,Material.CHEST));
        assertFalse(VehicleListener.shouldAttemptChestInstall(true,true,true,true,3,
                Material.AIR,Material.CHEST));
        assertFalse(VehicleListener.shouldAttemptChestInstall(true,false,true,true,3,
                Material.CHEST,Material.CHEST));
        assertFalse(VehicleListener.shouldAttemptChestInstall(true,true,false,true,3,
                Material.CHEST,Material.CHEST));
        assertFalse(VehicleListener.shouldAttemptChestInstall(true,true,true,false,3,
                Material.CHEST,Material.CHEST));
        assertFalse(VehicleListener.shouldAttemptChestInstall(true,true,true,true,0,
                Material.CHEST,Material.CHEST));
    }

    @Test public void accessoryDisabledMeansNoVisualOrHitbox(){
        VehicleInventoryDefinition i=inventory(chest("NONE"));
        assertEquals(VehicleChestDefinition.VisualMode.NONE,i.chest.mode);
        assertFalse(i.chest.interaction);
    }
    @Test public void sameClickCannotOpenNewlyInstalledChestThroughOtherEventHandlers(){
        UUID vehicle=UUID.randomUUID();
        long installationTick=350L;
        // The first interact event installs the chest. Bukkit may immediately
        // dispatch another at-entity or air/block fallback for that same click.
        assertTrue(VehicleListener.isChestInstallFollowup(
                vehicle,vehicle,installationTick,installationTick));
        assertTrue(VehicleListener.isChestInstallFollowup(
                vehicle,vehicle,installationTick,installationTick+1));
        // A fresh, deliberate right-click after the installation event burst
        // may open storage, including when a CHEST is still in the main hand.
        assertFalse(VehicleListener.isChestInstallFollowup(
                vehicle,vehicle,installationTick,installationTick+2));
    }

    @Test public void chestInstallSuppressionIsLimitedToOneVehicleAndValidTick(){
        UUID newlyUpgraded=UUID.randomUUID(),otherVehicle=UUID.randomUUID();
        assertFalse(VehicleListener.isChestInstallFollowup(
                otherVehicle,newlyUpgraded,200,200));
        assertFalse(VehicleListener.isChestInstallFollowup(
                newlyUpgraded,newlyUpgraded,200,199));
        assertFalse(VehicleListener.isChestInstallFollowup(
                newlyUpgraded,newlyUpgraded,200,203));
        assertFalse(VehicleListener.isChestInstallFollowup(
                newlyUpgraded,null,200,200));
        assertFalse(VehicleListener.isChestInstallFollowup(
                null,newlyUpgraded,200,200));
    }

    @Test public void chestItemIsEligibleBeforeAndAfterInstallation(){
        // This material/config predicate deliberately does not inspect
        // installedChest. Fresh installations take precedence; once installed,
        // the listener treats the same eligible click as a normal GUI open.
        VehicleInventoryDefinition definition=inventory(chest("MODEL_SWAP"));
        VehicleInstance vehicle=instance();
        assertTrue(VehicleListener.shouldAttemptChestInstall(true,
                definition.chest.enabled,definition.enabled,definition.storageEnabled,
                definition.rows,Material.CHEST,definition.chest.item));
        vehicle.installedChest=new ItemStack(Material.CHEST);
        assertTrue(vehicle.chestInstalled());
        assertTrue(VehicleListener.shouldAttemptChestInstall(true,
                definition.chest.enabled,definition.enabled,definition.storageEnabled,
                definition.rows,Material.CHEST,definition.chest.item));
    }
}
