package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.World;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Uses the existing main-thread vehicle tick. No extra entities, tasks or
 * stored particles. Only active vehicles with configured emitters are touched.
 */
final class VehicleParticles {
    private final VoxelVehicles plugin;
    private final Set<String> reported=new HashSet<String>();

    VehicleParticles(VoxelVehicles plugin){this.plugin=plugin;}

    void tick(Collection<VehicleInstance> instances,Map<org.voxelhorizons.content.ContentID,
            VehicleDefinition> definitions,long ticks){
        for(VehicleInstance vehicle:instances){
            if(!vehicle.active())continue; // never load a chunk or emit from offline vehicles
            VehicleDefinition definition=definitions.get(vehicle.type);
            if(definition==null||definition.particles.isEmpty())continue;
            World world=vehicle.location.getWorld();
            if(world==null)continue;
            boolean occupied=vehicle.driver!=null;
            for(VehicleParticleEmitter emitter:definition.particles){
                if(!emitter.shouldEmit(occupied,vehicle.motion.speed))continue;
                // Distribute large groups of vehicles across tick slots, rather
                // than producing synchronized particle bursts.
                long phase=Math.floorMod(vehicle.id.hashCode(),emitter.intervalTicks);
                if((ticks+phase)%emitter.intervalTicks!=0)continue;
                Location at=position(vehicle,definition,emitter);
                if(!world.isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))continue;
                try{
                    world.spawnParticle(emitter.particle,at,emitter.count,
                            emitter.spreadX,emitter.spreadY,emitter.spreadZ,emitter.speed);
                }catch(RuntimeException e){
                    // A provider/particle incompatibility must not stop the
                    // shared vehicle physics task or spam logs every tick.
                    String failure=definition.id+"/"+emitter.name;
                    if(reported.add(failure))
                        plugin.getLogger().warning("Cannot emit "+failure+": "+e.getMessage());
                }
            }
        }
    }

    static Location position(VehicleInstance vehicle,VehicleDefinition definition,
            VehicleParticleEmitter emitter){
        Location model=VehicleTransform.visual(vehicle.location,definition);
        double x=emitter.x,y=emitter.y,z=emitter.z;
        if(emitter.followTilt){
            // ItemDisplay transformation: R_x(-pitch) * R_z(roll) * scale.
            // Offsets are already in WORLD BLOCKS, so do not scale them again.
            double roll=Math.toRadians(vehicle.visualRoll);
            double c=Math.cos(roll),s=Math.sin(roll);
            double rx=x*c-y*s, ry=x*s+y*c;
            double pitch=Math.toRadians(vehicle.visualPitch);
            double cp=Math.cos(pitch),sp=Math.sin(pitch);
            x=rx;y=ry*cp+z*sp;z=-ry*sp+z*cp;
        }
        return VehicleTransform.local(model,model.getYaw(),x,y,z);
    }
}
