package org.qs.qsdungeons.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * Aislado en su propia clase para que el plugin arranque sin PlaceholderAPI.
 */
public final class PapiHook {

    private PapiHook() {
    }

    public static String apply(Player player, String text) {
        if (player == null || text == null) {
            return text;
        }
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
