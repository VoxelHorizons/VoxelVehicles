package org.voxelhorizons.vehicles;

import org.junit.Test;
import static org.junit.Assert.*;

/** Falling must accelerate, cap speed, preserve momentum and pitch by direction. */
public class VehicleAirborneTest {
    @Test public void gravityAcceleratesDownward(){
        double v=0;
        for(int tick=0;tick<10;tick++)v=VehiclePhysics.fallSpeed(v,0.08,1.6);
        assertEquals(-0.8,v,1e-9);
    }

    @Test public void fallSpeedIsCapped(){
        double v=0;
        for(int tick=0;tick<100;tick++)v=VehiclePhysics.fallSpeed(v,0.08,1.6);
        assertEquals(-1.6,v,1e-9);
        assertEquals(-1.6,VehiclePhysics.fallSpeed(-100,0.08,1.6),1e-9);
    }

    @Test public void forwardsFallPointsNoseDown(){
        assertTrue(VehiclePhysics.airbornePitch(0.28,-0.6,25,0.8)<-5);
        assertEquals(-25,VehiclePhysics.airbornePitch(0.28,-10,25,2),1e-9);
    }

    @Test public void reversingFallPointsRearDown(){
        assertTrue(VehiclePhysics.airbornePitch(-0.12,-0.6,25,0.8)>5);
        assertEquals(0,VehiclePhysics.airbornePitch(0,-0.6,25,1),1e-9);
        assertEquals(0,VehiclePhysics.airbornePitch(0.2,0,25,1),1e-9);
    }

    @Test public void noPitchPastLimit(){
        for(double speed:new double[]{-1.0,-0.1,0.1,1.0}){
            double pitch=VehiclePhysics.airbornePitch(speed,-2,15,2);
            assertTrue(Math.abs(pitch)<=15.000001);
        }
    }
}
