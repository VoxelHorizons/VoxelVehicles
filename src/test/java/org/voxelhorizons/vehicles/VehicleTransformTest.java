package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VehicleTransformTest {
    @Test public void offsetsAreLocalToVehicleYaw(){
        Location origin=new Location(null,12,64,8,0,0);
        Location zero=VehicleTransform.local(origin,0,2,1,3);
        assertEquals(14,zero.getX(),1.0e-6);
        assertEquals(65,zero.getY(),1.0e-6);
        assertEquals(11,zero.getZ(),1.0e-6);
        Location quarter=VehicleTransform.local(origin,90,2,1,3);
        assertEquals(9,quarter.getX(),1.0e-6);
        assertEquals(65,quarter.getY(),1.0e-6);
        assertEquals(10,quarter.getZ(),1.0e-6);
        assertEquals(12,origin.getX(),1.0e-6);
    }
}
