package org.qs.qsdungeons.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.instance.DungeonInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Expansion de PlaceholderAPI. Se registra solo cuando PlaceholderAPI esta
 * presente.
 *
 * <pre>
 * %qsdungeons_dungeon%          %qsdungeons_state%
 * %qsdungeons_players%          %qsdungeons_max_players%
 * %qsdungeons_time%             %qsdungeons_time_left%
 * %qsdungeons_kills%            %qsdungeons_total%
 * %qsdungeons_progress%         %qsdungeons_progress_bar%
 * %qsdungeons_boss%             %qsdungeons_boss_health%
 * %qsdungeons_boss_percent%     %qsdungeons_dungeons%
 * %qsdungeons_has_dungeon%
 * </pre>
 *
 * Tambien se puede consultar una dungeon concreta:
 * %qsdungeons_&lt;id&gt;_kills%
 */
public final class DungeonPlaceholderExpansion extends PlaceholderExpansion {

    private final QsDungeons plugin;

    public DungeonPlaceholderExpansion(QsDungeons plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "qsdungeons";
    }

    @Override
    public @NotNull String getAuthor() {
        return "QS";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        String key = params == null ? "" : params.toLowerCase(Locale.ROOT);
        if (key.isBlank()) {
            return "";
        }
        if (key.equals("dungeons") || key.equals("list")) {
            List<String> ids = new ArrayList<>();
            for (Dungeon dungeon : plugin.getDungeonManager().getDungeons()) {
                ids.add(dungeon.getId());
            }
            return String.join(",", ids);
        }
        if (key.equals("dungeon_count")) {
            return String.valueOf(plugin.getDungeonManager().getDungeons().size());
        }

        Player player = offlinePlayer == null ? null : offlinePlayer.getPlayer();
        DungeonInstance instance = null;
        String valueKey = key;
        int separator = key.indexOf('_');
        if (separator > 0) {
            String possibleId = key.substring(0, separator);
            if (plugin.getDungeonManager().getDungeon(possibleId) != null) {
                instance = plugin.getDungeonManager().getInstance(possibleId);
                valueKey = key.substring(separator + 1);
            }
        }
        if (instance == null && player != null) {
            instance = plugin.getDungeonManager().getPlayerInstance(player);
        }
        if (key.equals("has_dungeon")) {
            return instance == null ? "no" : "si";
        }

        String resolved = plugin.getPlaceholderService().apply("{" + valueKey + "}", player, instance, null);
        if (resolved == null || resolved.equals("{" + valueKey + "}")) {
            return "";
        }
        return resolved;
    }
}
