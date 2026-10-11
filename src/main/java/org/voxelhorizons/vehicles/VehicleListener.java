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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import java.util.Optional;

/** Item placement and right-click mounting integrate directly with VoxelCore. */
final class VehicleListener implements Listener {
    private final VoxelVehicles plugin;
    private final VehicleManager vehicles;
    private final VoxelCore core;
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

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEntityEvent event){
        if(event.getHand()!=EquipmentSlot.HAND)return;
        VehicleInstance v=vehicles.byEntity(event.getRightClicked()).orElse(null);
        if(v==null)return;
        Player player=event.getPlayer();
        // Sneak + non-block right-click opens the GUI instead of mounting.
        // Keep the existing sneak-with-block building behavior.
        if(player.isSneaking()){
            if(player.getInventory().getItemInMainHand().getType().isBlock())return;
            event.setCancelled(true);
            if(vehicles.inventories()!=null)vehicles.inventories().open(player,v);
            return;
        }
        event.setCancelled(true);
        if(!vehicles.mount(v,player))player.sendMessage("§cThis vehicle is already occupied or access is denied.");
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
        collect(v,(Player)event.getDamager());
    }

    /**
     * Some server versions don't fire damage events for invulnerable Interaction
     * entities. Left-click arm-swing plus a precise look-ray fallback supports those.
     */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void punch(PlayerAnimationEvent event){
        if(event.getAnimationType()!=PlayerAnimationType.ARM_SWING)return;
        VehicleInstance v=vehicles.targeted(event.getPlayer(),5.0).orElse(null);
        if(v!=null)collect(v,event.getPlayer());
    }

    private void collect(VehicleInstance v,Player player){
        if(v.driver!=null)return;
        if(!v.owner.equals(player.getUniqueId())
                &&!player.hasPermission("voxelvehicles.admin"))return;
        VehicleDefinition d=vehicles.definition(v.type).orElse(null);
        if(!vehicles.remove(v)){
            player.sendMessage("§cEmpty the vehicle storage before picking it up.");
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

    @EventHandler public void leave(PlayerQuitEvent event){vehicles.dismount(event.getPlayer());}
    @EventHandler public void load(ChunkLoadEvent event){vehicles.loadChunk(event.getChunk());}
    @EventHandler public void unload(ChunkUnloadEvent event){vehicles.unloadChunk(event.getChunk());}
}
