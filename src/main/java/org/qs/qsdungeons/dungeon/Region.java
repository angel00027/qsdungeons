package org.qs.qsdungeons.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Region cuboide de una dungeon.
 */
public final class Region {

    private final String worldName;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public Region(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.worldName = worldName;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public static Region from(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return null;
        }
        if (!first.getWorld().equals(second.getWorld())) {
            return null;
        }
        return new Region(first.getWorld().getName(),
                first.getBlockX(), first.getBlockY(), first.getBlockZ(),
                second.getBlockX(), second.getBlockY(), second.getBlockZ());
    }

    public static Region read(ConfigurationSection parent, String path) {
        if (parent == null) {
            return null;
        }
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            return null;
        }
        ConfigurationSection min = section.getConfigurationSection("min");
        ConfigurationSection max = section.getConfigurationSection("max");
        if (min == null || max == null) {
            return null;
        }
        String worldName = section.getString("world", parent.getString("world", "world"));
        return new Region(worldName,
                min.getInt("x"), min.getInt("y"), min.getInt("z"),
                max.getInt("x"), max.getInt("y"), max.getInt("z"));
    }

    public void write(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.createSection(path);
        section.set("world", worldName);
        section.set("min.x", minX);
        section.set("min.y", minY);
        section.set("min.z", minZ);
        section.set("max.x", maxX);
        section.set("max.y", maxY);
        section.set("max.z", maxZ);
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return contains(location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean contains(String world, int x, int y, int z) {
        return worldName.equalsIgnoreCase(world)
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public World getBukkitWorld() {
        return Bukkit.getWorld(worldName);
    }

    public Location getCenter() {
        World world = getBukkitWorld();
        if (world == null) {
            return null;
        }
        return new Location(world,
                (minX + maxX) / 2.0D + 0.5D,
                (minY + maxY) / 2.0D + 0.5D,
                (minZ + maxZ) / 2.0D + 0.5D);
    }

    public String describe() {
        return worldName + " [" + minX + ", " + minY + ", " + minZ + "] -> ["
                + maxX + ", " + maxY + ", " + maxZ + "]";
    }

    public long getVolume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }
}
