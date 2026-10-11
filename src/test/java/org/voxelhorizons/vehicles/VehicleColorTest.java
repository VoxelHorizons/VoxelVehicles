package org.voxelhorizons.vehicles;
import org.bukkit.Location;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import java.util.UUID;
import static org.junit.Assert.*;
public class VehicleColorTest {
    @Test public void colorPersistsInInstance() {
        VehicleInstance v = new VehicleInstance(UUID.randomUUID(),
            ContentID.of("voxel", "tractor"), UUID.randomUUID(),
            new Location(null, 1, 2, 3), Integer.valueOf(0x4287F5));
        assertEquals(Integer.valueOf(0x4287F5), v.dyeColor);
    }
    @Test public void missingColorStaysNull() {
        VehicleInstance v = new VehicleInstance(UUID.randomUUID(),
            ContentID.of("voxel", "tractor"), UUID.randomUUID(), new Location(null, 1, 2, 3));
        assertNull(v.dyeColor);
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectRgbOutOfRange() {
        new VehicleInstance(UUID.randomUUID(), ContentID.of("voxel", "tractor"),
            UUID.randomUUID(), new Location(null, 1, 2, 3), Integer.valueOf(0x1000000));
    }
}