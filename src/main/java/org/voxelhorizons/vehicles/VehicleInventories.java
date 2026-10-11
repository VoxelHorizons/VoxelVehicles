package org.voxelhorizons.vehicles;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import java.util.*;

/** Config-driven chest overlay with one locked resource-pack GUI model preview. */
final class VehicleInventories implements Listener {
    private final VoxelVehicles plugin;
    private final VehicleManager manager;
    private final VoxelCore core;
    private final Map<UUID,Session> open=new HashMap<UUID,Session>();

    private static final class Session {
        final VehicleInstance vehicle;
        final Player player;
        final Inventory top;
        final List<Integer> cargoSlots;
        Session(VehicleInstance vehicle,Player player,Inventory top,List<Integer> slots){
            this.vehicle=vehicle;this.player=player;this.top=top;this.cargoSlots=slots;
        }
    }

    VehicleInventories(VoxelVehicles plugin,VehicleManager manager,VoxelCore core){
        this.plugin=plugin;this.manager=manager;this.core=core;
    }

    boolean open(Player player,VehicleInstance vehicle){
        VehicleDefinition definition=manager.definition(vehicle.type).orElse(null);
        if(definition==null||!definition.inventory.enabled){
            player.sendMessage("§cThis vehicle has no inventory.");return false;
        }
        if(!player.hasPermission("voxelvehicles.inventory")
                &&!player.hasPermission("voxelvehicles.admin")){
            player.sendMessage("§cYou cannot use vehicle inventories.");return false;
        }
        if(!definition.publicAccess&&!vehicle.owner.equals(player.getUniqueId())
                &&!player.hasPermission("voxelvehicles.admin")){
            player.sendMessage("§cThis vehicle is private.");return false;
        }
        if(vehicle.driver!=null&&!vehicle.driver.equals(player.getUniqueId())
                &&!player.hasPermission("voxelvehicles.admin")){
            player.sendMessage("§cThe vehicle is occupied.");return false;
        }
        Session existing=open.get(vehicle.id);
        if(existing!=null){
            if(existing.player.getUniqueId().equals(player.getUniqueId())
                    &&player.getOpenInventory()!=null
                    &&player.getOpenInventory().getTopInventory()==existing.top)return true;
            if(existing.player.getUniqueId().equals(player.getUniqueId())){
                capture(existing);
                open.remove(vehicle.id);
            }else{
                player.sendMessage("§cThis vehicle inventory is currently in use.");
                return false;
            }
        }
        // A vehicle selects storage rows (0=no storage); the configured GUI
        // has a minimum height to preserve the authored model preview panel.
        int minimumRows=plugin.getConfig().getInt("inventory.rows",3);
        int storageRows=definition.inventory.effectiveStorageRows();
        int rows=Math.max(minimumRows,storageRows);
        int size=rows*9;
        int preview=plugin.getConfig().getInt("inventory.preview-slot",11);
        if(minimumRows<1||minimumRows>6||preview<0||preview>=size){
            player.sendMessage("§cInvalid vehicle GUI dimensions or preview slot.");return false;
        }
        List<Integer> cargo;
        try{
            cargo=storageSlots(plugin.getConfig().getIntegerList("inventory.storage-slots"),
                    storageRows,preview,size);
        }catch(IllegalArgumentException error){
            player.sendMessage("§cInvalid vehicle storage layout: "+error.getMessage());
            return false;
        }
        if(storageRows>0&&cargo.isEmpty()){
            player.sendMessage("§cVehicle storage rows have no assigned slots.");
            return false;
        }
        String raw=storageRows==0
                ?definition.inventory.title:definition.inventory.storageTitle;
        if(raw==null)raw=plugin.getConfig().getString(
                storageRows==0
                        ?"inventory.titles.without-storage":"inventory.titles.with-storage","Vehicle");
        String title=resolveTitle(player,raw);
        VehicleInventoryHolder holder=new VehicleInventoryHolder(vehicle.id);
        Inventory inv=Bukkit.createInventory(holder,size,title);
        holder.bind(inv);
        for(int i=0;i<cargo.size();i++)if(vehicle.storage[i]!=null)
            inv.setItem(cargo.get(i),vehicle.storage[i].clone());
        ContentID itemId=definition.inventory.previewItem!=null
                ?definition.inventory.previewItem:definition.model;
        try{
            ItemStack item=core.getItemManager().createRenderItem(itemId);
            if(vehicle.dyeColor!=null&&core.getItemManager().isDyeable(item))
                item=core.getItemManager().setDyeColor(item,vehicle.dyeColor.intValue());
            inv.setItem(preview,item);
        }catch(RuntimeException error){
            plugin.getLogger().warning("Cannot render vehicle GUI preview "+itemId+": "+error.getMessage());
            player.sendMessage("§cMissing preview item "+itemId+".");return false;
        }
        open.put(vehicle.id,new Session(vehicle,player,inv,cargo));
        player.openInventory(inv);
        return true;
    }

