package org.voxelhorizons.vehicles;

import org.bukkit.Chunk;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class VehicleOrphanSweepTest {
    private Chunk chunk(int x){
        return (Chunk)Proxy.newProxyInstance(Chunk.class.getClassLoader(),
                new Class<?>[]{Chunk.class},(proxy,method,args)->{
                    if(method.getName().equals("getX"))return x;
                    if(method.getName().equals("hashCode"))return x;
                    if(method.getName().equals("equals"))return proxy==args[0];
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test public void hundredsOfChunksNeverExceedPerTickBudget(){
        VehicleOrphanSweep sweep=new VehicleOrphanSweep();
        List<Chunk> chunks=new ArrayList<Chunk>();
        for(int i=0;i<1000;i++)chunks.add(chunk(i));
        assertTrue(sweep.schedule(chunks));
        AtomicInteger processed=new AtomicInteger();
        for(int tick=0;tick<62;tick++){
            int count=sweep.drain(16,c->{processed.incrementAndGet();});
            assertEquals(16,count);
        }
        assertEquals(8,sweep.drain(16,c->{processed.incrementAndGet();}));
        assertEquals(1000,processed.get());
        assertEquals(0,sweep.remaining());
        assertEquals(1,sweep.cyclesCompleted());
    }

    @Test public void inProgressSweepIsNotDiscardedByAnotherSchedule(){
        VehicleOrphanSweep sweep=new VehicleOrphanSweep();
        List<Chunk> a=new ArrayList<Chunk>();
        for(int i=0;i<40;i++)a.add(chunk(i));
        assertTrue(sweep.schedule(a));
        assertEquals(10,sweep.drain(10,c->{}));
        assertFalse(sweep.schedule(java.util.Collections.singletonList(chunk(99))));
        assertEquals(30,sweep.remaining());
        assertEquals(30,sweep.drain(30,c->{}));
        assertTrue(sweep.schedule(java.util.Collections.singletonList(chunk(99))));
        assertEquals(1,sweep.drain(10,c->{}));
        assertEquals(2,sweep.cyclesCompleted());
    }

    @Test public void emptySweepCanBeSafelyScheduledAgain(){
        VehicleOrphanSweep sweep=new VehicleOrphanSweep();
        assertTrue(sweep.schedule(java.util.Collections.<Chunk>emptyList()));
        assertEquals(0,sweep.drain(5,c->{}));
        assertEquals(0,sweep.cyclesCompleted());
        assertTrue(sweep.schedule(java.util.Collections.<Chunk>emptyList()));
    }
}
