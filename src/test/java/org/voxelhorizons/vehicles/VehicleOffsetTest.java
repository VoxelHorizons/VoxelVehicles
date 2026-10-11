package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import java.util.*;
import static org.junit.Assert.*;

/**
 * Regression coverage for model/clickbox/physics separation and stable render
 * roots: a tractor must not jump from one entity origin to another on mount.
 */
public class VehicleOffsetTest {
    private static VehicleDefinition tractor() {
        Map<String,Object> global=new HashMap<String,Object>();
        global.put("x",1.0);global.put("y",8.0);global.put("z",2.0);
        Map<String,Object> clickOffset=new HashMap<String,Object>();
        clickOffset.put("x",0.2);clickOffset.put("y",-8.0);clickOffset.put("z",0.3);
        Map<String,Object> hitbox=new HashMap<String,Object>();
        hitbox.put("width",3.0);hitbox.put("height",4.0);hitbox.put("offset",clickOffset);
        Map<String,Object> seat=new HashMap<String,Object>();
        seat.put("x",0.25);seat.put("y",-0.7);seat.put("z",0.4);
        Map<String,Object> vehicle=new HashMap<String,Object>();
        vehicle.put("offset",global);vehicle.put("hitbox",hitbox);vehicle.put("seat",seat);
        Map<String,Object> properties=new HashMap<String,Object>();
        properties.put("vehicle",vehicle);
        ItemDefinition item=new ItemDefinition(ContentID.of("voxel","tractor"),
                null,"PAPER","Tractor",Collections.<String>emptyList(),true,
                Optional.<ContentID>empty(),null,properties);
        return VehicleDefinition.parse(item).get();
    }
    @Test public void collisionIgnoresBothVisualAndInteractionOffsets(){
        VehicleDefinition d=tractor();
        Location origin=new Location(null,30,64,20,0,0);
        assertEquals(64,VehicleTransform.collisionBase(origin,d).getY(),0.00001);
        assertEquals(72,VehicleTransform.visual(origin,d).getY(),0.00001);
        assertEquals(66,VehicleTransform.hitbox(origin,d).getY(),0.00001);
        assertEquals(1.0,d.collisionWidth,0.00001);
        assertEquals(1.5,d.collisionHeight,0.00001);
    }
    @Test public void modelIsAtSameWorldPositionBeforeAndAfterMount(){
        VehicleDefinition d=tractor();
        Location origin=new Location(null,30,64,20,90,0);
        Location visual=VehicleTransform.visual(origin,d);
        Location root=VehicleTransform.renderRoot(origin,d);
        double[] translation=VehicleTransform.modelTranslation(d);
        Location recovered=VehicleTransform.local(root,root.getYaw(),
                translation[0],translation[1],translation[2]);
        assertEquals(visual.getX(),recovered.getX(),0.000001);
        assertEquals(visual.getY(),recovered.getY(),0.000001);
        assertEquals(visual.getZ(),recovered.getZ(),0.000001);
        assertEquals(root,VehicleTransform.renderRoot(origin,d));
    }
    @Test public void collisionConfigurationCanBeAdjustedSeparately(){
        VehicleDefinition d=tractor();
        assertEquals(3,d.width,0.000001);
        assertEquals(4,d.height,0.000001);
        assertEquals(1,d.collisionWidth,0.000001);
        assertEquals(1.5,d.collisionHeight,0.000001);
        VehicleCollision.Body body=new VehicleCollision.Body(
                new Location(null,30,64,20,0,0),d);
        assertEquals(29.5,body.x0,0.000001);
        assertEquals(30.5,body.x1,0.000001);
        assertEquals(64,body.y0,0.000001);
        assertEquals(65.5,body.y1,0.000001);
    }
}
