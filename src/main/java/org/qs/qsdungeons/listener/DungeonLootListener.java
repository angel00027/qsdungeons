package org.qs.qsdungeons.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.RewardBlock;
import org.qs.qsdungeons.dungeon.instance.DungeonInstance;

import java.util.Map;

/**
 * Rellena cofres y entrega loot al romper bloques premiados.
 */
public final class DungeonLootListener implements Listener {

    private final QsDungeons plugin;

    public DungeonLootListener(QsDungeons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        DungeonInstance instance = plugin.getDungeonManager().getPlayerInstance(player);
        if (instance == null) {
            return;
        }
        Block block = event.getBlock();
        if (instance.hasChest(block)) {
            if (instance.getSettings().isAllowChestBreak()) {
                instance.handleChestBroken(block);
                return;
            }
            event.setCancelled(true);
            plugin.getMessages().send(player, "protection.chest-protected",
                    Map.of("dungeon", instance.getDungeon().getId()));
            return;
        }
        RewardBlock reward = instance.getRewardAt(block);
        if (reward == null) {
            return;
        }
        event.setCancelled(true);
        instance.handleRewardBreak(player, block, reward);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        Dungeon dungeon = plugin.getDungeonManager().findDungeonAt(block.getLocation());
        if (dungeon == null) {
            return;
        }
        DungeonInstance instance = plugin.getDungeonManager().getPlayerInstance(player);
        if (instance == null || !instance.getDungeon().getId().equals(dungeon.getId())) {
            DungeonInstance active = plugin.getDungeonManager().getInstance(dungeon.getId());
            if (active != null && active.hasChest(block)) {
                if (active.getSettings().isAllowChestBreak()) {
                    return;
                }
                event.setCancelled(true);
                plugin.getMessages().send(player, "protection.chest-locked", Map.of("dungeon", dungeon.getId()));
            }
            return;
        }
        instance.handleChestOpen(player, block);
    }
}
