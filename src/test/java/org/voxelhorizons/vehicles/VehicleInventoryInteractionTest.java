package org.voxelhorizons.vehicles;

import org.junit.Test;
import static org.junit.Assert.*;

public class VehicleInventoryInteractionTest {
    @Test public void shiftRightClickCannotPickUpTractor(){
        assertTrue(VehicleListener.shouldSuppressPickup(true,null,100L));
        assertTrue(VehicleListener.shouldSuppressPickup(true,Long.valueOf(99L),100L));
    }
    @Test public void animationAfterRightClickCannotTriggerPickup(){
        assertTrue(VehicleListener.shouldSuppressPickup(false,Long.valueOf(100L),100L));
        assertTrue(VehicleListener.shouldSuppressPickup(false,Long.valueOf(100L),102L));
    }
    @Test public void deliberateNormalLeftClickCanStillPickUp(){
        assertFalse(VehicleListener.shouldSuppressPickup(false,null,100L));
        assertFalse(VehicleListener.shouldSuppressPickup(false,Long.valueOf(100L),103L));
    }
    @Test public void backwardWorldTimeDoesNotBlockPickupForever(){
        assertFalse(VehicleListener.shouldSuppressPickup(false,Long.valueOf(999L),10L));
    }
}
