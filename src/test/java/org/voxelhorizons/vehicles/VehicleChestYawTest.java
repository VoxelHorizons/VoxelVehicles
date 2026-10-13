package org.voxelhorizons.vehicles;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Turning is applied at the seat pivot in the local display matrix.
 * The passenger entity never needs its own per-tick world yaw updates.
 */
public class VehicleChestYawTest {
    @Test public void rotationTracksBothDirectionsAroundOriginalHeading(){
        assertEquals(0,VehicleChestYaw.delta(30,30),0.0001f);
        assertEquals(15,VehicleChestYaw.delta(45,30),0.0001f);
        assertEquals(-15,VehicleChestYaw.delta(15,30),0.0001f);
        assertEquals(90,VehicleChestYaw.delta(120,30),0.0001f);
        assertEquals(-90,VehicleChestYaw.delta(-60,30),0.0001f);
    }

    @Test public void crossingTheYawBoundaryDoesNotPerformFullTurn(){
        assertEquals(2,VehicleChestYaw.delta(-179,179),0.0001f);
        assertEquals(-2,VehicleChestYaw.delta(179,-179),0.0001f);
        assertEquals(5,VehicleChestYaw.delta(362,-3),0.0001f);
        assertEquals(0,VehicleChestYaw.delta(370,10),0.0001f);
    }

    @Test public void localMatrixUsesTheSameWorldHeadingAsVehicleRoot(){
        final double x=0.8,z=-1.5;
        final float initial=135;
        for(int deg=-720;deg<=720;deg+=5){
            float target=deg;
            // A fixed passenger initial yaw, then a matrix Y rotation
            // of -delta, is equal to vehicle render-root yaw.
            double passengerYaw=Math.toRadians(initial);
            double localDelta=VehicleChestYaw.matrixRadians(target,initial);
            double worldX=rotateX(passengerYaw+(-localDelta),x,z);
            double worldZ=rotateZ(passengerYaw+(-localDelta),x,z);
            double expectedX=rotateX(Math.toRadians(target),x,z);
            double expectedZ=rotateZ(Math.toRadians(target),x,z);
            assertEquals("x at yaw "+target,expectedX,worldX,1e-5);
            assertEquals("z at yaw "+target,expectedZ,worldZ,1e-5);
        }
    }

    private static double rotateX(double angle,double x,double z){
        return x*Math.cos(angle)-z*Math.sin(angle);
    }
    private static double rotateZ(double angle,double x,double z){
        return x*Math.sin(angle)+z*Math.cos(angle);
    }

    @Test public void quaternionArcIsSmallForContinuousSteering(){
        float initial=87.5f;
        for(float current=-720;current<720;current+=0.5f){
            float a=VehicleChestYaw.delta(current,initial);
            float b=VehicleChestYaw.delta(current+0.5f,initial);
            // Wrapped orientations have the same quaternion orientation,
            // but degrees may differ by one full turn near +/-180.
            float angularDifference=VehicleChestYaw.delta(b,a);
            assertEquals(0.5f,angularDifference,0.0001f);
        }
    }
}
