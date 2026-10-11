package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** No chest or cargo (including hidden slots) may disappear during pickup. */
public class VehiclePickupDropsTest {
    private static VehicleInstance vehicle(){
        return new VehicleInstance(UUID.randomUUID(),
                ContentID.parse("voxel:tractor","voxel"),
                UUID.randomUUID(),new Location(null,0.5,64,0.5,45,0));
    }

    @Test public void plainVehicleReturnsOnlyItsVoxelCoreItem(){
        VehicleInstance v=vehicle();
        ItemStack tractor=new ItemStack(Material.PAPER);
        List<ItemStack> drops=VehiclePickupDrops.snapshot(v,tractor);
        assertEquals(1,drops.size());
        assertEquals(Material.PAPER,drops.get(0).getType());
        assertNotSame(tractor,drops.get(0));
    }

    @Test public void installedChestAndHiddenCargoAreAllReturned(){
        VehicleInstance v=vehicle();
        v.installedChest=new ItemStack(Material.CHEST,1);
        v.storage[0]=new ItemStack(Material.DIAMOND,42);
        v.storage[17]=new ItemStack(Material.EMERALD,7);
        v.storage[53]=new ItemStack(Material.GOLD_INGOT,18); // hidden sixth-row slot
        List<ItemStack> drops=VehiclePickupDrops.snapshot(v,new ItemStack(Material.PAPER));
        assertEquals(5,drops.size());
        assertEquals(Material.PAPER,drops.get(0).getType());
        assertEquals(Material.CHEST,drops.get(1).getType());
        assertEquals(Material.DIAMOND,drops.get(2).getType());
        assertEquals(42,drops.get(2).getAmount());
        assertEquals(Material.EMERALD,drops.get(3).getType());
        assertEquals(Material.GOLD_INGOT,drops.get(4).getType());
        assertEquals(18,drops.get(4).getAmount());
    }

    @Test public void everyCargoIndexIsPreservedWithDuplicates(){
        VehicleInstance v=vehicle();
        for(int i=0;i<v.storage.length;i++)v.storage[i]=new ItemStack(Material.STONE,i+1);
        v.installedChest=new ItemStack(Material.CHEST);
        List<ItemStack> drops=VehiclePickupDrops.snapshot(v,new ItemStack(Material.PAPER));
        assertEquals(56,drops.size()); // tractor + chest + 54 cargo stacks
        for(int i=0;i<v.storage.length;i++)
            assertEquals(i+1,drops.get(i+2).getAmount());
    }

    @Test public void snapshotsAreClonesAndCannotMutateTheStoredVehicle(){
        VehicleInstance v=vehicle();
        v.installedChest=new ItemStack(Material.CHEST,1);
        v.storage[20]=new ItemStack(Material.DIAMOND,12);
        ItemStack tractor=new ItemStack(Material.PAPER);
        List<ItemStack> drops=VehiclePickupDrops.snapshot(v,tractor);
        drops.get(0).setAmount(9);
        drops.get(1).setAmount(5);
        drops.get(2).setAmount(1);
        assertEquals(1,tractor.getAmount());
        assertEquals(1,v.installedChest.getAmount());
        assertEquals(12,v.storage[20].getAmount());
        assertEquals(3,drops.size());
    }

    @Test public void emptySlotsAreIgnored(){
        VehicleInstance v=vehicle();
        v.storage[5]=new ItemStack(Material.DIRT,0);
        assertEquals(1,VehiclePickupDrops.snapshot(v,new ItemStack(Material.PAPER)).size());
    }

    @Test(expected=IllegalArgumentException.class)
    public void neverStartPickupWithMissingVehicleDrop(){
        VehiclePickupDrops.snapshot(vehicle(),null);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void dropSnapshotCannotBeStructurallyAltered(){
        VehiclePickupDrops.snapshot(vehicle(),new ItemStack(Material.PAPER))
                .add(new ItemStack(Material.CHEST));
    }
}
