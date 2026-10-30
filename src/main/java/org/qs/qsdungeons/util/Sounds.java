package org.qs.qsdungeons.util;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Reproduce sonidos por su clave ("entity.wither.spawn", "minecraft:music_disc.pigstep"
 * o sonidos de un resource pack como "nexo:boss_theme").
 */
public final class Sounds {

    private Sounds() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        // Permite tambien el formato de enum de Bukkit: ENTITY_WITHER_SPAWN.
        if (!key.contains(".") && !key.contains(":") && key.contains("_")) {
            key = key.replace('_', '.');
        }
        return key;
    }

    public static void play(Player player, String raw, SoundCategory category, float volume, float pitch) {
        play(player, player == null ? null : player.getLocation(), raw, category, volume, pitch);
    }

    public static void play(Player player, Location location, String raw, SoundCategory category,
                            float volume, float pitch) {
        String key = normalize(raw);
        if (player == null || location == null || key.isEmpty()) {
            return;
        }
        player.playSound(location, key, category, volume, pitch);
    }

    public static void stop(Player player, String raw, SoundCategory category) {
        String key = normalize(raw);
        if (player != null && !key.isEmpty()) {
            player.stopSound(key, category);
        }
    }
}
