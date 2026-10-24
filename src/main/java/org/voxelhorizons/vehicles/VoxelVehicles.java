package org.voxelhorizons.vehicles;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.vehicles.bedrock.BedrockProjectionPublisher;

import java.util.logging.Level;

/** VoxelCore-backed vehicle addon; no independent item database. */
public final class VoxelVehicles extends JavaPlugin {
    private VoxelCore core;
    private VehicleManager vehicles;
    private VehicleInventories inventories;
    private BedrockProjectionPublisher bedrockPublisher;

    @Override public void onEnable() {
        saveDefaultConfig();
        Plugin dependency=getServer().getPluginManager().getPlugin("VoxelCore");
        if(!(dependency instanceof VoxelCore)||!dependency.isEnabled()){
            getLogger().severe("An enabled VoxelCore is required.");
            getServer().getPluginManager().disablePlugin(this);return;
        }
        try{
            // ITEM_DISPLAY and INTERACTION were added in 1.19.4.
            org.bukkit.entity.EntityType.valueOf("ITEM_DISPLAY");
            org.bukkit.entity.EntityType.valueOf("INTERACTION");
            core=(VoxelCore)dependency;
            vehicles=new VehicleManager(this,core);
            vehicles.start();
            inventories=new VehicleInventories(this,vehicles,core);
            vehicles.attachInventories(inventories);
            getServer().getPluginManager().registerEvents(inventories,this);
            getServer().getPluginManager().registerEvents(new VehicleListener(this,vehicles,core),this);
            VehicleCommand command=new VehicleCommand(vehicles,core);
            PluginCommand root=getCommand("voxelvehicles");
            if(root==null)throw new IllegalStateException("Missing voxelvehicles command");
            root.setExecutor(command);root.setTabCompleter(command);
            boolean nativeInputs=VehicleInputAdapter.register(this,vehicles);
            if(!nativeInputs)getLogger().warning("PlayerInputEvent missing: vehicle driving is disabled on this older server. Placement and storage remain available.");
            bedrockPublisher = BedrockProjectionPublisher.start(this,"vehicles",
                    () -> vehicles.bedrockSnapshot().asMap());
            getLogger().info("Loaded "+vehicles.definitions().size()+" vehicles from VoxelCore and "+
                    vehicles.instances().size()+" placed vehicles. Native driving: "+nativeInputs);
        }catch(RuntimeException e){
            getLogger().log(Level.SEVERE,"VoxelVehicles could not start",e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable(){if(bedrockPublisher!=null)bedrockPublisher.close();if(vehicles!=null)vehicles.shutdown();}
    VoxelCore core(){return core;}
    public VehicleManager getVehicleManager(){return vehicles;}
}
