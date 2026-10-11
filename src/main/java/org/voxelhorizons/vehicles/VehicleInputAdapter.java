package org.voxelhorizons.vehicles;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.EventExecutor;
import java.lang.reflect.Method;

/** Native WASD adapter on 1.21.4+; no packet polling or ProtocolLib. */
final class VehicleInputAdapter {
    static boolean register(final VoxelVehicles plugin,final VehicleManager manager){
        try{
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventType=(Class<? extends Event>)Class.forName("org.bukkit.event.player.PlayerInputEvent");
            final Method getInput=eventType.getMethod("getInput");
            final Class<?> inputType=Class.forName("org.bukkit.Input");
            final Method forward=inputType.getMethod("isForward");
            final Method backward=inputType.getMethod("isBackward");
            final Method left=inputType.getMethod("isLeft");
            final Method right=inputType.getMethod("isRight");
            final Method jump=inputType.getMethod("isJump");
            final Method sneak=inputType.getMethod("isSneak");
            plugin.getServer().getPluginManager().registerEvent(eventType,new Listener(){},EventPriority.NORMAL,
                new EventExecutor(){
                    @Override public void execute(Listener ignored,Event event){
                        if(!(event instanceof PlayerEvent))return;
                        try{
                            Player player=((PlayerEvent)event).getPlayer();
                            Object input=getInput.invoke(event);
                            double axis=(bool(forward,input)?1:0)-(bool(backward,input)?1:0);
                            double turning=(bool(left,input)?-1:0)+(bool(right,input)?1:0);
                            VehiclePhysics.Input state=new VehiclePhysics.Input(axis,turning,
                                    bool(jump,input),bool(sneak,input));
                            if(event.isAsynchronous()){
                                plugin.getServer().getScheduler().runTask(plugin,()->manager.input(player,state));
                            }else manager.input(player,state);
                        }catch(ReflectiveOperationException ex){
                            plugin.getLogger().warning("Cannot read native player input: "+ex.getMessage());
                        }
                    }
                },plugin,true);
            return true;
        }catch(ReflectiveOperationException|LinkageError ex){
            return false;
        }
    }
    private static boolean bool(Method method,Object input)throws ReflectiveOperationException{
        return Boolean.TRUE.equals(method.invoke(input));
    }
}
