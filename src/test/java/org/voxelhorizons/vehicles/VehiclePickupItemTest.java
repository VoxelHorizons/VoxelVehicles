package org.voxelhorizons.vehicles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Original custom metadata must round-trip through pickup and saved data. */
public class VehiclePickupItemTest {
    private static final ContentID CONCRETE=ContentID.of("voxel","yellow_tractor");
    private static final ContentID PARENT=ContentID.of("voxel","tractor_base");

    private VehicleInstance vehicle(){
        return new VehicleInstance(UUID.randomUUID(),CONCRETE,UUID.randomUUID(),
                new Location(null,4.5,64,9.5,90,0),Integer.valueOf(0xFFE100));
    }

    private ItemStack original(){
        ItemStack item=new ItemStack(Material.PAPER,16);
        item.setDurability((short)5);
        // On runtimes with a Bukkit ItemFactory, also validate name and lore.
        // The actual production ItemStack is copied without re-authoring meta.
        if(Bukkit.getServer()!=null){
            ItemMeta meta=item.getItemMeta();
            meta.setDisplayName("§eYELLOW TRACTOR");
            meta.setLore(Arrays.asList("§7Custom serialized lore","§6Owner edition"));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Test public void pickupClonesExactPlacedStackInsteadOfRebuildingParent(){
        VehicleInstance v=vehicle();
        ItemStack hand=original();
        v.originalItem=VehiclePickupItem.capture(hand);
        hand.setAmount(1); // PlayerInteractEvent consumes the stack afterward
        AtomicInteger factoryCalls=new AtomicInteger();
        ItemStack reclaimed=VehiclePickupItem.resolve(v,id->{
            factoryCalls.incrementAndGet();
            return new ItemStack(Material.CHEST);
        },item->false,(item,rgb)->item);
        assertEquals(0,factoryCalls.get());
        assertEquals(1,reclaimed.getAmount());
        assertEquals(v.originalItem,reclaimed);
        assertNotSame(v.originalItem,reclaimed);
        assertEquals(hand.getDurability(),reclaimed.getDurability());
        if(hand.hasItemMeta()){
            assertEquals(hand.getItemMeta().getDisplayName(),
                    reclaimed.getItemMeta().getDisplayName());
            assertEquals(hand.getItemMeta().getLore(),
                    reclaimed.getItemMeta().getLore());
        }
    }

    @Test public void legacyVehicleFallsBackToExactConcreteIdNotParent(){
        VehicleInstance legacy=vehicle();
        assertNull(legacy.originalItem);
        AtomicReference<ContentID> requested=new AtomicReference<ContentID>();
        AtomicInteger tintCalls=new AtomicInteger();
        ItemStack result=VehiclePickupItem.resolve(legacy,id->{
            requested.set(id);
            return new ItemStack(Material.PAPER,1);
        },item->true,(item,color)->{
            assertEquals(Integer.valueOf(0xFFE100),color);
            tintCalls.incrementAndGet();
            return item;
        });
        assertEquals(CONCRETE,requested.get());
        assertNotEquals(PARENT,requested.get());
        assertEquals(1,tintCalls.get());
        assertEquals(Material.PAPER,result.getType());
    }

    @Test public void previousCustomSourceDoesNotGetRecoloredOrMutated(){
        VehicleInstance v=vehicle();
        v.originalItem=VehiclePickupItem.capture(original());
        assertEquals(1,v.originalItem.getAmount());
        ItemStack originalBefore=v.originalItem.clone();
        ItemStack item=VehiclePickupItem.resolve(v,id->{
            fail("must not build generic item");return null;
        },stack->{
            fail("must not recolor existing item");return false;
        },(stack,color)->{
            fail("must not overwrite original custom components");return null;
        });
        item.setAmount(9);
        assertEquals(originalBefore,v.originalItem);
        assertEquals(1,v.originalItem.getAmount());
    }

    @Test public void originalAndCargoRemainDistinctAfterYamlRoundTrip()throws Exception{
        VehicleInstance v=vehicle();
        v.originalItem=VehiclePickupItem.capture(original());
        v.installedChest=new ItemStack(Material.CHEST);
        v.storage[15]=new ItemStack(Material.DIAMOND,23);
        YamlConfiguration yaml=new YamlConfiguration();
        yaml.set("vehicles.1.original-item",v.originalItem.clone());
        yaml.set("vehicles.1.installed-chest",v.installedChest.clone());
        yaml.set("vehicles.1.storage.15",v.storage[15].clone());
        YamlConfiguration restored=new YamlConfiguration();
        restored.loadFromString(yaml.saveToString());
        assertEquals(v.originalItem,restored.getItemStack("vehicles.1.original-item"));
        assertEquals(v.installedChest,restored.getItemStack("vehicles.1.installed-chest"));
        assertEquals(v.storage[15],restored.getItemStack("vehicles.1.storage.15"));
        if(v.originalItem.hasItemMeta()){
            assertEquals(v.originalItem.getItemMeta().getDisplayName(),
                    restored.getItemStack("vehicles.1.original-item").getItemMeta().getDisplayName());
            assertEquals(v.originalItem.getItemMeta().getLore(),
                    restored.getItemStack("vehicles.1.original-item").getItemMeta().getLore());
        }
    }

    @Test public void legacyPickupStillIncludesChestAndCargo(){
        VehicleInstance v=vehicle();
        v.installedChest=new ItemStack(Material.CHEST);
        v.storage[53]=new ItemStack(Material.EMERALD,3);
        ItemStack rebuilt=VehiclePickupItem.resolve(v,id->new ItemStack(Material.PAPER),
                item->false,(item,color)->item);
        assertEquals(3,VehiclePickupDrops.snapshot(v,rebuilt).size());
    }

    @Test(expected=IllegalArgumentException.class)
    public void cannotCaptureAirVehicle(){
        VehiclePickupItem.capture(new ItemStack(Material.AIR));
    }

    @Test public void stackOfVehiclesIsSavedAndReturnedOneAtATime(){
        ItemStack item=original();
        assertEquals(16,item.getAmount());
        ItemStack stored=VehiclePickupItem.capture(item);
        assertEquals(16,item.getAmount());
        assertEquals(1,stored.getAmount());
    }
}
