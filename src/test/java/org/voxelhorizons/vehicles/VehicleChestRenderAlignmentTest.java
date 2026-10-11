package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

/** Prevent accessory displays taking a separately rotated world-space path. */
public class VehicleChestRenderAlignmentTest {
    private VehicleDefinition definition(boolean tilt){
        ContentID id=ContentID.of("voxel","tractor");
        Map<String,Object> seat=vector(0.3,-0.4,0.5);
        Map<String,Object> offset=vector(-0.4,1.5,0.8);
        offset.put("rotation",0);
        Map<String,Object> chestVisual=new HashMap<String,Object>();
        chestVisual.put("mode","ITEM_DISPLAY");
        chestVisual.put("offset",vector(0.7,1.1,-1.4));
        chestVisual.put("follow_tilt",tilt);
        Map<String,Object> chest=new HashMap<String,Object>();
        chest.put("enabled",true);
        chest.put("visual",chestVisual);
        Map<String,Object> inventory=new HashMap<String,Object>();
        inventory.put("rows",3);
        inventory.put("chest",chest);
        Map<String,Object> vehicle=new HashMap<String,Object>();
        vehicle.put("seat",seat);
        vehicle.put("offset",offset);
        vehicle.put("scale",6.0);
        vehicle.put("inventory",inventory);
        Map<String,Object> properties=new HashMap<String,Object>();
        properties.put("vehicle",vehicle);
        ItemDefinition item=new ItemDefinition(id,null,"PAPER","Tractor",
                Collections.<String>emptyList(),true,Optional.<ContentID>empty(),
                null,properties);
        return VehicleDefinition.parse(item).get();
    }
    private Map<String,Object> vector(double x,double y,double z){
        Map<String,Object> result=new HashMap<String,Object>();
        result.put("x",x);result.put("y",y);result.put("z",z);
        return result;
    }
    private VehicleInstance vehicle(float yaw){
        return new VehicleInstance(UUID.randomUUID(),ContentID.of("voxel","tractor"),
                UUID.randomUUID(),new Location(null,10,64,20,yaw,0));
    }

    @Test public void chestAndTractorShareExactlyTheSameRenderRootAtAllYawAngles(){
        VehicleDefinition d=definition(true);
        for(float yaw:new float[]{0,45,90,135,180,-90,-135}){
            VehicleInstance v=vehicle(yaw);
            v.visualPitch=15;
            v.visualRoll=-9;
            Location tractor=VehicleTransform.renderRoot(v.location,d);
            Location chest=VehicleChestRenderer.renderRoot(v,d);
            assertEquals(tractor.getX(),chest.getX(),1e-6);
            assertEquals(tractor.getY(),chest.getY(),1e-6);
            assertEquals(tractor.getZ(),chest.getZ(),1e-6);
            assertEquals(tractor.getYaw(),chest.getYaw(),1e-6);
        }
    }

    @Test public void accessoryOffsetRemainsAttachedAtRightAndLeftTurns(){
        VehicleDefinition d=definition(false);
        for(float yaw:new float[]{0,45,90,180,270,-90}){
            VehicleInstance v=vehicle(yaw);
            Location visual=VehicleTransform.visual(v.location,d);
            Location expected=VehicleTransform.local(visual,visual.getYaw(),
                    d.inventory.chest.x,d.inventory.chest.y,d.inventory.chest.z);
            Location hitboxCenter=VehicleChestRenderer.position(v,d);
            assertEquals(expected.getX(),hitboxCenter.getX(),1e-6);
            assertEquals(expected.getY(),hitboxCenter.getY(),1e-6);
            assertEquals(expected.getZ(),hitboxCenter.getZ(),1e-6);
        }
    }

    @Test public void tiltedChestHitboxRotatesAroundVisualOriginNotPassengerSeat(){
        VehicleDefinition d=definition(true);
        VehicleInstance v=vehicle(90);
        Location normal=VehicleChestRenderer.position(v,d);
        v.visualPitch=18;
        v.visualRoll=-12;
        Location tilted=VehicleChestRenderer.position(v,d);
        Location visual=VehicleTransform.visual(v.location,d);
        assertTrue(normal.distanceSquared(tilted)>1e-4);
        double expectedDistance=Math.pow(d.inventory.chest.x,2)
                +Math.pow(d.inventory.chest.y,2)
                +Math.pow(d.inventory.chest.z,2);
        assertEquals(expectedDistance,tilted.distanceSquared(visual),1e-6);
        // Tilt must not move the tractor/chest shared ItemDisplay ROOT.
        Location tractorRoot=VehicleTransform.renderRoot(v.location,d);
        assertEquals(0,tractorRoot.distanceSquared(VehicleChestRenderer.renderRoot(v,d)),1e-8);
    }

    @Test public void worldPositionChangesDoNotChangeLocalMountOffset(){
        VehicleDefinition d=definition(true);
        VehicleInstance v=vehicle(-40);
        Location oldRoot=VehicleChestRenderer.renderRoot(v,d);
        v.location.add(7,2,-3);
        Location newRoot=VehicleChestRenderer.renderRoot(v,d);
        assertEquals(7,newRoot.getX()-oldRoot.getX(),1e-6);
        assertEquals(2,newRoot.getY()-oldRoot.getY(),1e-6);
        assertEquals(-3,newRoot.getZ()-oldRoot.getZ(),1e-6);
    }
}
