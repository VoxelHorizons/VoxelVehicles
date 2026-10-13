package org.voxelhorizons.vehicles;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/** Preserves the exact VoxelCore item ID and Bukkit metadata through pickup. */
final class VehiclePickupItem {
    private VehiclePickupItem(){}

    /** A placed stack always represents exactly one vehicle, even if stacked. */
    static ItemStack capture(ItemStack source){
        if(source==null||source.getAmount()<=0||source.getType()==Material.AIR
                ||source.getType().name().endsWith("_AIR"))
            throw new IllegalArgumentException("Vehicle source item missing or invalid");
        ItemStack one=source.clone();
        one.setAmount(1);
        return one;
    }

    /**
     * Prefer the ORIGINAL item saved on placement, not the parent or the
     * definition's optional display/drop model. Only pre-migration instances
     * need rebuilding from their exact placed content ID.
     *
     * Creation and tint callbacks are called only for old saved vehicles.
     */
    static ItemStack resolve(VehicleInstance vehicle,
            Function<ContentID,ItemStack> create,
            Predicate<ItemStack> dyeable,
            BiFunction<ItemStack,Integer,ItemStack> color){
        if(vehicle==null)throw new IllegalArgumentException("Missing vehicle");
        if(vehicle.originalItem!=null)return capture(vehicle.originalItem);
        // This is the placed item's identity, whereas vehicle.model and
        // vehicle.drop may point to an abstract or generic parent model.
        ItemStack reconstructed=create.apply(vehicle.type);
        if(reconstructed!=null&&vehicle.dyeColor!=null&&dyeable.test(reconstructed))
            reconstructed=color.apply(reconstructed,vehicle.dyeColor);
        return capture(reconstructed);
    }
}
