package org.qs.qsdungeons.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.qs.qsdungeons.QsDungeons;

import java.util.Map;

/**
 * Protege las regiones de las dungeons salvo para administradores.
 */
public final class DungeonProtectionListener implements Listener {

    private final QsDungeons plugin;

    public DungeonProtectionListener(QsDungeons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (player != null && block != null) {
            org.qs.qsdungeons.dungeon.instance.DungeonInstance instance = plugin.getDungeonManager().getPlayerInstance(player);
            if (instance != null && instance.hasChest(block) && instance.getSettings().isAllowChestBreak()) {
                return;
            }
            org.qs.qsdungeons.dungeon.Dungeon dungeon = plugin.getDungeonManager().findDungeonAt(block.getLocation());
            if (dungeon != null && dungeon.getSettings().isAllowChestBreak()) {
                org.qs.qsdungeons.dungeon.instance.DungeonInstance active = plugin.getDungeonManager().getInstance(dungeon.getId());
                if (active != null && active.hasChest(block)) {
                    return;
                }
            }
        }
        if (deny(player, block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (deny(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        if (deny(event.getPlayer(), block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlock();
        if (deny(event.getPlayer(), block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.getDungeonManager().isProtected(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.getDungeonManager().isProtected(block.getLocation()));
    }

    private boolean deny(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }
        if (player.hasPermission("qsdungeons.admin.bypass")
                && plugin.getConfig().getBoolean("protection.admin-bypass", false)) {
            return false;
        }
        if (!plugin.getDungeonManager().isProtected(block.getLocation())) {
            return false;
        }
        plugin.getMessages().send(player, "protection.denied", Map.of());
        return true;
    }
}
