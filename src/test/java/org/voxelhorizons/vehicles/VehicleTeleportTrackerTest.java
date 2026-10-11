package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Tests the actual Bukkit teleport gate without a running Minecraft server. */
public class VehicleTeleportTrackerTest {
    private static final class FakeEntity {
        final UUID id=UUID.randomUUID();
        final AtomicInteger teleports=new AtomicInteger();
        final AtomicBoolean accepts=new AtomicBoolean(true);
        Entity entity(){
            return (Entity)Proxy.newProxyInstance(
                    Entity.class.getClassLoader(),new Class<?>[]{Entity.class},
                    (proxy,method,args)->{
                        switch(method.getName()){
                            case "getUniqueId":return id;
                            case "isValid":return true;
                            case "teleport":
                                teleports.incrementAndGet();
                                return accepts.get();
                            case "equals":return proxy==args[0];
                            case "hashCode":return System.identityHashCode(proxy);
                            case "toString":return "FakeEntity("+id+")";
                            default:throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }
    private static Location at(double x,double y,double z,float yaw){
        return new Location(null,x,y,z,yaw,0);
    }

    @Test public void newlySpawnedVehicleDisplaySkipsIdenticalPosition(){
        VehicleTeleportTracker state=new VehicleTeleportTracker();
        FakeEntity mock=new FakeEntity();
        Entity display=mock.entity();
        Location start=at(10,70,20,90);
        state.spawned(display,start);
        assertTrue(state.ensure(display,start.clone(),true));
        assertEquals(0,mock.teleports.get());
        assertTrue(state.ensure(display,at(10.5,70,20,90),true));
        assertEquals(1,mock.teleports.get());
        assertTrue(state.ensure(display,at(10.5,70,20,90),true));
        assertEquals(1,mock.teleports.get());
    }

    @Test public void pureTurningRotatesVehicleDisplayButNotCenteredInteraction(){
        VehicleTeleportTracker state=new VehicleTeleportTracker();
        FakeEntity visual=new FakeEntity(),hitbox=new FakeEntity();
        Entity model=visual.entity(),interaction=hitbox.entity();
        Location start=at(4,65,-5,0);
        state.spawned(model,start);
        state.spawned(interaction,start);
        Location turned=at(4,65,-5,180);
        assertTrue(state.ensure(model,turned,true));
        assertTrue(state.ensure(interaction,turned,false));
        assertEquals(1,visual.teleports.get());
        assertEquals("axis-aligned interaction yaw never needs a teleport",0,hitbox.teleports.get());
    }

    @Test public void movingChestRootAndClickboxEachTeleportOnlyOnce(){
        VehicleTeleportTracker state=new VehicleTeleportTracker();
        FakeEntity root=new FakeEntity(),chest=new FakeEntity(),hitbox=new FakeEntity();
        Entity tractor=root.entity(),chestDisplay=chest.entity(),chestInteraction=hitbox.entity();
        Location start=at(0,64,0,90);
        state.spawned(tractor,start);
        state.spawned(chestDisplay,start);
        state.spawned(chestInteraction,at(1,65,0,90));
        Location target=at(0.25,64,0,90);
        Location targetHitbox=at(1.25,65,0,90);
        assertTrue(state.ensure(tractor,target,true));
        assertTrue(state.ensure(chestDisplay,target,true));
        assertTrue(state.ensure(chestInteraction,targetHitbox,false));
        // On the same tick the suspension callback should only update the
        // ItemDisplay transformation, not re-teleport its root.
        assertTrue(state.ensure(chestDisplay,target,true));
        assertTrue(state.ensure(chestInteraction,targetHitbox,false));
        assertEquals(1,root.teleports.get());
        assertEquals(1,chest.teleports.get());
        assertEquals(1,hitbox.teleports.get());
    }

    @Test public void realTiltChangeMovesTheChestClickboxButNotDisplayRoot(){
        VehicleTeleportTracker state=new VehicleTeleportTracker();
        FakeEntity chest=new FakeEntity(),hitbox=new FakeEntity();
        Entity display=chest.entity(),interaction=hitbox.entity();
        state.spawned(display,at(0,64,0,90));
        state.spawned(interaction,at(1,65,0,90));
        assertTrue(state.ensure(display,at(0,64,0,90),true));
        assertTrue(state.ensure(interaction,at(1,65.2,0,90),false));
        assertEquals(0,chest.teleports.get());
        assertEquals(1,hitbox.teleports.get());
    }

    @Test public void failedTeleportIsRetriedWithoutPoisoningPoseCache(){
        VehicleTeleportTracker state=new VehicleTeleportTracker();
        FakeEntity mock=new FakeEntity();
        Entity entity=mock.entity();
        state.spawned(entity,at(0,64,0,0));
        mock.accepts.set(false);
        assertFalse(state.ensure(entity,at(1,64,0,0),true));
        assertEquals(1,mock.teleports.get());
        mock.accepts.set(true);
        assertTrue(state.ensure(entity,at(1,64,0,0),true));
        assertEquals(2,mock.teleports.get());
        assertTrue(state.ensure(entity,at(1,64,0,0),true));
        assertEquals(2,mock.teleports.get());
    }

    @Test public void entityRespawnAndDeactivationInvalidateTheCache(){
        VehicleTeleportTracker state=new VehicleTeleportTracker();
        Location start=at(0,64,0,90);
        FakeEntity old=new FakeEntity(),replacement=new FakeEntity();
        Entity oldEntity=old.entity(),newEntity=replacement.entity();
        state.spawned(oldEntity,start);
        state.forget(oldEntity);
        assertTrue(state.ensure(newEntity,start,true));
        assertEquals(1,replacement.teleports.get());
        state.clear();
        assertTrue(state.ensure(newEntity,start,true));
        assertEquals(2,replacement.teleports.get());
    }

    @Test public void exactMovementNeverDropsSubPixelOrYawChanges(){
        Location start=at(0,64,0,0);
        assertFalse(VehicleTeleportTracker.changed(start,start.clone(),true));
        assertTrue(VehicleTeleportTracker.changed(start,at(1e-7,64,0,0),false));
        assertTrue(VehicleTeleportTracker.changed(start,at(0,64,0,0.001f),true));
        assertFalse(VehicleTeleportTracker.changed(start,at(0,64,0,0.001f),false));
    }
}
