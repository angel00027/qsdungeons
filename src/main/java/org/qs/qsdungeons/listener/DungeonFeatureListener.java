package org.qs.qsdungeons.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.instance.DungeonInstance;

import java.util.Map;

/**
 * Llaves: abrir puertas y cofres cerrados con clic derecho.
 */
public final class DungeonFeatureListener implements Listener {

    private final QsDungeons plugin;

    public DungeonFeatureListener(QsDungeons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        DungeonInstance instance = plugin.getDungeonManager().getPlayerInstance(player);
        if (instance == null || !instance.isRunning()) {
            return;
        }
        Block block = event.getClickedBlock();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (instance.tryUseKey(player, block, hand)) {
            event.setCancelled(true);
            return;
        }
        String requiredKey = instance.lockedChestKey(block);
        if (requiredKey != null) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "dungeon.chest-locked", Map.of("key", requiredKey), instance);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getMarkerService().hide(event.getPlayer());
    }
}
