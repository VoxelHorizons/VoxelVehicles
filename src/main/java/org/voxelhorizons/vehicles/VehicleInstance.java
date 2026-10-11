package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;
import java.util.UUID;

/** Logical vehicle identity is independent of transient entity UUIDs. */
public final class VehicleInstance {
    public final UUID id,owner;
    public final ContentID type;
    /** Per-placement RGB color; null means use VoxelCore's default item rendering. */
    public final Integer dyeColor;
    public final VehiclePhysics.State motion;
    public Location location;
    public Entity display,hitbox,seat,chestDisplay,chestHitbox;
    public UUID driver;
    /** True when the rider (or compatibility seat) rides the ItemDisplay root. */
    public boolean linkedSeat;
    /** Cosmetic terrain suspension only; never rotates the physical hitbox or rider. */
    public double visualPitch,visualRoll;
    public VehiclePhysics.Input input=VehiclePhysics.Input.NEUTRAL;
    /** Logical per-vehicle cargo, saved independently from display entities. */
    final ItemStack[] storage=new ItemStack[54];
    /** The original upgrade ItemStack is returned when the chest is detached. */
    ItemStack installedChest;
    boolean chestInstalled(){return installedChest!=null&&installedChest.getAmount()>0;}

    boolean hasStoredItems(){
        for(ItemStack stack:storage)if(stack!=null&&stack.getAmount()>0)return true;
        return false;
    }
    public VehicleInstance(UUID id,ContentID type,UUID owner,Location location) {
        this(id,type,owner,location,null);
    }
    public VehicleInstance(UUID id,ContentID type,UUID owner,Location location,Integer dyeColor) {
        if(dyeColor!=null&&(dyeColor.intValue()<0||dyeColor.intValue()>0xFFFFFF))
            throw new IllegalArgumentException("Invalid RGB vehicle color");
        this.id=id;this.owner=owner;this.type=type;this.location=location.clone();
        this.dyeColor=dyeColor;
        this.motion=new VehiclePhysics.State(location.getYaw());
    }
    public boolean active(){return display!=null&&display.isValid();}
    public void stop(){driver=null;input=VehiclePhysics.Input.NEUTRAL;motion.speed=0;}
}
