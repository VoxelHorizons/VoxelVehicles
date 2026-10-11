package org.voxelhorizons.vehicles;

import org.bukkit.Location;

/**
 * All authored offsets use the same local axes as VoxelFurniture:
 * X right, Y up, Z forward, rotated with the current vehicle yaw.
 * A vehicle's global offset moves the complete visual assembly, including hitbox and seat.
 */
final class VehicleTransform {
    private VehicleTransform() {}

    static Location local(Location origin, float yaw, double x, double y, double z) {
        double radians = Math.toRadians(yaw);
        double sine = Math.sin(radians), cosine = Math.cos(radians);
        Location at = origin.clone().add(x * cosine - z * sine, y, x * sine + z * cosine);
        at.setYaw(yaw);
        return at;
    }

    static Location visual(Location origin, VehicleDefinition definition) {
        Location at=local(origin,origin.getYaw(),definition.offsetX,definition.offsetY,definition.offsetZ);
        at.setYaw(origin.getYaw()+(float)definition.offsetRotation);
        return at;
    }

    static Location hitbox(Location origin, VehicleDefinition definition) {
        Location display=visual(origin,definition);
        return local(display,display.getYaw(),definition.hitboxX,
                definition.hitboxY+definition.height*0.5,definition.hitboxZ);
    }

    static Location collisionBase(Location origin, VehicleDefinition definition) {
        Location display=visual(origin,definition);
        return local(display,display.getYaw(),definition.hitboxX,definition.hitboxY,definition.hitboxZ);
    }

    static Location seat(Location origin, VehicleDefinition definition) {
        Location display=visual(origin,definition);
        return local(display,display.getYaw(),definition.seatX,definition.seatY,definition.seatZ);
    }
}
