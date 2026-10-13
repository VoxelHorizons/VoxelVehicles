package org.voxelhorizons.vehicles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.voxelhorizons.content.ContentID;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/** Durable logical instances: entity IDs are never serialized. */
final class VehicleStore {
    private final File file;
    VehicleStore(File file){this.file=file;}

    void load(Map<UUID,VehicleInstance> vehicles){
        if(!file.isFile())return;
        YamlConfiguration config=YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section=config.getConfigurationSection("vehicles");
        if(section==null)return;
        for(String key:section.getKeys(false)){
            try{
                UUID id=UUID.fromString(key);
                String worldName=section.getString(key+".world");
                World world=Bukkit.getWorld(worldName);
                if(world==null)throw new IllegalStateException("World unavailable: "+worldName);
                ContentID type=ContentID.parse(section.getString(key+".type"),"voxel");
                UUID owner=UUID.fromString(section.getString(key+".owner"));
                double x=section.getDouble(key+".x"),y=section.getDouble(key+".y"),z=section.getDouble(key+".z");
                float yaw=(float)section.getDouble(key+".yaw");
                if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Non-finite location");
                Integer color=null;
                Object savedColor=section.get(key+".color");
                if(savedColor!=null){
                    if(!(savedColor instanceof Number))throw new IllegalArgumentException("Invalid RGB color value");
                    int rgb=((Number)savedColor).intValue();
                    if(rgb<0||rgb>0xFFFFFF||((Number)savedColor).doubleValue()!=rgb)
                        throw new IllegalArgumentException("RGB color must be an integer between 0 and 16777215");
                    color=Integer.valueOf(rgb);
                }
                VehicleInstance vehicle=new VehicleInstance(id,type,owner,new Location(world,x,y,z,yaw,0),color);
                // Absent on format <=3: pickup rebuilds the correct concrete
                // vehicle type instead of the generic model/drop parent.
                ItemStack original=section.getItemStack(key+".original-item");
                if(original!=null){
                    try{vehicle.originalItem=VehiclePickupItem.capture(original);}
                    catch(IllegalArgumentException invalid){
                        // A damaged original-item field must never destroy the
                        // entire parked vehicle or its independent cargo.
                        Bukkit.getLogger().warning("[VoxelVehicles] Invalid original item for "
                                +id+"; will reconstruct its concrete type at pickup: "
                                +invalid.getMessage());
                    }
                }
                ItemStack attachedChest=section.getItemStack(key+".installed-chest");
                if(attachedChest!=null&&attachedChest.getAmount()>0)
                    vehicle.installedChest=attachedChest.clone();
                ConfigurationSection cargo=section.getConfigurationSection(key+".storage");
                if(cargo!=null)for(String slot:cargo.getKeys(false)){
                    int index=Integer.parseInt(slot);
                    if(index<0||index>=vehicle.storage.length)throw new IllegalArgumentException("Invalid storage index");
                    ItemStack item=cargo.getItemStack(slot);
                    if(item!=null)vehicle.storage[index]=item.clone();
                }
                vehicles.put(id,vehicle);
            }catch(Exception error){
                Bukkit.getLogger().warning("[VoxelVehicles] Skipping invalid saved vehicle "+key+": "+error.getMessage());
            }
        }
    }

    void save(Map<UUID,VehicleInstance> vehicles) throws IOException{
        YamlConfiguration config=new YamlConfiguration();
        config.set("format",4);
        for(VehicleInstance v:vehicles.values()){
            String k="vehicles."+v.id+".";
            config.set(k+"type",v.type.toString());
            config.set(k+"owner",v.owner.toString());
            if(v.dyeColor!=null)config.set(k+"color",v.dyeColor);
            if(v.originalItem!=null)config.set(k+"original-item",
                    VehiclePickupItem.capture(v.originalItem));
            if(v.chestInstalled())config.set(k+"installed-chest",v.installedChest.clone());
            config.set(k+"world",v.location.getWorld().getName());
            config.set(k+"x",v.location.getX());config.set(k+"y",v.location.getY());
            config.set(k+"z",v.location.getZ());config.set(k+"yaw",v.location.getYaw());
            for(int i=0;i<v.storage.length;i++){
                ItemStack stored=v.storage[i];
                if(stored!=null&&stored.getAmount()>0)config.set(k+"storage."+i,stored.clone());
            }
        }
        File parent=file.getParentFile();
        if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Cannot create "+parent);
        File temp=new File(parent,file.getName()+".tmp");
        config.save(temp);
        try{Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(java.nio.file.AtomicMoveNotSupportedException ex){
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
