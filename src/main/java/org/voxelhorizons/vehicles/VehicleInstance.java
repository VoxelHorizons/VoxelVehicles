package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
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
    public Entity display,hitbox,seat;
    public UUID driver;
    public VehiclePhysics.Input input=VehiclePhysics.Input.NEUTRAL;
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
