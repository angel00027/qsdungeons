package org.qs.qsdungeons.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.qs.qsdungeons.QsDungeons;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Puente opcional con MythicMobs. Solo se instancia si el plugin esta presente.
 */
public final class MythicMobsHook {

    private final QsDungeons plugin;
    private final boolean available;

    public MythicMobsHook(QsDungeons plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * IDs de los mobs definidos en MythicMobs, para autocompletado y GUI.
     */
    public List<String> listMobIds() {
        if (!available) {
            return List.of();
        }
        try {
            Collection<String> names = io.lumine.mythic.bukkit.MythicBukkit.inst()
                    .getMobManager()
                    .getMobNames();
            List<String> result = new ArrayList<>(names);
            result.sort(String.CASE_INSENSITIVE_ORDER);
            return result;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("No se pudieron listar los mobs de MythicMobs: " + throwable.getMessage());
            return List.of();
        }
    }

    public Entity spawn(String mobId, Location location) {
        if (!available) {
            return null;
        }
        try {
            return io.lumine.mythic.bukkit.MythicBukkit.inst()
                    .getAPIHelper()
                    .spawnMythicMob(mobId, location);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("No se pudo invocar el mob de MythicMobs '" + mobId + "': " + throwable.getMessage());
            return null;
        }
    }

    public boolean isMythic(Entity entity) {
        if (!available || entity == null) {
            return false;
        }
        try {
            return io.lumine.mythic.bukkit.MythicBukkit.inst().getAPIHelper().isMythicMob(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
