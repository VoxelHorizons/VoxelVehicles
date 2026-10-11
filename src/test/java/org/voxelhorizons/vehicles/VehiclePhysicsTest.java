package org.voxelhorizons.vehicles;

import org.junit.Test;
import static org.junit.Assert.*;

/** Basic sign, normalization and input-clamping regression tests. */
public class VehiclePhysicsTest {
    @Test public void headingFollowsMinecraftYaw(){
        assertEquals(0,VehiclePhysics.dx(0),0.000001);
        assertEquals(1,VehiclePhysics.dz(0),0.000001);
        assertEquals(-1,VehiclePhysics.dx(90),0.000001);
        assertEquals(0,VehiclePhysics.dz(90),0.000001);
        assertEquals(1,VehiclePhysics.dx(-90),0.000001);
    }
    @Test public void inputIsClamped(){
        VehiclePhysics.Input input=new VehiclePhysics.Input(6,-4,true,false);
        assertEquals(1.0,input.throttle,0);
        assertEquals(-1.0,input.turn,0);
        assertTrue(input.jump);
        assertFalse(input.sneak);
    }
    @Test public void stationaryStateRemainsStationary(){
        VehiclePhysics.State state=new VehiclePhysics.State(45);
        assertEquals(0,state.speed,0);
        assertEquals(45,state.yaw,0);
    }
}
