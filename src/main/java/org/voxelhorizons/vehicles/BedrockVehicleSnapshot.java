package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.voxelhorizons.content.ContentID;

import java.util.*;

/**
 * Immutable render-only description of the authoritative Paper vehicle state.
 * No inventory payload, control input mutation, economy or ownership enforcement
 * is replicated here. Future Geyser adapters can use it for visual projection.
 */
public final class BedrockVehicleSnapshot {
    public static final int SCHEMA = 1;
    private final long contentRevision;
    private final List<Map<String,Object>> vehicles;

    private BedrockVehicleSnapshot(long revision,List<Map<String,Object>> vehicles) {
        this.contentRevision=revision;
        this.vehicles=Collections.unmodifiableList(vehicles);
    }
    public long contentRevision(){return contentRevision;}
    public List<Map<String,Object>> vehicles(){return vehicles;}

    public Map<String,Object> asMap(){
        Map<String,Object> result=new LinkedHashMap<String,Object>();
        result.put("schema",Integer.valueOf(SCHEMA));
        result.put("content_revision",Long.valueOf(contentRevision));
        result.put("vehicles",vehicles);
        return Collections.unmodifiableMap(result);
    }

    public static BedrockVehicleSnapshot capture(long revision,Collection<VehicleInstance> instances,
                                                  Map<ContentID,VehicleDefinition> definitions){
        if(instances==null||definitions==null)
            throw new IllegalArgumentException("Vehicle snapshot inputs must not be null");
        List<VehicleInstance> sorted=new ArrayList<VehicleInstance>(instances);
        Collections.sort(sorted,(a,b)->a.id.toString().compareTo(b.id.toString()));
        List<Map<String,Object>> values=new ArrayList<Map<String,Object>>();
        for(VehicleInstance vehicle:sorted){
            VehicleDefinition definition=definitions.get(vehicle.type);
            if(definition==null)
                throw new IllegalArgumentException("Missing vehicle definition "+vehicle.type);
            Location location=vehicle.location.clone();
            if(location.getWorld()==null)throw new IllegalArgumentException("Vehicle has no world "+vehicle.id);
            Map<String,Object> state=new LinkedHashMap<String,Object>();
            state.put("instance_uuid",vehicle.id.toString());
            state.put("world_uuid",location.getWorld().getUID().toString());
            state.put("world_name",location.getWorld().getName());
            state.put("definition",vehicle.type.toString());
            state.put("model_item",definition.model.toString());
            state.put("vehicle_type",definition.type.name());
            state.put("position",xyz(location.getX(),location.getY(),location.getZ()));
            state.put("yaw",Float.valueOf(vehicle.motion.yaw));
            state.put("pitch",Double.valueOf(vehicle.visualPitch));
            state.put("roll",Double.valueOf(vehicle.visualRoll));
            state.put("speed",Double.valueOf(vehicle.motion.speed));
            state.put("grounded",Boolean.valueOf(vehicle.motion.grounded));
            state.put("dye_rgb",vehicle.dyeColor);
            state.put("scale",Double.valueOf(definition.scale));
            state.put("local_offset",xyz(definition.offsetX,definition.offsetY,definition.offsetZ));
            state.put("offset_rotation",Double.valueOf(definition.offsetRotation));
            state.put("view_distance",Float.valueOf(definition.viewDistance));
            state.put("hitbox",xyz(definition.width,definition.height,0));
            state.put("hitbox_offset",xyz(definition.hitboxX,definition.hitboxY,definition.hitboxZ));
            state.put("seat_offset",xyz(definition.seatX,definition.seatY,definition.seatZ));
            // Chest accessory display model (ITEM_DISPLAY mode with a VoxelCore item only),
            // so the Geyser bridge can give the chest its own native entity.
            VehicleChestDefinition chest=definition.inventory==null?null:definition.inventory.chest;
            boolean chestModel=chest!=null&&chest.enabled&&chest.mode==VehicleChestDefinition.VisualMode.ITEM_DISPLAY
                    &&chest.visualItem!=null;
            state.put("chest_model_item",chestModel?chest.visualItem.toString():null);
            state.put("chest_scale",chestModel?Double.valueOf(chest.scale):null);
            // Paper alone handles collision, inventories and rider permissions.
            Map<String,Object> linked=new LinkedHashMap<String,Object>();
            linked.put("visual",id(vehicle.display));
            linked.put("hitbox",id(vehicle.hitbox));
            linked.put("seat",id(vehicle.seat));
            linked.put("chest_display",id(vehicle.chestDisplay));
            linked.put("chest_hitbox",id(vehicle.chestHitbox));
            linked.put("rider_uuid",vehicle.driver==null?null:vehicle.driver.toString());
            state.put("entity_links",Collections.unmodifiableMap(linked));
            values.add(Collections.unmodifiableMap(state));
        }
        return new BedrockVehicleSnapshot(revision,values);
    }
    private static String id(Entity entity){return entity==null?null:entity.getUniqueId().toString();}
    private static List<Double> xyz(double a,double b,double c){
        return Collections.unmodifiableList(Arrays.asList(Double.valueOf(a),Double.valueOf(b),Double.valueOf(c)));
    }
}
