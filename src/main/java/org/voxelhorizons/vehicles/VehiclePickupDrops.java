package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * All item stacks returned when reclaiming a placed vehicle.
 *
 * Storage is saved by logical cargo index, not GUI slot; check every index,
 * including slots currently hidden by configuration changes. Each stack is
 * cloned so later changes cannot alter the drop snapshot or its metadata.
 */
final class VehiclePickupDrops {
    private VehiclePickupDrops(){}

    static List<ItemStack> snapshot(VehicleInstance vehicle,ItemStack vehicleItem){
        if(vehicle==null||!droppable(vehicleItem))
            throw new IllegalArgumentException("Vehicle pickup requires a valid vehicle item");
        List<ItemStack> drops=new ArrayList<ItemStack>();
        drops.add(vehicleItem.clone());
        if(vehicle.chestInstalled()&&droppable(vehicle.installedChest))
            drops.add(vehicle.installedChest.clone());
        for(ItemStack cargo:vehicle.storage)
            if(droppable(cargo))drops.add(cargo.clone());
        return Collections.unmodifiableList(drops);
    }

    private static boolean droppable(ItemStack stack){
        if(stack==null||stack.getAmount()<=0)return false;
        Material material=stack.getType();
        return material!=Material.AIR&&!material.name().endsWith("_AIR");
    }

    /**
     * The server may reject an individual item spawn (world hooks, plugins,
     * invalid items). Roll back any already-spawned drops before rethrowing.
     */
    static List<Item> spawn(World world,Location at,List<ItemStack> drops){
        List<Item> spawned=new ArrayList<Item>(drops.size());
        try{
            for(ItemStack drop:drops){
                Item item=world.dropItemNaturally(at,drop);
                if(item==null)throw new IllegalStateException("World rejected a vehicle pickup drop");
                spawned.add(item);
            }
        }catch(RuntimeException failure){
            rollback(spawned);
            throw failure;
        }
        return spawned;
    }

    static void rollback(List<Item> spawned){
        for(Item drop:spawned){
            if(drop==null)continue;
            try{if(drop.isValid())drop.remove();}
            catch(RuntimeException ignored){/* best-effort cleanup */ }
        }
    }
}
