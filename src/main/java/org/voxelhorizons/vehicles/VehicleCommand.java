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
        if(args.length>0&&"inventory".equalsIgnoreCase(args[0])){
            if(!(sender instanceof Player)){sender.sendMessage("§cRequires a player.");return true;}
            Player viewer=(Player)sender;
            VehicleInstance selected=null;
            if(viewer.getVehicle()!=null)
                selected=vehicles.byEntity(viewer.getVehicle()).orElse(null);
            if(selected==null){
                double best=36;
                for(VehicleInstance instance:vehicles.instances()){
                    if(instance.location.getWorld()==null
                            ||!instance.location.getWorld().equals(viewer.getWorld()))continue;
                    double distance=instance.location.distanceSquared(viewer.getLocation());
                    if(distance<best){best=distance;selected=instance;}
                }
            }
            if(selected==null)viewer.sendMessage("§cNo nearby vehicle within six blocks.");
            else if(vehicles.inventories()!=null)vehicles.inventories().open(viewer,selected);
            return true;
        }
        if(args.length>0&&"chest".equalsIgnoreCase(args[0])){
            if(!(sender instanceof Player)){sender.sendMessage("§cRequires a player.");return true;}
            if(args.length!=2||!"remove".equalsIgnoreCase(args[1])){
                sender.sendMessage("§7Usage: /vv chest remove");
                return true;
            }
            Player player=(Player)sender;
            VehicleInstance target=null;
            if(player.getVehicle()!=null)target=vehicles.byEntity(player.getVehicle()).orElse(null);
            if(target==null){
                double best=36.0;
                for(VehicleInstance vehicle:vehicles.instances()){
                    if(vehicle.location.getWorld()==null
                            ||!vehicle.location.getWorld().equals(player.getWorld()))continue;
                    double dist=vehicle.location.distanceSquared(player.getLocation());
                    if(dist<best){best=dist;target=vehicle;}
                }
            }
            if(target==null){player.sendMessage("§cNo nearby vehicle within six blocks.");return true;}
            VehicleDefinition definition=vehicles.definition(target.type).orElse(null);
            if(definition==null||!definition.inventory.chest.enabled||!target.chestInstalled()){
                player.sendMessage("§cThat vehicle has no installed chest.");return true;
            }
            if(!target.owner.equals(player.getUniqueId())
                    &&!player.hasPermission("voxelvehicles.admin")){
                player.sendMessage("§cOnly the owner can remove this chest.");return true;
            }
            org.bukkit.inventory.ItemStack returned=vehicles.detachChest(target);
            if(returned==null){
                player.sendMessage("§cEmpty the vehicle storage before removing its chest.");
                return true;
            }
            Map<Integer,org.bukkit.inventory.ItemStack> overflow=player.getInventory().addItem(returned);
            for(org.bukkit.inventory.ItemStack excess:overflow.values())
                player.getWorld().dropItemNaturally(player.getLocation(),excess);
            player.sendMessage("§aStorage chest removed.");
            return true;
        }
        if(!sender.hasPermission("voxelvehicles.admin")){sender.sendMessage("§cNo permission.");return true;}
        if(args.length==0){sender.sendMessage("§7/vv <inventory|chest remove|list|spawn <namespace:id>|remove|reload|cleanup|sync>");return true;}
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
        if(!sender.hasPermission("voxelvehicles.admin")){
            if(!sender.hasPermission("voxelvehicles.inventory"))return Collections.emptyList();
            if(args.length==1){
                List<String> choices=new ArrayList<String>(Arrays.asList("inventory","chest"));
                choices.removeIf(choice->!choice.startsWith(args[0].toLowerCase(Locale.ROOT)));
                return choices;
            }
            if(args.length==2&&"chest".equalsIgnoreCase(args[0]))
                return "remove".startsWith(args[1].toLowerCase(Locale.ROOT))
                        ?Collections.singletonList("remove"):Collections.emptyList();
            return Collections.emptyList();
        }
        List<String> out=new ArrayList<String>();
        if(args.length==1)out.addAll(Arrays.asList("inventory","chest","list","spawn","remove","reload","cleanup","sync"));
        else if(args.length==2&&"chest".equalsIgnoreCase(args[0]))out.add("remove");
        else if(args.length==2&&"spawn".equalsIgnoreCase(args[0]))
            for(ContentID id:vehicles.definitions().keySet())out.add(id.toString());
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
        out.removeIf(s->!s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
