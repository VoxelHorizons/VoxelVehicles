package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public final class BedrockVehicleSnapshotTest {
    private static World world(UUID uuid) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},
            (proxy,method,args) -> {
                if("getUID".equals(method.getName()))return uuid;
                if("getName".equals(method.getName()))return "realms";
                if("toString".equals(method.getName()))return "realms";
                if("hashCode".equals(method.getName()))return uuid.hashCode();
                if("equals".equals(method.getName()))return proxy==args[0];
                throw new UnsupportedOperationException(method.getName());
            });
    }
    private static VehicleDefinition tractor(){
        ContentID id=ContentID.of("voxel","tractor");
        Map<String,Object> properties=new HashMap<String,Object>();
        Map<String,Object> vehicle=new HashMap<String,Object>();
        vehicle.put("type","LAND");
        vehicle.put("model_item","tractor_model");
        vehicle.put("scale",1.25);
        properties.put("vehicle",vehicle);
        ItemDefinition item=new ItemDefinition(id,null,"PAPER","Tractor",
                Collections.<String>emptyList(),true,Optional.<ContentID>empty(),null,properties);
        return VehicleDefinition.parse(item).get();
    }

    @Test public void exportsStableRenderStateButNotCargoOrInventory() {
        VehicleDefinition def=tractor();
        UUID worldId=UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID id=UUID.fromString("00000000-0000-0000-0000-000000000012");
        VehicleInstance vehicle=new VehicleInstance(id,def.id,UUID.randomUUID(),
                new Location(world(worldId),5,70,-12,30f,0f),Integer.valueOf(0x123456));
        vehicle.motion.yaw=135f;
        vehicle.motion.speed=0.4;
        vehicle.visualPitch=12;
        vehicle.visualRoll=-4;
        Map<ContentID,VehicleDefinition> definitions=Collections.singletonMap(def.id,def);
        BedrockVehicleSnapshot snapshot=BedrockVehicleSnapshot.capture(7L,Arrays.asList(vehicle),definitions);
        assertEquals(1,snapshot.asMap().get("schema"));
        assertEquals(7L,snapshot.asMap().get("content_revision"));
        Map<?,?> item=snapshot.vehicles().get(0);
        assertEquals(id.toString(),item.get("instance_uuid"));
        assertEquals("voxel:tractor_model",item.get("model_item"));
        assertEquals("LAND",item.get("vehicle_type"));
        assertEquals(worldId.toString(),item.get("world_uuid"));
        assertEquals(Integer.valueOf(0x123456),item.get("dye_rgb"));
        assertEquals(135f,((Number)item.get("yaw")).floatValue(),0.0001f);
        assertEquals(12.0,((Number)item.get("pitch")).doubleValue(),0.0001);
        assertFalse(item.containsKey("inventory_contents"));
        assertFalse(item.containsKey("owner"));
        assertFalse(item.containsKey("physics_input"));
        try { snapshot.vehicles().clear();fail("Snapshot list must be immutable");}
        catch(UnsupportedOperationException expected) {}
        try { snapshot.vehicles().get(0).clear();fail("Snapshot row must be immutable");}
        catch(UnsupportedOperationException expected) {}
    }

    @Test public void unknownVehicleDefinitionsAreReported() {
        ContentID unknown=ContentID.of("voxel","missing_vehicle");
        VehicleInstance vehicle=new VehicleInstance(UUID.randomUUID(),unknown,UUID.randomUUID(),
                new Location(world(UUID.randomUUID()),0,0,0));
        try {
            BedrockVehicleSnapshot.capture(1L,Collections.singletonList(vehicle),Collections.emptyMap());
            fail("Unmapped vehicle must be diagnosed");
        } catch(IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing vehicle definition"));
        }
    }
}
