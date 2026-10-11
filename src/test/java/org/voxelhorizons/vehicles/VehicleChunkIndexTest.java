package org.voxelhorizons.vehicles;

import org.junit.Test;
import java.util.Collection;
import java.util.UUID;
import static org.junit.Assert.*;

/** Stress the spatial index without spawning Bukkit entities or loading chunks. */
public class VehicleChunkIndexTest {
    @Test public void threeHundredVehiclesAreFoundByOriginChunkWithoutGlobalScans(){
        VehicleChunkIndex chunks=new VehicleChunkIndex();
        UUID world=UUID.randomUUID();
        UUID[] ids=new UUID[300];
        for(int i=0;i<ids.length;i++){
            ids[i]=UUID.randomUUID();
            chunks.update(ids[i],world,(i%15)*16+0.5,(i/15)*16+0.5);
        }
        assertEquals(300,chunks.size());
        assertEquals(1,chunks.inChunk(world,4,7).size());
        assertTrue(chunks.inChunk(world,500,500).isEmpty());
        assertEquals(1,chunks.nearby(world,4*16+8,7*16+8,5).size());
    }

    @Test public void movingAcrossNegativeChunkBoundariesUpdatesLookup(){
        VehicleChunkIndex chunks=new VehicleChunkIndex();
        UUID world=UUID.randomUUID(),id=UUID.randomUUID();
        chunks.update(id,world,-0.5,0);
        assertTrue(chunks.inChunk(world,-1,0).contains(id));
        chunks.update(id,world,0,0);
        assertFalse(chunks.inChunk(world,-1,0).contains(id));
        assertTrue(chunks.inChunk(world,0,0).contains(id));
        chunks.update(id,world,15.99,0);
        assertEquals(1,chunks.inChunk(world,0,0).size());
        chunks.update(id,world,16.0,0);
        assertTrue(chunks.inChunk(world,0,0).isEmpty());
        assertTrue(chunks.inChunk(world,1,0).contains(id));
    }

    @Test public void nearbySearchFindsAcrossChunkBordersAndKeepsWorldsSeparate(){
        VehicleChunkIndex chunks=new VehicleChunkIndex();
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        UUID west=UUID.randomUUID(),east=UUID.randomUUID(),distant=UUID.randomUUID();
        chunks.update(west,a,15.5,8);
        chunks.update(east,a,16.5,8);
        chunks.update(distant,a,99,99);
        chunks.update(UUID.randomUUID(),b,15.5,8);
        Collection<UUID> near=chunks.nearby(a,16,8,5);
        assertEquals(2,near.size());
        assertTrue(near.contains(west));
        assertTrue(near.contains(east));
        assertFalse(near.contains(distant));
    }

    @Test public void clickingAnOffsetVehicleUsesHitboxCentreNotDistantOrigin(){
        VehicleChunkIndex originIndex=new VehicleChunkIndex();
        VehicleChunkIndex clickIndex=new VehicleChunkIndex();
        UUID world=UUID.randomUUID(),tractor=UUID.randomUUID();
        // Authored visual/hitbox offsets can place the interaction centre
        // several chunks away from the physics origin.
        originIndex.update(tractor,world,0.5,0.5);
        clickIndex.update(tractor,world,48.5,0.5);
        assertFalse(originIndex.nearby(world,48.5,0.5,13).contains(tractor));
        assertTrue(clickIndex.nearby(world,48.5,0.5,13).contains(tractor));
        // Content reload can move the authored hitbox without moving vehicle.
        clickIndex.update(tractor,world,64.5,0.5);
        assertTrue(clickIndex.nearby(world,64.5,0.5,13).contains(tractor));
    }

    @Test public void removalAndReseatingDoNotLeaveStaleEntries(){
        VehicleChunkIndex chunks=new VehicleChunkIndex();
        UUID world=UUID.randomUUID(),id=UUID.randomUUID();
        chunks.update(id,world,8,8);
        Collection<UUID> old=chunks.inChunk(world,0,0);
        chunks.update(id,world,64,8);
        assertEquals(1,old.size()); // snapshot is independent of updates
        assertTrue(chunks.inChunk(world,0,0).isEmpty());
        assertEquals(1,chunks.inChunk(world,4,0).size());
        chunks.remove(id);
        assertEquals(0,chunks.size());
        assertTrue(chunks.inChunk(world,4,0).isEmpty());
        chunks.clear();
        assertEquals(0,chunks.size());
    }
}
