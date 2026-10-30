package org.qs.qsdungeons.dungeon;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.qs.qsdungeons.storage.LocationSerializer;

/**
 * Punto de control. Cuando un jugador del grupo pasa cerca, todo el grupo
 * reaparece ahi al morir (si leave-on-death esta desactivado).
 */
public final class Checkpoint {

    private final String id;
    private Location location;
    private double radius = 3.0D;

    public Checkpoint(String id) {
        this.id = id;
    }

    public static Checkpoint read(String id, ConfigurationSection section) {
        Checkpoint checkpoint = new Checkpoint(id);
        if (section == null) {
            return checkpoint;
        }
        checkpoint.location = LocationSerializer.read(section, "location");
        checkpoint.radius = Math.max(1.0D, section.getDouble("radius", checkpoint.radius));
        return checkpoint;
    }

    public void write(ConfigurationSection section) {
        if (location != null) {
            LocationSerializer.write(section, "location", location);
        }
        section.set("radius", radius);
    }

    public boolean isInside(Location other) {
        return location != null && other != null && location.getWorld() != null
                && location.getWorld().equals(other.getWorld())
                && location.distanceSquared(other) <= radius * radius;
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = Math.max(1.0D, radius);
    }
}
