package org.voxelhorizons.vehicles;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.flags.type.Flags;
import me.angeschossen.lands.api.flags.type.RoleFlag;
import me.angeschossen.lands.api.land.LandWorld;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Optional WorldGuard and Lands hooks; no broad vanilla interaction exemptions. */
public final class VehicleProtection {
    public enum Action { PLACE, BREAK, INTERACT, INVENTORY, MODIFY }

    private final boolean worldGuard;
    private final boolean landsEnabled;
    private final LandsIntegration lands;

    public static void registerWorldGuardFlags(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null
                && plugin.getConfig().getBoolean("protection.worldguard", true))
            WorldGuardHook.register();
    }

    public VehicleProtection(JavaPlugin plugin) {
        worldGuard = plugin.getConfig().getBoolean("protection.worldguard", true)
                && Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
        landsEnabled = plugin.getConfig().getBoolean("protection.lands", true)
                && Bukkit.getPluginManager().isPluginEnabled("Lands");
        lands = landsEnabled ? LandsHook.connect(plugin) : null;
        plugin.getLogger().info("Protection: WorldGuard=" + worldGuard + ", Lands=" + landsEnabled);
    }

    public boolean allows(Player player, Location at, Action action) {
        if (player == null || at == null || at.getWorld() == null) return false;
        if (worldGuard && !WorldGuardHook.allows(player, at, action)) return false;
        return !landsEnabled || LandsHook.allows(lands, player, at, action);
    }

    /** Cancelled events are eligible for recovery only if a custom region flag grants it. */
    public boolean explicitWorldGuardAllow(Player player, Location at, Action action) {
        return worldGuard && WorldGuardHook.explicitAllow(player, at, action);
    }

    private static final class WorldGuardHook {
        private static StateFlag place, breakFlag, interact, inventory, modify;

        static void register() {
            FlagRegistry flags = WorldGuard.getInstance().getFlagRegistry();
            place = register(flags, "voxel-vehicle-place");
            breakFlag = register(flags, "voxel-vehicle-break");
            interact = register(flags, "voxel-vehicle-interact");
            inventory = register(flags, "voxel-vehicle-inventory");
            modify = register(flags, "voxel-vehicle-modify");
        }

        private static StateFlag register(FlagRegistry registry, String name) {
            try {
                StateFlag flag = new StateFlag(name, false);
                registry.register(flag);
                return flag;
            } catch (FlagConflictException conflict) {
                Flag<?> existing = registry.get(name);
                if (existing instanceof StateFlag) return (StateFlag) existing;
                throw new IllegalStateException("WorldGuard flag conflict for " + name, conflict);
            }
        }

        private static StateFlag forAction(Action action) {
            switch (action) {
                case PLACE: return place;
                case BREAK: return breakFlag;
                case INVENTORY: return inventory;
                case MODIFY: return modify;
                default: return interact;
            }
        }

        private static RegionQuery query() {
            return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        }

        private static boolean explicitAllow(Player player, Location at, Action action) {
            StateFlag flag = forAction(action);
            return flag != null && query().queryState(BukkitAdapter.adapt(at),
                    WorldGuardPlugin.inst().wrapPlayer(player), flag) == StateFlag.State.ALLOW;
        }

        private static boolean allows(Player player, Location at, Action action) {
            StateFlag flag = forAction(action);
            if (flag == null) return false;
            com.sk89q.worldedit.util.Location location = BukkitAdapter.adapt(at);
            com.sk89q.worldguard.LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            RegionQuery regions = query();
            if (action == Action.INVENTORY
                    && regions.queryState(location, localPlayer,
                    com.sk89q.worldguard.protection.flags.Flags.CHEST_ACCESS) == StateFlag.State.DENY)
                return false;
            return regions.testBuild(location, localPlayer, flag);
        }
    }

    private static final class LandsHook {
        private static LandsIntegration connect(JavaPlugin plugin) {
            return LandsIntegration.of(plugin);
        }

        private static boolean allows(LandsIntegration api, Player player, Location at, Action action) {
            LandWorld world = api.getWorld(at.getWorld());
            if (world == null) return true;
            RoleFlag flag;
            switch (action) {
                case PLACE:
                case MODIFY: flag = Flags.BLOCK_PLACE; break;
                case BREAK: flag = Flags.BLOCK_BREAK; break;
                case INVENTORY: flag = Flags.INTERACT_CONTAINER; break;
                default: flag = Flags.VEHICLE_USE; break;
            }
            Material material = action == Action.INVENTORY ? Material.CHEST : null;
            return world.hasRoleFlag(api.getLandPlayer(player.getUniqueId()), at, flag, material, false);
        }
    }
}
