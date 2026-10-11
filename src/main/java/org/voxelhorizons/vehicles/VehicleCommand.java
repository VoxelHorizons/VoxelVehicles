package org.voxelhorizons.vehicles;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import java.util.*;

/** Developer commands; players place and pick up normal VoxelCore items. */
final class VehicleCommand implements CommandExecutor,TabCompleter {
    private final VehicleManager vehicles;
    private final VoxelCore core;
    VehicleCommand(VehicleManager vehicles,VoxelCore core){this.vehicles=vehicles;this.core=core;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("voxelvehicles.admin")){sender.sendMessage("§cNo permission.");return true;}
        if(args.length==0){sender.sendMessage("§7/vv <list|spawn <namespace:id>|remove|reload|cleanup|sync>");return true;}
        String action=args[0].toLowerCase(Locale.ROOT);
        if("list".equals(action)){
            sender.sendMessage("§6VoxelVehicles: §f"+vehicles.definitions().keySet());return true;
        }
        if("reload".equals(action)){
            try{vehicles.refreshDefinitions();sender.sendMessage("§aLoaded "+vehicles.definitions().size()+" definitions.");}
            catch(RuntimeException error){sender.sendMessage("§cInvalid vehicle config: "+error.getMessage());}
            return true;
        }
        if("cleanup".equals(action)){
            int removed=vehicles.cleanupLoadedOrphans();
            sender.sendMessage("§aRemoved "+removed+" orphaned vehicle entities.");return true;
        }
        if("sync".equals(action)){
            vehicles.synchronizeLoaded();
            sender.sendMessage("§aSynchronized loaded vehicles with VoxelCore.");return true;
        }
        if(!(sender instanceof Player)){sender.sendMessage("§cRequires a player.");return true;}
        Player player=(Player)sender;
        if("spawn".equals(action)){
            if(args.length<2){sender.sendMessage("§cUsage: /vv spawn <namespace:id>");return true;}
            try{
                ContentID id=ContentID.parse(args[1],"voxel");
                Location at=player.getLocation().clone();
                at.setYaw(player.getLocation().getYaw());
                VehicleManager.SpawnResult result=vehicles.trySpawn(id,player.getUniqueId(),at,null);
                sender.sendMessage(result.successful()?
                        "§aVehicle spawned.":"§cCannot spawn "+id+": "+result.failure);
            }catch(IllegalArgumentException e){sender.sendMessage("§c"+e.getMessage());}
            return true;
        }
        if("remove".equals(action)){
            VehicleInstance closest=null;
            double distance=36;
            for(VehicleInstance instance:vehicles.instances()){
                if(!instance.location.getWorld().equals(player.getWorld()))continue;
                double next=instance.location.distanceSquared(player.getLocation());
                if(next<distance){closest=instance;distance=next;}
            }
            sender.sendMessage(closest!=null&&vehicles.remove(closest)
                    ?"§aVehicle removed.":"§cNo vehicle within six blocks.");
            return true;
        }
        return false;
    }

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("voxelvehicles.admin"))return Collections.emptyList();
        List<String> out=new ArrayList<String>();
        if(args.length==1)out.addAll(Arrays.asList("list","spawn","remove","reload","cleanup","sync"));
        else if(args.length==2&&"spawn".equalsIgnoreCase(args[0]))
            for(ContentID id:vehicles.definitions().keySet())out.add(id.toString());
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
        out.removeIf(s->!s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
