package org.voxelhorizons.vehicles;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Simulates hundreds of riders sampling the same blocks in one physics tick. */
public class VehicleTerrainFrameTest {
    private static final class FakeWorld {
        final AtomicInteger shapeLookups=new AtomicInteger();
        final AtomicInteger loadedChecks=new AtomicInteger();
        Material material=Material.AIR;
        boolean loaded=true;
        final World world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class},(proxy,method,args)->{
                    switch(method.getName()){
                        case "isChunkLoaded":
                            loadedChecks.incrementAndGet();
                            return loaded;
                        case "getBlockAt":
                            shapeLookups.incrementAndGet();
                            return (Block)Proxy.newProxyInstance(Block.class.getClassLoader(),
                                    new Class<?>[]{Block.class},(b,m,a)->{
                                        if(m.getName().equals("getType"))return material;
                                        if(m.getName().equals("isEmpty"))return material==Material.AIR;
                                        if(m.getName().equals("toString"))return "FakeBlock";
                                        throw new UnsupportedOperationException(m.getName());
                                    });
                        case "equals":return proxy==args[0];
                        case "hashCode":return System.identityHashCode(proxy);
                        case "toString":return "FakeWorld";
                        default:throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    @Test public void twoHundredVehiclesShareDecodedShapesPerTick(){
        FakeWorld world=new FakeWorld();
        VehicleTerrain.Frame frame=new VehicleTerrain.Frame();
        for(int vehicle=0;vehicle<200;vehicle++){
            VehicleTerrain.Space space=frame.space(world.world);
            // Roughly four wheels sampling repeated ground blocks.
            assertTrue(space.block(12,63,-7).isEmpty());
            assertTrue(space.block(13,63,-7).isEmpty());
            assertTrue(space.block(12,63,-8).isEmpty());
            assertTrue(space.block(13,63,-8).isEmpty());
        }
        assertEquals(1,frame.worldCount());
        assertEquals("four decoded blocks, not 800",4,frame.decodedBlocks());
        assertEquals(796,frame.reusedBlocks());
        assertEquals(4,world.shapeLookups.get());
        assertEquals("all probes share one loaded chunk check",1,world.loadedChecks.get());
    }

    @Test public void aNewTickSeesNewBlockValues(){
        FakeWorld world=new FakeWorld();
        VehicleTerrain.Frame initial=new VehicleTerrain.Frame();
        initial.space(world.world).block(5,60,7);
        assertEquals(1,world.shapeLookups.get());
        world.material=Material.STONE;
        VehicleTerrain.Frame following=new VehicleTerrain.Frame();
        assertFalse(following.space(world.world).block(5,60,7).isEmpty());
        assertEquals(2,world.shapeLookups.get());
    }

    @Test public void unloadedChunksNeverReadBlocks(){
        FakeWorld world=new FakeWorld();
        world.loaded=false;
        VehicleTerrain.Frame frame=new VehicleTerrain.Frame();
        VehicleTerrain.Space space=frame.space(world.world);
        assertFalse(space.loaded(0,0));
        assertTrue(space.block(0,64,0).isEmpty());
        assertEquals(0,world.shapeLookups.get());
        assertEquals(1,world.loadedChecks.get());
        // Next tick a newly loaded chunk must become visible.
        world.loaded=true;
        assertTrue(new VehicleTerrain.Frame().space(world.world).loaded(0,0));
    }

    @Test public void worldsDoNotShareBlocksOrChunkState(){
        FakeWorld a=new FakeWorld(),b=new FakeWorld();
        VehicleTerrain.Frame frame=new VehicleTerrain.Frame();
        assertNotSame(frame.space(a.world),frame.space(b.world));
        frame.space(a.world).block(0,64,0);
        frame.space(b.world).block(0,64,0);
        assertEquals(2,frame.worldCount());
        assertEquals(2,frame.decodedBlocks());
        assertEquals(1,a.shapeLookups.get());
        assertEquals(1,b.shapeLookups.get());
    }

    @Test public void negativeAndLargeCoordinatesNeverAlias(){
        FakeWorld world=new FakeWorld();
        VehicleTerrain.Space space=new VehicleTerrain.Frame().space(world.world);
        space.block(-1,-64,-1);
        space.block(-1,-64,-2);
        space.block(65535,320,65535);
        space.block(-65535,320,-65535);
        assertEquals(4,world.shapeLookups.get());
        assertEquals(3,world.loadedChecks.get());
    }
}
