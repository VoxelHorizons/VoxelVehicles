package org.voxelhorizons.vehicles;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class VehicleTerrainTest {
    private VehicleTerrain.Box obstacle(double height){
        return new VehicleTerrain.Box(0,0,0,1,height,1,0,0,0);
    }

    @Test public void fullBlockIsClimbableAtOneBlockStep(){
        double rise=VehicleTerrain.riseForObstructions(0,0,1.0,
                Collections.singletonList(obstacle(1)));
        assertEquals(1,rise,0.00001);
    }

    @Test public void halfSlabUsesActualHalfHeight(){
        double rise=VehicleTerrain.riseForObstructions(0,0,1.0,
                Collections.singletonList(obstacle(0.5)));
        assertEquals(0.5,rise,0.00001);
    }

    @Test public void neverClimbTwoBlocks(){
        double rise=VehicleTerrain.riseForObstructions(0,0,1.0,
                Collections.singletonList(obstacle(2)));
        assertTrue(Double.isNaN(rise));
    }

    @Test public void customStepRestrictionRemainsRespected(){
        assertTrue(Double.isNaN(VehicleTerrain.riseForObstructions(0,0,0.5,
                Collections.singletonList(obstacle(1)))));
    }

    @Test public void visualPitchHasConfigurableLimit(){
        assertEquals(20,VehicleTerrain.inclination(1,0,1,20),0.001);
        assertEquals(-20,VehicleTerrain.inclination(0,1,1,20),0.001);
        assertEquals(0,VehicleTerrain.inclination(0,0,2,20),0.001);
    }

    @Test public void tiltSmoothingConvergesWithoutOvershoot(){
        assertEquals(4.8,VehicleTerrain.smooth(0,20,0.24),0.001);
        assertEquals(20,VehicleTerrain.smooth(20,20,0.24),0.001);
    }

    @Test public void groundContactDoesNotDriftUpward(){
        // Gravity moved the target down 0.12; an existing block top at y=65
        // should return exactly to y=65, without accumulating epsilon.
        double rise=VehicleTerrain.riseForObstructions(65,64.88,1.0,
                Collections.singletonList(obstacle(65)));
        assertEquals(65,64.88+rise,0.0000001);
    }
}
