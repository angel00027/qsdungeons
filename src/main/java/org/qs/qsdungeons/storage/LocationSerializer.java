package org.qs.qsdungeons.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Serializa ubicaciones dentro de archivos YAML de forma legible.
 */
public final class LocationSerializer {

    private LocationSerializer() {
    }

    public static Location read(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw", 0.0D);
        float pitch = (float) section.getDouble("pitch", 0.0D);
        return new Location(world, x, y, z, yaw, pitch);
    }

    public static Location read(ConfigurationSection parent, String path) {
        return read(parent == null ? null : parent.getConfigurationSection(path));
    }

    public static void write(ConfigurationSection section, Location location) {
        if (section == null || location == null) {
            return;
        }
        section.set("world", location.getWorld() == null ? "world" : location.getWorld().getName());
        section.set("x", round(location.getX()));
        section.set("y", round(location.getY()));
        section.set("z", round(location.getZ()));
        section.set("yaw", round(location.getYaw()));
        section.set("pitch", round(location.getPitch()));
    }

    public static void write(ConfigurationSection parent, String path, Location location) {
        if (parent == null || location == null) {
            return;
        }
        write(parent.createSection(path), location);
    }

    public static Location center(Location location) {
        if (location == null) {
            return null;
        }
        Location copy = location.clone();
        copy.setX(copy.getBlockX() + 0.5D);
        copy.setZ(copy.getBlockZ() + 0.5D);
        return copy;
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
