package org.voxelhorizons.vehicles;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Item placement and right-click mounting integrate directly with VoxelCore. */
final class VehicleListener implements Listener {
    private final VoxelVehicles plugin;
    private final VehicleManager vehicles;
    private final VoxelCore core;
    // Right-clicks can also emit hand animations on some client versions.
    // Tracking right-click intent prevents those swings from claiming a vehicle.
    private final Map<UUID,Long> recentRightClicks=new HashMap<UUID,Long>();
    VehicleListener(VoxelVehicles plugin,VehicleManager vehicles,VoxelCore core) {
        this.plugin=plugin;this.vehicles=vehicles;this.core=core;
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(PlayerInteractEvent event){
        if(event.getHand()!=EquipmentSlot.HAND||event.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        Player player=event.getPlayer();
        ItemStack hand=player.getInventory().getItemInMainHand();
        Optional<ContentID> id=core.getItemManager().identify(hand);
        if(!id.isPresent()||!vehicles.definition(id.get()).isPresent())return;
        Location target=event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5,0,0.5);
        target.setYaw(player.getLocation().getYaw());
        VehicleManager.SpawnResult result=vehicles.trySpawn(id.get(),player.getUniqueId(),target,hand);
        if(!result.successful()){
            player.sendMessage("§cCannot place "+id.get()+": "+result.failure);
            event.setCancelled(true);
            return;
        }
        if(player.getGameMode()!=GameMode.CREATIVE){
            hand.setAmount(hand.getAmount()-1);
        }
        event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void interact(PlayerInteractEntityEvent event){
        if(event.getHand()!=EquipmentSlot.HAND)return;
        VehicleInstance v=vehicles.byEntity(event.getRightClicked()).orElse(null);
        if(v==null)return;
        Player player=event.getPlayer();
        markRightClick(player);
        event.setCancelled(true);
        VehicleDefinition d=vehicles.definition(v.type).orElse(null);
        if(d==null)return;
        if(!d.publicAccess&&!v.owner.equals(player.getUniqueId())
                &&!player.hasPermission("voxelvehicles.admin")){
            player.sendMessage("§cYou cannot modify this vehicle.");return;
        }
        if(v.chestHitbox!=null&&event.getRightClicked().equals(v.chestHitbox)){
            if(player.isSneaking()&&v.chestInstalled()){
                detachChest(v,player);
            }else openInventory(player,v);
            return;
        }
        // A compatible chest clicked onto the vehicle upgrades its cargo
        // without placing a chest block or mounting the driver.
        if(tryInstallChest(v,player))return;
        if(player.isSneaking()){
            openInventory(player,v);
            return;
        }
        if(!vehicles.mount(v,player))
            player.sendMessage("§cThis vehicle is already occupied or access is denied.");
    }

    /**
     * The hit-position variant has its own handler registration on some
     * server versions. Handle sneak use from either event path; the menu
     * de-duplicates repeated opens of the same vehicle.
     */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void interactAt(PlayerInteractAtEntityEvent event){
        if(event.getPlayer().isSneaking())interact(event);
    }

    /**
     * Fallback for interactions targeted at the large Interaction hitbox when
     * another plugin cancels entity interaction or the click is reported as
     * a block/air use. This does not affect ordinary right-clicks unless the
     * player is sneaking and directly targeting a live vehicle.
     */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void sneakRightClickFallback(PlayerInteractEvent event){
        if(event.getHand()!=EquipmentSlot.HAND||!event.getPlayer().isSneaking())return;
        if(event.getAction()!=Action.RIGHT_CLICK_AIR&&event.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        Player player=event.getPlayer();
        VehicleInstance v=vehicles.targeted(player,5.0).orElse(null);
        if(v==null)return;
        markRightClick(player);
        event.setCancelled(true);
        if(!tryInstallChest(v,player))openInventory(player,v);
    }

    private boolean tryInstallChest(VehicleInstance vehicle,Player player){
        VehicleDefinition d=vehicles.definition(vehicle.type).orElse(null);
        if(d==null||!d.inventory.chest.enabled||!d.inventory.enabled
                ||!d.inventory.storageEnabled||d.inventory.rows<1)return false;
        ItemStack hand=player.getInventory().getItemInMainHand();
        if(hand==null||hand.getType()!=d.inventory.chest.item)return false;
        if(!vehicle.owner.equals(player.getUniqueId())
                &&!player.hasPermission("voxelvehicles.admin")){
            player.sendMessage("§cOnly the owner can install a chest on this vehicle.");
            return true;
        }
        if(vehicle.chestInstalled()){
            openInventory(player,vehicle);
            return true;
        }
        if(vehicle.driver!=null&& !vehicle.driver.equals(player.getUniqueId()))return true;
        if(vehicles.installChest(vehicle,hand)){
            int remaining=hand.getAmount()-1;
            if(remaining>0)hand.setAmount(remaining);
            else player.getInventory().setItemInMainHand(null);
            player.sendMessage("§aStorage chest attached to vehicle.");
        }else player.sendMessage("§cCannot attach a storage chest to this vehicle.");
        return true;
    }

    private void detachChest(VehicleInstance vehicle,Player player){
        if(!vehicle.owner.equals(player.getUniqueId())
                &&!player.hasPermission("voxelvehicles.admin")){
            player.sendMessage("§cOnly the owner can remove this chest.");return;
        }
        ItemStack returned=vehicles.detachChest(vehicle);
        if(returned==null){
            player.sendMessage("§cEmpty the chest storage before detaching it.");
            return;
        }
        Map<Integer,ItemStack> remaining=player.getInventory().addItem(returned);
        for(ItemStack excess:remaining.values())
            player.getWorld().dropItemNaturally(player.getLocation(),excess);
        player.sendMessage("§aStorage chest removed.");
    }

    private void markRightClick(Player player){
        recentRightClicks.put(player.getUniqueId(),Long.valueOf(
                player.getWorld().getFullTime()));
    }

    private boolean suppressPickup(Player player){
        return shouldSuppressPickup(player.isSneaking(),
                recentRightClicks.get(player.getUniqueId()),player.getWorld().getFullTime());
    }

    static boolean shouldSuppressPickup(boolean crouching,Long lastRightClick,long now){
        if(crouching)return true; // Shift-right-click must never pick up a vehicle.
        if(lastRightClick==null)return false;
        long elapsed=now-lastRightClick.longValue();
        return elapsed>=0&&elapsed<=2;
    }

    private void openInventory(Player player,VehicleInstance vehicle){
        if(vehicles.inventories()!=null)
            vehicles.inventories().open(player,vehicle);
    }

    /**
     * Vanilla Java handles E locally while riding an ItemDisplay, so no
     * inventory-open packet/event exists to intercept. F (swap hands) does
     * produce a Bukkit event and provides a client-mod-free riding shortcut.
     * This affects ONLY the driver of a vehicle that has an enabled GUI.
     */
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void ridingInventoryShortcut(PlayerSwapHandItemsEvent event){
        if(!plugin.getConfig().getBoolean("inventory.open-with-swap-hand",true))return;
        Player player=event.getPlayer();
        if(player.getVehicle()==null)return;
        VehicleInstance vehicle=vehicles.byEntity(player.getVehicle()).orElse(null);
        if(vehicle==null||!player.getUniqueId().equals(vehicle.driver))return;
        VehicleDefinition definition=vehicles.definition(vehicle.type).orElse(null);
        if(definition==null||!definition.inventory.enabled||vehicles.inventories()==null)return;
        if(!player.hasPermission("voxelvehicles.inventory")
                &&!player.hasPermission("voxelvehicles.admin"))return;
        event.setCancelled(true);
        // Do not open a chest menu inside a hand-swap packet callback.
        // Confirm the player is still riding this vehicle on the next tick.
        plugin.getServer().getScheduler().runTask(plugin,()->{
            if(player.isOnline()&&player.getVehicle()!=null
                    &&vehicles.byEntity(player.getVehicle()).orElse(null)==vehicle
                    &&player.getUniqueId().equals(vehicle.driver))
                vehicles.inventories().open(player,vehicle);
        });
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event){
        if(vehicles.byEntity(event.getEntity()).isPresent())event.setCancelled(true);
    }

    /**
     * EntityDamageByEntityEvent handles attacks on the visible or interaction entity
     * when the server sends that event. No crouch is required to reclaim a vehicle.
     */
    @EventHandler(priority=EventPriority.HIGHEST)
    public void pickup(EntityDamageByEntityEvent event){
        VehicleInstance v=vehicles.byEntity(event.getEntity()).orElse(null);
        if(v==null||!(event.getDamager() instanceof Player))return;
        event.setCancelled(true);
        Player player=(Player)event.getDamager();
        if(!suppressPickup(player))collect(v,player);
    }

    /**
     * Some server versions don't fire damage events for invulnerable Interaction
     * entities. Left-click arm-swing plus a precise look-ray fallback supports those.
     */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void punch(PlayerAnimationEvent event){
        if(event.getAnimationType()!=PlayerAnimationType.ARM_SWING)return;
        Player player=event.getPlayer();
        if(suppressPickup(player))return;
        VehicleInstance v=vehicles.targeted(player,5.0).orElse(null);
        if(v!=null)collect(v,player);
    }

    private void collect(VehicleInstance v,Player player){
        if(v.driver!=null||suppressPickup(player))return;
        if(!v.owner.equals(player.getUniqueId())
                &&!player.hasPermission("voxelvehicles.admin"))return;
        VehicleDefinition d=vehicles.definition(v.type).orElse(null);
        if(!vehicles.remove(v)){
            player.sendMessage("§cRemove the installed chest and empty vehicle storage before picking it up.");
            return;
        }
        if(d!=null){
            // Drop back into the world so creative and survival players can both
            // reclaim a vehicle without losing its normal VoxelCore item identity.
            ItemStack drop=core.getItemManager().createItem(d.drop);
            if(v.dyeColor!=null&&core.getItemManager().isDyeable(drop))
                drop=core.getItemManager().setDyeColor(drop,v.dyeColor.intValue());
            player.getWorld().dropItemNaturally(v.location,drop);
        }
    }

    @EventHandler public void leave(PlayerQuitEvent event){
        recentRightClicks.remove(event.getPlayer().getUniqueId());
        vehicles.dismount(event.getPlayer());
    }
    @EventHandler public void load(ChunkLoadEvent event){vehicles.loadChunk(event.getChunk());}
    @EventHandler public void unload(ChunkUnloadEvent event){vehicles.unloadChunk(event.getChunk());}
}
