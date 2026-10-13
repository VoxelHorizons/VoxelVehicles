package org.voxelhorizons.vehicles;

import org.bukkit.Location;

/**
 * Four-point terrain suspension. It tilts just the ItemDisplay model, never
 * the movement collision box or the passenger mount. No ticking entities are
 * added. Samples only block shapes in chunks that are already loaded.
 */
final class VehicleSuspension {
    private VehicleSuspension(){}

    static void initialize(VehicleInstance vehicle,VehicleDefinition definition){
        update(vehicle,definition,new VehicleTerrain.Space(vehicle.location.getWorld()),true);
    }

    static boolean update(VehicleInstance vehicle,VehicleDefinition definition,
            VehicleTerrain.Space space){
        return update(vehicle,definition,space,false);
    }

    private static boolean update(VehicleInstance vehicle,VehicleDefinition definition,
            VehicleTerrain.Space space,boolean immediate){
        double smoothing=immediate?1.0:definition.tiltSmoothing;
        if(definition.type!=VehicleDefinition.Type.LAND)return level(vehicle,smoothing);
        if(!immediate&&!vehicle.motion.grounded){
            // All four wheels have left the ground. Use the relationship
            // between horizontal momentum and downward velocity instead of
            // sampling terrain several blocks below the falling vehicle.
            double target=VehiclePhysics.airbornePitch(vehicle.motion.speed,
                    vehicle.motion.verticalSpeed,definition.maxPitch,
                    definition.airbornePitchFactor);
            return smooth(vehicle,target,0,smoothing);
        }
        Location base=VehicleTransform.collisionBase(vehicle.location,definition);
        double angle=Math.toRadians(base.getYaw());
        double forwardX=-Math.sin(angle), forwardZ=Math.cos(angle);
        double rightX=Math.cos(angle), rightZ=Math.sin(angle);
        double halfLength=definition.wheelbase*0.5,halfWidth=definition.trackWidth*0.5;
        double x=base.getX(),y=base.getY(),z=base.getZ();

        double fl=surface(space,x+forwardX*halfLength-rightX*halfWidth,
                z+forwardZ*halfLength-rightZ*halfWidth,y);
        double fr=surface(space,x+forwardX*halfLength+rightX*halfWidth,
                z+forwardZ*halfLength+rightZ*halfWidth,y);
        double rl=surface(space,x-forwardX*halfLength-rightX*halfWidth,
                z-forwardZ*halfLength-rightZ*halfWidth,y);
        double rr=surface(space,x-forwardX*halfLength+rightX*halfWidth,
                z-forwardZ*halfLength+rightZ*halfWidth,y);
        double front=average(fl,fr,y),rear=average(rl,rr,y);
        double left=average(fl,rl,y),right=average(fr,rr,y);
        double targetPitch=VehicleTerrain.inclination(front,rear,
                definition.wheelbase,definition.maxPitch);
        double targetRoll=VehicleTerrain.inclination(right,left,
                definition.trackWidth,definition.maxRoll);
        return smooth(vehicle,targetPitch,targetRoll,smoothing);
    }

    private static double surface(VehicleTerrain.Space space,double x,double z,double y){
        // Ignore ceilings and two-block walls; probe one block ahead and below.
        return space.surface(x,z,y,-1.3,1.02);
    }
    private static double average(double a,double b,double fallback){
        if(!Double.isFinite(a))return Double.isFinite(b)?b:fallback;
        if(!Double.isFinite(b))return a;
        return (a+b)*0.5;
    }
    private static boolean level(VehicleInstance v,double smoothing){
        return smooth(v,0,0,smoothing);
    }

    private static boolean smooth(VehicleInstance v,double pitch,double roll,double amount){
        double oldPitch=v.visualPitch,oldRoll=v.visualRoll;
        v.visualPitch=VehicleTerrain.smooth(oldPitch,pitch,amount);
        v.visualRoll=VehicleTerrain.smooth(oldRoll,roll,amount);
        if(Math.abs(v.visualPitch)<0.02&&Math.abs(pitch)<0.02)v.visualPitch=0;
        if(Math.abs(v.visualRoll)<0.02&&Math.abs(roll)<0.02)v.visualRoll=0;
        return Math.abs(v.visualPitch-oldPitch)>0.025
                ||Math.abs(v.visualRoll-oldRoll)>0.025;
    }
}
