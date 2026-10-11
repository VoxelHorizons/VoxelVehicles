package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks locations last successfully sent to VoxelVehicles-owned entities.
 *
 * Bukkit Entity.teleport is relatively expensive, especially for display
 * entities with passengers. Only teleport when the target has actually
 * changed. Interaction hitboxes are axis-aligned and do not need yaw updates
 * if their world position is unchanged.
 *
 * The tracker is per logical vehicle and is cleared whenever its renderers
 * are deactivated. A newly spawned entity has its initial pose recorded.
 * Every legitimate movement still uses the regular Bukkit teleport API.
 *
 * Called only from the Paper server thread.
 */
final class VehicleTeleportTracker {
    private static final class Pose {
        final World world;
        final double x, y, z;
        final float yaw, pitch;

        Pose(Location at) {
            world=at.getWorld();
            x=at.getX();y=at.getY();z=at.getZ();
            yaw=at.getYaw();pitch=at.getPitch();
        }
    }

    private final Map<UUID,Pose> last=new HashMap<UUID,Pose>(5);

    /**
     * Exact comparison: no positional epsilon or frame skipping, which would
     * otherwise introduce jitter or desynchronize the driver and chest.
     */
    static boolean changed(Location before,Location after,boolean rotationMatters) {
        if(before==null || after==null)return true;
        return before.getWorld()!=after.getWorld()
                || Double.compare(before.getX(),after.getX())!=0
                || Double.compare(before.getY(),after.getY())!=0
                || Double.compare(before.getZ(),after.getZ())!=0
                || (rotationMatters && (Float.compare(before.getYaw(),after.getYaw())!=0
                        || Float.compare(before.getPitch(),after.getPitch())!=0));
    }

    private static boolean changed(Pose before,Location after,boolean rotationMatters) {
        if(before==null || after==null)return true;
        return before.world!=after.getWorld()
                || Double.compare(before.x,after.getX())!=0
                || Double.compare(before.y,after.getY())!=0
                || Double.compare(before.z,after.getZ())!=0
                || (rotationMatters && (Float.compare(before.yaw,after.getYaw())!=0
                        || Float.compare(before.pitch,after.getPitch())!=0));
    }

    /**
     * True when the entity is already at target or was successfully moved.
     * False means Bukkit rejected the teleport; do not cache failed moves.
     */
    boolean ensure(Entity entity,Location target,boolean rotationMatters){
        if(entity==null||!entity.isValid())return false;
        UUID id=entity.getUniqueId();
        if(!changed(last.get(id),target,rotationMatters))return true;
        if(!entity.teleport(target))return false;
        last.put(id,new Pose(target));
        return true;
    }

    /** A newly spawned entity is already at its spawn target. */
    void spawned(Entity entity,Location initial) {
        if(entity!=null&&initial!=null)last.put(entity.getUniqueId(),new Pose(initial));
    }

    void forget(Entity entity){
        if(entity!=null)last.remove(entity.getUniqueId());
    }

    void clear(){last.clear();}
}
