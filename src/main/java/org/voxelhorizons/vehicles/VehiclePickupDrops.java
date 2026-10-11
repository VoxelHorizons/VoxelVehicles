package org.voxelhorizons.vehicles;

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
        if(vehicle==null||vehicleItem==null||vehicleItem.getAmount()<=0)
            throw new IllegalArgumentException("Vehicle pickup requires a valid vehicle item");
        List<ItemStack> drops=new ArrayList<ItemStack>();
        drops.add(vehicleItem.clone());
        if(vehicle.chestInstalled())drops.add(vehicle.installedChest.clone());
        for(ItemStack cargo:vehicle.storage)
            if(cargo!=null&&cargo.getAmount()>0)drops.add(cargo.clone());
        return Collections.unmodifiableList(drops);
    }
}
