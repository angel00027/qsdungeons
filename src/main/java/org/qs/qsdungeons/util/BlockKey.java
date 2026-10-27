package org.qs.qsdungeons.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;

/**
 * Identificador ligero de un bloque, seguro para usar como clave de mapa.
 */
public record BlockKey(String world, int x, int y, int z) {

    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockKey of(Location location) {
        Objects.requireNonNull(location.getWorld(), "location world");
        return new BlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Block toBlock() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : bukkitWorld.getBlockAt(x, y, z);
    }

    @Override
    public String toString() {
        return world + ":" + x + "," + y + "," + z;
    }
}
