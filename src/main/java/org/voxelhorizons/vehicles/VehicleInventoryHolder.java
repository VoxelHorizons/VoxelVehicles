package org.voxelhorizons.vehicles;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

/** Marks a VoxelVehicles GUI so other inventories are never intercepted. */
public final class VehicleInventoryHolder implements InventoryHolder {
    private final UUID vehicleId;
    private Inventory inventory;
    VehicleInventoryHolder(UUID vehicleId){this.vehicleId=vehicleId;}
    UUID vehicleId(){return vehicleId;}
    void bind(Inventory inventory){this.inventory=inventory;}
    @Override public Inventory getInventory(){return inventory;}
}
