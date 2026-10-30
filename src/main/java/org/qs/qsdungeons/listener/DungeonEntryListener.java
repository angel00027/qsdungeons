package org.qs.qsdungeons.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.qs.qsdungeons.QsDungeons;

/**
 * Detecta entrada/salida por region, muerte, respawn y desconexion.
 */
public final class DungeonEntryListener implements Listener {

    private final QsDungeons plugin;

    public DungeonEntryListener(QsDungeons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        plugin.getDungeonManager().handleMove(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getDungeonManager().handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getDungeonManager().handleDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        org.bukkit.Location location = plugin.getDungeonManager().handleRespawn(event.getPlayer());
        if (location != null) {
            event.setRespawnLocation(location);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        plugin.getDungeonManager().handleMove(event.getPlayer(), event.getFrom(), event.getTo());
    }
}
