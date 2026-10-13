package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import java.util.*;
import static org.junit.Assert.*;

public class VehicleParticlesTest {
    private final ContentID tractor=ContentID.of("voxel","tractor");

    private Map<String,Object> emitter(String when) {
        Map<String,Object> item=new HashMap<String,Object>();
        item.put("particle","SMOKE");
        item.put("when",when);
        item.put("count",2);
        item.put("interval_ticks",4);
        Map<String,Object> offset=new HashMap<String,Object>();
        offset.put("x",0.5);
        offset.put("y",1.0);
        offset.put("z",1.5);
        item.put("offset",offset);
        return item;
    }

    private VehicleDefinition definition(){
        Map<String,Object> offsets=new HashMap<String,Object>();
        offsets.put("x",1.0);offsets.put("y",1.5);
        Map<String,Object> data=new HashMap<String,Object>();
        data.put("offset",offsets);
        data.put("particles",Collections.singletonList(emitter("OCCUPIED")));
        Map<String,Object> props=new HashMap<String,Object>();
        props.put("vehicle",data);
        ItemDefinition item=new ItemDefinition(tractor,null,"PAPER","Tractor",
                Collections.<String>emptyList(),true,Optional.<ContentID>empty(),
                null,props);
        return VehicleDefinition.parse(item).get();
    }

    @Test public void parsesAndInheritsParticleDefinitions(){
        VehicleDefinition d=definition();
        assertEquals(1,d.particles.size());
        assertEquals("SMOKE",d.particles.get(0).name);
        assertEquals(2,d.particles.get(0).count);
        assertEquals(4,d.particles.get(0).intervalTicks);
    }

    @Test public void emitterFollowsVehicleYawAndVisualOffset(){
        VehicleDefinition d=definition();
        VehicleInstance v=new VehicleInstance(UUID.randomUUID(),tractor,
                UUID.randomUUID(),new Location(null,10,64,20,0,0));
        Location a=VehicleParticles.position(v,d,d.particles.get(0));
        assertEquals(11.5,a.getX(),1e-6);
        assertEquals(66.5,a.getY(),1e-6);
        assertEquals(21.5,a.getZ(),1e-6);
        v.location.setYaw(90);
        Location b=VehicleParticles.position(v,d,d.particles.get(0));
        assertEquals(8.5,b.getX(),1e-6);
        assertEquals(66.5,b.getY(),1e-6);
        assertEquals(21.5,b.getZ(),1e-6);
    }

    @Test public void followsPitchButDoesNotScaleOffsetsAgain(){
        VehicleDefinition d=definition();
        VehicleInstance v=new VehicleInstance(UUID.randomUUID(),tractor,
                UUID.randomUUID(),new Location(null,0,64,0));
        v.visualPitch=30;
        Location effect=VehicleParticles.position(v,d,d.particles.get(0));
        double y=1.0*Math.cos(Math.toRadians(30))+1.5*Math.sin(Math.toRadians(30));
        assertEquals(65.5+y,effect.getY(),1e-6);
    }

    @Test public void conditionsSupportIdleMovingAndParked(){
        VehicleParticleEmitter idle=VehicleParticleEmitter.parse(tractor,
                Collections.singletonList(emitter("IDLE"))).get(0);
        assertTrue(idle.shouldEmit(true,0));
        assertFalse(idle.shouldEmit(false,0));
        assertFalse(idle.shouldEmit(true,0.1));
        VehicleParticleEmitter moving=VehicleParticleEmitter.parse(tractor,
                Collections.singletonList(emitter("MOVING"))).get(0);
        assertTrue(moving.shouldEmit(true,0.1));
        assertFalse(moving.shouldEmit(true,0));
        VehicleParticleEmitter always=VehicleParticleEmitter.parse(tractor,
                Collections.singletonList(emitter("ALWAYS"))).get(0);
        assertTrue(always.shouldEmit(false,0));
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectsUnsafeParticleCounts(){
        Map<String,Object> bad=emitter("OCCUPIED");bad.put("count",10000);
        VehicleParticleEmitter.parse(tractor,Collections.singletonList(bad));
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectsInvalidNestedKeys(){
        Map<String,Object> bad=emitter("OCCUPIED");bad.put("unknown",true);
        VehicleParticleEmitter.parse(tractor,Collections.singletonList(bad));
    }

    @Test public void emptyEmitterListPreservesExistingVehicleItems(){
        assertTrue(VehicleParticleEmitter.parse(tractor,null).isEmpty());
        assertTrue(VehicleParticleEmitter.parse(tractor,Collections.emptyList()).isEmpty());
    }
}
