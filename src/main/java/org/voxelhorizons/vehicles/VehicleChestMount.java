package org.voxelhorizons.vehicles;

import org.bukkit.entity.Entity;

/**
 * The chest visual is a passenger of the vehicle ItemDisplay, not an
 * independently teleported model. Minecraft clients interpolate the parent
 * once for the rider and the accessory. No packet APIs or extra tasks.
 *
 * Not every server implementation accepts another passenger, so a failed
 * attachment falls back to the existing synchronized teleport renderer.
 * Attempts are rate-limited by the logical vehicle lifecycle: create,
 * content refresh, remount, or unexpected passenger detachment.
 */
final class VehicleChestMount {
    private VehicleChestMount(){}

    static boolean attached(VehicleInstance v){
        return v!=null && v.display!=null && v.chestDisplay!=null
                && v.display.isValid() && v.chestDisplay.isValid()
                && v.chestDisplay.getVehicle()==v.display;
    }

    static boolean ensure(VehicleInstance v,boolean enabled){
        if(v==null||v.display==null||v.chestDisplay==null
                ||!v.display.isValid()||!v.chestDisplay.isValid())return false;
        if(!enabled){
            if(attached(v)||v.chestPassengerLinked)release(v);
            return false;
        }
        if(attached(v)){
            v.chestPassengerLinked=true;
            return true;
        }
        if(v.chestPassengerLinked){
            // Paper/client state discarded the passenger while the tractor
            // continued moving. Permit one reattachment instead of freezing
            // the accessory at its previous location.
            v.chestPassengerLinked=false;
            v.chestPassengerAttempted=false;
        }
        if(v.chestPassengerAttempted)return false;
        v.chestPassengerAttempted=true;
        try{
            Entity former=v.chestDisplay.getVehicle();
            if(former!=null)former.removePassenger(v.chestDisplay);
            if(v.display.addPassenger(v.chestDisplay)){
                v.chestPassengerLinked=true;
                // No separate entity teleports while mounted. Forget the
                // previous root pose so any later fallback moves immediately.
                v.teleports.forget(v.chestDisplay);
                return true;
            }
        }catch(RuntimeException ignored){
            // Keep the original independent ItemDisplay fallback.
        }
        return false;
    }

    /** A new seating or content refresh is a safe, bounded retry point. */
    static void allowRetry(VehicleInstance v){
        if(v!=null&&!attached(v))v.chestPassengerAttempted=false;
    }

    /** Remove only the accessory. Do not eject the driver or other riders. */
    static void release(VehicleInstance v){
        if(v==null)return;
        if(v.display!=null && v.chestDisplay!=null && attached(v)){
            try{v.display.removePassenger(v.chestDisplay);}
            catch(RuntimeException ignored){/* invalidated during unload */ }
        }
        v.chestPassengerLinked=false;
        v.chestPassengerAttempted=false;
        v.teleports.forget(v.chestDisplay);
    }

    static void reset(VehicleInstance v){
        release(v);
    }
}
