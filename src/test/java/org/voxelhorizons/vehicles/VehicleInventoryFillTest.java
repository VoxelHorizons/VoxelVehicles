package org.voxelhorizons.vehicles;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemRenderDefinition;

import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.Assert.*;

public class VehicleInventoryFillTest {
    @Test public void previewMovedLeftIsNeverFiller(){
        List<Integer> cargo=VehicleInventories.storageSlots(
                Arrays.asList(4,5,6,7,8,13,14,15,16,17,22,23,24,25,26),
                3,10,27);
        List<Integer> filler=VehicleInventories.decorativeSlots(27,10,cargo);
        assertEquals(11,filler.size());
        assertFalse(filler.contains(10));
        for(int slot:cargo)assertFalse(filler.contains(slot));
        assertTrue(filler.contains(9));assertTrue(filler.contains(11));
    }

    @Test public void previewOnlyMenuFillsEveryOtherSlot(){
        List<Integer> filler=VehicleInventories.decorativeSlots(27,10,
                Collections.<Integer>emptyList());
        assertEquals(26,filler.size());
        assertFalse(filler.contains(10));
        assertTrue(filler.contains(4));
    }

    @Test public void sixRowMenuFillsOnlyNonCargoSlots(){
        List<Integer> cargo=VehicleInventories.storageSlots(
                Arrays.asList(4,5,6,7,8,13,14,15,16,17,22,23,24,25,26,
                        31,32,33,34,35,40,41,42,43,44,49,50,51,52,53),6,10,54);
        assertEquals(23,VehicleInventories.decorativeSlots(54,10,cargo).size());
    }

    @Test public void modelPathCanResolveRegisteredVoxelCoreItem(){
        ContentID ui=ContentID.of("voxel","ui-blank");
        Map<ContentID,ItemDefinition> definitions=new HashMap<ContentID,ItemDefinition>();
        definitions.put(ui,new ItemDefinition(ui,null,"PAPER"," ",
                Collections.<String>emptyList(),true,Optional.<ContentID>empty(),
                new ItemRenderDefinition("voxel:ui/blank",null),
                Collections.<String,Object>emptyMap()));
        assertEquals(ui,VehicleInventories.findFillItem(definitions,
                "voxel:ui/blank"));
        assertEquals(ui,VehicleInventories.findFillItem(definitions,ui.toString()));
    }

    @Test(expected=IllegalArgumentException.class)
    public void unknownFillModelIsRejectedClearly(){
        VehicleInventories.findFillItem(Collections.<ContentID,ItemDefinition>emptyMap(),
                "voxel:missing_blank");
    }

    @Test public void sixColumnStorageDoesNotFillSlots3_12Or23(){
        List<Integer> slots=Arrays.asList(3,4,5,6,7,8,12,13,14,15,16,17,
                21,22,23,24,25,26);
        List<Integer> active=VehicleInventories.storageSlots(slots,3,10,27);
        List<Integer> filler=VehicleInventories.decorativeSlots(27,10,active);
        assertEquals(18,active.size());
        for(int slot:new int[]{3,12,23}) {
            assertTrue(active.contains(slot));
            assertFalse(filler.contains(slot));
        }
        assertEquals(8,filler.size());
    }

    @Test public void previewTitleAndLoreCopyToFillerWithoutReplacingModel(){
        ItemMeta source=metadata("§6Tractor",Arrays.asList("§7Engine", "§fStorage"));
        ItemMeta target=metadata("Invisible",Arrays.asList("Should be removed"));
        VehicleInventories.copyPreviewHover(target,source);
        assertEquals("§6Tractor",target.getDisplayName());
        assertEquals(Arrays.asList("§7Engine","§fStorage"),target.getLore());
        // A subsequent change to the source lore must not mutate the copy.
        source.setLore(Collections.singletonList("Different"));
        assertEquals(Arrays.asList("§7Engine","§fStorage"),target.getLore());
    }

    @Test public void previewWithoutLoreClearsFillerLore(){
        ItemMeta source=metadata(null,null);
        ItemMeta target=metadata("Other",Collections.singletonList("Other lore"));
        VehicleInventories.copyPreviewHover(target,source);
        assertFalse(target.hasDisplayName());
        assertFalse(target.hasLore());
    }

    private static ItemMeta metadata(String name,List<String> lore){
        final Map<String,Object> values=new HashMap<String,Object>();
        values.put("name",name);
        values.put("lore",lore==null?null:new ArrayList<String>(lore));
        return (ItemMeta)Proxy.newProxyInstance(
                ItemMeta.class.getClassLoader(),new Class<?>[]{ItemMeta.class},
                (proxy,method,args)->{
                    switch(method.getName()){
                        case "hasDisplayName":return values.get("name")!=null;
                        case "getDisplayName":return values.get("name");
                        case "setDisplayName":values.put("name",args[0]);return null;
                        case "hasLore":return values.get("lore")!=null;
                        case "getLore":return values.get("lore")==null?null:
                                new ArrayList<String>((List<String>)values.get("lore"));
                        case "setLore":values.put("lore",args[0]==null?null:
                                new ArrayList<String>((List<String>)args[0]));return null;
                        default:throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    @Test public void shiftClickNeverReplacesPreviewOrFiller(){
        final ItemStack[] slots=new ItemStack[27];
        slots[10]=new ItemStack(Material.PAPER,1);
        slots[9]=new ItemStack(Material.GLASS,1);
        Inventory inventory=(Inventory)Proxy.newProxyInstance(
                Inventory.class.getClassLoader(),new Class<?>[]{Inventory.class},
                (proxy,method,args)->{
                    if("getItem".equals(method.getName()))return slots[(Integer)args[0]];
                    if("setItem".equals(method.getName())){slots[(Integer)args[0]]=(ItemStack)args[1];return null;}
                    if("getMaxStackSize".equals(method.getName()))return 64;
                    throw new UnsupportedOperationException(method.getName());
                });
        List<Integer> cargo=Arrays.asList(4,5,6,7,8);
        ItemStack leftover=VehicleInventories.moveIntoCargo(inventory,cargo,
                new ItemStack(Material.STONE,42));
        assertNull(leftover);
        assertEquals(Material.STONE,slots[4].getType());
        assertEquals(42,slots[4].getAmount());
        assertEquals(Material.PAPER,slots[10].getType());
        assertEquals(Material.GLASS,slots[9].getType());
    }

    @Test public void fullCargoWithDifferentItemPreservesRemainingItems(){
        final ItemStack[] slots=new ItemStack[27];
        slots[4]=new ItemStack(Material.DIRT,64);
        Inventory inventory=(Inventory)Proxy.newProxyInstance(
                Inventory.class.getClassLoader(),new Class<?>[]{Inventory.class},
                (proxy,method,args)->{
                    if("getItem".equals(method.getName()))return slots[(Integer)args[0]];
                    if("setItem".equals(method.getName())){slots[(Integer)args[0]]=(ItemStack)args[1];return null;}
                    if("getMaxStackSize".equals(method.getName()))return 64;
                    throw new UnsupportedOperationException(method.getName());
                });
        ItemStack input=new ItemStack(Material.STONE,12);
        ItemStack leftover=VehicleInventories.moveIntoCargo(inventory,
                Collections.singletonList(4),input);
        assertEquals(12,leftover.getAmount());
        assertEquals(12,input.getAmount());
        assertEquals(Material.DIRT,slots[4].getType());
        assertEquals(64,slots[4].getAmount());
    }
}
