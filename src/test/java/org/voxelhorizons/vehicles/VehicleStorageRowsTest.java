package org.voxelhorizons.vehicles;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class VehicleStorageRowsTest {
    private static List<Integer> slots(){
        return Arrays.asList(
                4,5,6,7,8,
                13,14,15,16,17,
                22,23,24,25,26,
                31,32,33,34,35,
                40,41,42,43,44,
                49,50,51,52,53);
    }

    @Test public void noStorageKeepsPreviewOnly(){
        assertTrue(VehicleInventories.storageSlots(slots(),0,10,27).isEmpty());
    }
    @Test public void firstRowUsesExistingFiveSlots(){
        assertEquals(Arrays.asList(4,5,6,7,8),
                VehicleInventories.storageSlots(slots(),1,10,27));
    }
    @Test public void threeRowsPreserveExistingFifteenSlots(){
        assertEquals(slots().subList(0,15),
                VehicleInventories.storageSlots(slots(),3,10,27));
    }
    @Test public void sixRowsExposeThirtySlotsWithoutMovingEarlierOnes(){
        assertEquals(slots(),VehicleInventories.storageSlots(slots(),6,10,54));
    }
    @Test public void fourthRowGrowsMenuAndKeepsPreviousRows(){
        assertEquals(slots().subList(0,20),
                VehicleInventories.storageSlots(slots(),4,10,36));
    }
    @Test(expected=IllegalArgumentException.class)
    public void preventsPreviewBecomingStorage(){
        VehicleInventories.storageSlots(Arrays.asList(4,10,8),3,10,27);
    }
    @Test(expected=IllegalArgumentException.class)
    public void preventsDuplicateStorageSlots(){
        VehicleInventories.storageSlots(Arrays.asList(4,4,8),1,10,27);
    }
}