    /**
     * Keep the existing five slots per row exactly as configured. Every
     * configured slot within the first N rows becomes cargo; there is no
     * separate slot-count setting to keep synchronized with the artwork.
     */
    static List<Integer> storageSlots(List<Integer> configured,int storageRows,
            int preview,int visibleSize){
        if(storageRows<0||storageRows>6)throw new IllegalArgumentException("rows must be 0..6");
        Set<Integer> seen=new HashSet<Integer>();
        List<Integer> active=new ArrayList<Integer>();
        for(int slot:configured){
            // Allow slots in later rows even when the current vehicle uses
            // only the original 3-row preview overlay.
            if(slot<0||slot>=54||slot==preview||!seen.add(slot))
                throw new IllegalArgumentException("invalid or duplicate slot "+slot);
            if(slot/9<storageRows){
                if(slot>=visibleSize)throw new IllegalArgumentException("slot "+slot+" is outside the menu");
                active.add(slot);
            }
        }
        return active;
    }

    private String resolveTitle(Player player,String source){
        String resolved=core.getTextPlaceholderService().resolve(
                ChatColor.translateAlternateColorCodes('&',source),true,true);
        if(plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")){
            try{
                Class<?> api=Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Object value=api.getMethod("setPlaceholders",org.bukkit.OfflinePlayer.class,
                        String.class).invoke(null,player,resolved);
                if(value instanceof String)resolved=(String)value;
            }catch(ReflectiveOperationException ignored){}
        }
        return ChatColor.translateAlternateColorCodes('&',resolved);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void click(InventoryClickEvent event){
        Session session=find(event.getView().getTopInventory());
        if(session==null)return;
        int raw=event.getRawSlot();
        // Double-click collection searches the entire open inventory,
        // including the locked preview item, even when a cargo slot is clicked.
        if(event.getAction()==InventoryAction.COLLECT_TO_CURSOR){
            event.setCancelled(true);
            return;
        }
        if(raw>=0&&raw<session.top.getSize()){
            if(!session.cargoSlots.contains(raw)){event.setCancelled(true);return;}
        }else if(event.getAction()==InventoryAction.MOVE_TO_OTHER_INVENTORY
                ||event.getAction()==InventoryAction.COLLECT_TO_CURSOR){
            // These actions could steal the preview or populate locked slots.
            event.setCancelled(true);return;
        }
        queueCapture(session);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void drag(InventoryDragEvent event){
        Session s=find(event.getView().getTopInventory());
        if(s==null)return;
        for(int raw:event.getRawSlots())
            if(raw<s.top.getSize()&&!s.cargoSlots.contains(raw)){
                event.setCancelled(true);return;
            }
        queueCapture(s);
    }

    @EventHandler public void close(InventoryCloseEvent event){
        Session s=find(event.getView().getTopInventory());
        if(s==null)return;
        open.remove(s.vehicle.id);
        capture(s);
        manager.save();
    }

    private void queueCapture(Session s){
        plugin.getServer().getScheduler().runTask(plugin,()->{
            if(open.get(s.vehicle.id)==s)capture(s);
        });
    }
    private Session find(Inventory top){
        if(top==null||!(top.getHolder() instanceof VehicleInventoryHolder))return null;
        Session s=open.get(((VehicleInventoryHolder)top.getHolder()).vehicleId());
        return s!=null&&s.top==top?s:null;
    }
    private void capture(Session s){
        for(int i=0;i<s.cargoSlots.size();i++){
            ItemStack item=s.top.getItem(s.cargoSlots.get(i));
            s.vehicle.storage[i]=item==null?null:item.clone();
        }
    }
    void captureOpen(){for(Session s:new ArrayList<Session>(open.values()))capture(s);}
    void closeFor(VehicleInstance vehicle){
        Session s=open.get(vehicle.id);
        if(s==null)return;
        capture(s);
        s.player.closeInventory();
        open.remove(vehicle.id);
    }
    void shutdown(){
        for(Session s:new ArrayList<Session>(open.values()))closeFor(s.vehicle);
    }
}
