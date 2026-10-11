package org.voxelhorizons.vehicles;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class VehicleRendererInterpolationTest {
    @Test public void occupiedVehiclesNeverSnapInstantly(){
        assertEquals(1,VehicleRenderer.clampMountedInterpolation(0));
        assertEquals(1,VehicleRenderer.clampMountedInterpolation(-4));
    }
    @Test public void oneTickSettingIsPreserved(){
        assertEquals(1,VehicleRenderer.clampMountedInterpolation(1));
        assertEquals(2,VehicleRenderer.clampMountedInterpolation(2));
        assertEquals(3,VehicleRenderer.clampMountedInterpolation(3));
    }
    @Test public void excessiveDelayIsClamped(){
        assertEquals(3,VehicleRenderer.clampMountedInterpolation(59));
    }
}
