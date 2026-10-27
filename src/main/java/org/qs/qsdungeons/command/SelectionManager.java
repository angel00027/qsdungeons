package org.qs.qsdungeons.command;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.qs.qsdungeons.dungeon.Region;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Guarda las dos esquinas marcadas con /dungeon pos1 y /dungeon pos2.
 */
public final class SelectionManager {

    private final Map<UUID, Location> first = new HashMap<>();
    private final Map<UUID, Location> second = new HashMap<>();

    public void setFirst(Player player, Block block) {
        if (player == null || block == null) {
            return;
        }
        first.put(player.getUniqueId(), new Location(block.getWorld(),
                block.getX(), block.getY(), block.getZ()));
    }

    public void setSecond(Player player, Block block) {
        if (player == null || block == null) {
            return;
        }
        second.put(player.getUniqueId(), new Location(block.getWorld(),
                block.getX(), block.getY(), block.getZ()));
    }

    public Location getFirst(Player player) {
        return player == null ? null : first.get(player.getUniqueId());
    }

    public Location getSecond(Player player) {
        return player == null ? null : second.get(player.getUniqueId());
    }

    public Region getRegion(Player player) {
        return Region.from(getFirst(player), getSecond(player));
    }

    public boolean isComplete(Player player) {
        return getFirst(player) != null && getSecond(player) != null;
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }
        first.remove(player.getUniqueId());
        second.remove(player.getUniqueId());
    }

    public void clearAll() {
        first.clear();
        second.clear();
    }
}
