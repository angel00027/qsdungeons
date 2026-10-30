package org.qs.qsdungeons.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.instance.DungeonInstance;
import org.qs.qsdungeons.stats.StatsManager;

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
 * Estadisticas del jugador (todas las dungeons):
 * %qsdungeons_stats_completions%  %qsdungeons_stats_fails%  %qsdungeons_stats_deaths%
 * %qsdungeons_stats_kills%        %qsdungeons_stats_boss_kills%
 *
 * Estadisticas y ranking de una dungeon:
 * %qsdungeons_&lt;id&gt;_stats_completions%  %qsdungeons_&lt;id&gt;_stats_best_time%
 * %qsdungeons_&lt;id&gt;_record%  %qsdungeons_&lt;id&gt;_record_holder%
 * %qsdungeons_&lt;id&gt;_top_&lt;n&gt;_name%  %qsdungeons_&lt;id&gt;_top_&lt;n&gt;_time%
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

        String stats = stats(offlinePlayer, key);
        if (stats != null) {
            return stats;
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

    /**
     * Placeholders de estadisticas y ranking. Devuelve null si la clave no es
     * de estadisticas.
     */
    private String stats(OfflinePlayer player, String key) {
        StatsManager stats = plugin.getStatsManager();
        if (key.startsWith("stats_")) {
            if (player == null) {
                return "0";
            }
            return String.valueOf(stats.total(player.getUniqueId(), statKey(key.substring(6))));
        }
        int separator = key.indexOf('_');
        if (separator <= 0) {
            return null;
        }
        String dungeonId = key.substring(0, separator);
        if (plugin.getDungeonManager().getDungeon(dungeonId) == null) {
            return null;
        }
        String rest = key.substring(separator + 1);
        if (rest.startsWith("stats_")) {
            if (player == null) {
                return "0";
            }
            String field = statKey(rest.substring(6));
            long value = stats.get(player.getUniqueId(), dungeonId, field);
            return field.equals(StatsManager.BEST_TIME)
                    ? (value <= 0 ? "-" : plugin.getPlaceholderService().formatDuration(value))
                    : String.valueOf(value);
        }
        if (rest.equals("record") || rest.equals("record_holder")) {
            List<StatsManager.Entry> top = stats.top(dungeonId, 1);
            if (top.isEmpty()) {
                return "-";
            }
            return rest.equals("record")
                    ? plugin.getPlaceholderService().formatDuration(top.get(0).seconds())
                    : top.get(0).name();
        }
        if (rest.startsWith("top_")) {
            String[] parts = rest.split("_");
            if (parts.length < 3) {
                return "";
            }
            int position;
            try {
                position = Integer.parseInt(parts[1]);
            } catch (NumberFormatException exception) {
                return "";
            }
            List<StatsManager.Entry> top = stats.top(dungeonId, Math.max(1, position));
            if (position < 1 || position > top.size()) {
                return "-";
            }
            StatsManager.Entry entry = top.get(position - 1);
            return switch (parts[2]) {
                case "name" -> entry.name();
                case "time" -> plugin.getPlaceholderService().formatDuration(entry.seconds());
                case "completions" -> String.valueOf(entry.completions());
                default -> "";
            };
        }
        return null;
    }

    private static String statKey(String raw) {
        return switch (raw) {
            case "boss_kills" -> StatsManager.BOSS_KILLS;
            case "best_time" -> StatsManager.BEST_TIME;
            default -> raw;
        };
    }
}
