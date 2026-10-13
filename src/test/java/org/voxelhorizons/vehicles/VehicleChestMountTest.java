package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** Verify passenger attachment without a live Paper world. */
public class VehicleChestMountTest {
    private static final class MockEntity {
        final UUID id=UUID.randomUUID();
        final List<Entity> passengers=new ArrayList<Entity>();
        final boolean player;
        Entity vehicle;
        boolean valid=true,accepts=true;
        int addCalls,removeCalls,teleports,ejectCalls;
        final Entity entity;

        MockEntity(boolean player){
            this.player=player;
            entity=(Entity)Proxy.newProxyInstance(Entity.class.getClassLoader(),
                    new Class<?>[]{player?Player.class:Entity.class},
                    (proxy,method,args)->{
                        switch(method.getName()){
                            case "getUniqueId":return id;
                            case "isValid":return valid;
                            case "getVehicle":return vehicle;
                            case "getPassengers":return new ArrayList<Entity>(passengers);
                            case "addPassenger":
                                addCalls++;
                                if(!accepts)return false;
                                Entity child=(Entity)args[0];
                                passengers.add(child);
                                // In this harness, the player and display proxies
                                // are the only entities we create.
                                if(child==accessory.entity)accessory.vehicle=(Entity)proxy;
                                if(child==rider.entity)rider.vehicle=(Entity)proxy;
                                return true;
                            case "removePassenger":
                                removeCalls++;
                                boolean removed=passengers.remove(args[0]);
                                if(args[0]==accessory.entity)accessory.vehicle=null;
                                if(args[0]==rider.entity)rider.vehicle=null;
                                return removed;
                            case "eject":ejectCalls++;passengers.clear();return true;
                            case "teleport":teleports++;return true;
                            case "equals":return proxy==args[0];
                            case "hashCode":return System.identityHashCode(proxy);
                            case "toString":return "MockEntity("+id+")";
                            default:throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
        // Test fixture assigns these before any passenger API is used.
        MockEntity accessory,rider;
    }

    private static final class Fixture {
        final MockEntity main=new MockEntity(false);
        final MockEntity chest=new MockEntity(false);
        final MockEntity driver=new MockEntity(true);
        final VehicleInstance vehicle=new VehicleInstance(UUID.randomUUID(),
                ContentID.of("voxel","tractor"),UUID.randomUUID(),
                new Location(null,10,70,20,90,0));
        Fixture(){
            for(MockEntity mock:new MockEntity[]{main,chest,driver}){
                mock.accessory=chest;
                mock.rider=driver;
            }
            vehicle.display=main.entity;
            vehicle.chestDisplay=chest.entity;
        }
    }

    @Test public void attachedChestUsesOneParentAndSkipsRepeatedAttachmentRequests(){
        Fixture f=new Fixture();
        assertTrue(VehicleChestMount.ensure(f.vehicle,true));
        assertTrue(VehicleChestMount.attached(f.vehicle));
        for(int tick=0;tick<1000;tick++)assertTrue(VehicleChestMount.ensure(f.vehicle,true));
        assertEquals(1,f.main.addCalls);
        assertSame(f.main.entity,f.chest.vehicle);
    }

    @Test public void unseatingDriverDoesNotEjectTheAttachedChest(){
        Fixture f=new Fixture();
        assertTrue(VehicleChestMount.ensure(f.vehicle,true));
        assertTrue(f.main.entity.addPassenger(f.driver.entity));
        assertTrue(VehicleRenderer.unseatDriver(f.main.entity,f.driver.id));
        assertNull(f.driver.vehicle);
        assertEquals(0,f.main.ejectCalls);
        assertTrue(VehicleChestMount.attached(f.vehicle));
        assertEquals(1,f.main.passengers.size());
        assertSame(f.chest.entity,f.main.passengers.get(0));
    }

    @Test public void failedAttachmentDoesNotRetryEveryTickOrBlockDriver(){
        Fixture f=new Fixture();
        f.main.accepts=false;
        assertFalse(VehicleChestMount.ensure(f.vehicle,true));
        for(int tick=0;tick<100;tick++)
            assertFalse(VehicleChestMount.ensure(f.vehicle,true));
        assertEquals(1,f.main.addCalls);
        VehicleChestMount.release(f.vehicle);
        f.main.accepts=true;
        assertTrue(f.main.entity.addPassenger(f.driver.entity));
        assertTrue(VehicleChestMount.ensure(f.vehicle,true));
        assertEquals(3,f.main.addCalls);
        assertTrue(VehicleChestMount.attached(f.vehicle));
    }

    @Test public void contentRefreshOrRemountRetriesPreviouslyRejectedAttachment(){
        Fixture f=new Fixture();
        f.main.accepts=false;
        assertFalse(VehicleChestMount.ensure(f.vehicle,true));
        assertFalse(VehicleChestMount.ensure(f.vehicle,true));
        assertEquals(1,f.main.addCalls);
        // Retry remains gated by an explicit lifecycle transition rather
        // than repeatedly attempting mounts during normal vehicle ticks.
        VehicleChestMount.allowRetry(f.vehicle);
        f.main.accepts=true;
        assertTrue(VehicleChestMount.ensure(f.vehicle,true));
        assertEquals(2,f.main.addCalls);
    }

    @Test public void unexpectedPassengerDetachGetsOneRecoveryAttempt(){
        Fixture f=new Fixture();
        assertTrue(VehicleChestMount.ensure(f.vehicle,true));
        assertTrue(f.main.entity.removePassenger(f.chest.entity));
        assertFalse(VehicleChestMount.attached(f.vehicle));
        assertTrue(VehicleChestMount.ensure(f.vehicle,true));
        assertSame(f.main.entity,f.chest.vehicle);
        assertEquals(2,f.main.addCalls);
    }

    @Test public void compatibilityModeUnlinksAccessoryAndPreservesFallback(){
        Fixture f=new Fixture();
        assertTrue(VehicleChestMount.ensure(f.vehicle,true));
        assertFalse(VehicleChestMount.ensure(f.vehicle,false));
        assertNull(f.chest.vehicle);
        assertFalse(f.vehicle.chestPassengerLinked);
        assertFalse(VehicleChestMount.ensure(f.vehicle,false));
        assertEquals("not repeatedly unlinked every frame",1,f.main.removeCalls);
        assertTrue(VehicleChestMount.ensure(f.vehicle,true));
        assertTrue(VehicleChestMount.attached(f.vehicle));
    }

    @Test public void accessoryRemovalDoesNotDismountActualDriver(){
        Fixture f=new Fixture();
        assertTrue(f.main.entity.addPassenger(f.driver.entity));
        assertTrue(VehicleChestMount.ensure(f.vehicle,true));
        VehicleChestMount.release(f.vehicle);
        assertSame(f.main.entity,f.driver.vehicle);
        assertEquals(1,f.main.passengers.size());
        assertSame(f.driver.entity,f.main.passengers.get(0));
    }
}
