package org.voxelhorizons.vehicles;

/**
 * Rotates a mounted chest's local ItemDisplay matrix without sending entity
 * yaw packets. The carrier ItemDisplay owns world movement; its passenger
 * keeps the yaw it had when linked, and only the local model matrix turns.
 *
 * A normalized shortest-angle delta avoids 0/360 and +/-180 wraparound
 * interpolation artifacts. Vanilla clients interpolate the display matrix
 * quaternion while the host movement stays on its existing path.
 */
final class VehicleChestYaw {
    private VehicleChestYaw() {}

    static float delta(float targetYaw,float passengerYawAtMount){
        float difference=(targetYaw-passengerYawAtMount)%360.0f;
        if(difference<=-180.0f)difference+=360.0f;
        if(difference>180.0f)difference-=360.0f;
        return difference==0.0f?0.0f:difference;
    }

    /** Bukkit's positive yaw rotates opposite to positive JOML Y rotation. */
    static float matrixRadians(float targetYaw,float passengerYawAtMount){
        return (float)Math.toRadians(-delta(targetYaw,passengerYawAtMount));
    }
}
