package org.qs.qsdungeons.dungeon;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Configuracion de una bossbar. Se usa tanto en dungeons como en jefes.
 */
public final class BossBarSettings {

    private boolean enabled = true;
    private String title = "<gradient:#8e2de2:#4a00e0><bold>{dungeon}</bold></gradient> <dark_gray>| <white>{kills}<gray>/<white>{total}</white>";
    private String color = "PURPLE";
    private String style = "PROGRESS";
    private double radius = 64.0D;

    public BossBarSettings() {
    }

    public BossBarSettings(BossBarSettings other) {
        if (other != null) {
            this.enabled = other.enabled;
            this.title = other.title;
            this.color = other.color;
            this.style = other.style;
            this.radius = other.radius;
        }
    }

    public static BossBarSettings read(ConfigurationSection section, BossBarSettings fallback) {
        BossBarSettings result = new BossBarSettings(fallback);
        if (section == null) {
            return result;
        }
        result.enabled = section.getBoolean("enabled", result.enabled);
        result.title = section.getString("title", result.title);
        result.color = section.getString("color", result.color);
        result.style = section.getString("style", result.style);
        result.radius = section.getDouble("radius", result.radius);
        return result;
    }

    public void write(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        section.set("enabled", enabled);
        section.set("title", title);
        section.set("color", color);
        section.set("style", style);
        section.set("radius", radius);
    }

    public BossBar.Color parseColor() {
        try {
            return BossBar.Color.valueOf(color.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return BossBar.Color.PURPLE;
        }
    }

    public BossBar.Overlay parseOverlay() {
        String normalized = style == null ? "PROGRESS" : style.trim().toUpperCase();
        if (normalized.equals("SOLID")) {
            return BossBar.Overlay.PROGRESS;
        }
        try {
            return BossBar.Overlay.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }
}
