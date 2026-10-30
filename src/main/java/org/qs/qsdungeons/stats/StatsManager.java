package org.qs.qsdungeons.stats;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.qs.qsdungeons.QsDungeons;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Estadisticas por jugador y dungeon, y ranking de mejores tiempos.
 * Se guardan en plugins/QSDungeons/data/stats.yml.
 */
public final class StatsManager {

    public static final String COMPLETIONS = "completions";
    public static final String FAILS = "fails";
    public static final String DEATHS = "deaths";
    public static final String KILLS = "kills";
    public static final String BOSS_KILLS = "boss-kills";
    public static final String BEST_TIME = "best-time";

    private final QsDungeons plugin;
    private final Map<UUID, PlayerStats> players = new HashMap<>();
    private File file;
    private boolean dirty;

    public StatsManager(QsDungeons plugin) {
        this.plugin = plugin;
    }

    public void load() {
        players.clear();
        file = new File(plugin.getRepository().getDataFolder(), "stats.yml");
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String rawUuid : root.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(rawUuid);
            if (section == null) {
                continue;
            }
            PlayerStats stats = new PlayerStats(section.getString("name", "?"));
            ConfigurationSection dungeons = section.getConfigurationSection("dungeons");
            if (dungeons != null) {
                for (String dungeonId : dungeons.getKeys(false)) {
                    ConfigurationSection values = dungeons.getConfigurationSection(dungeonId);
                    if (values == null) {
                        continue;
                    }
                    Map<String, Long> map = new HashMap<>();
                    for (String key : values.getKeys(false)) {
                        map.put(key, values.getLong(key));
                    }
                    stats.dungeons.put(dungeonId, map);
                }
            }
            players.put(uuid, stats);
        }
    }

    public void save() {
        if (!dirty || file == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerStats> entry : players.entrySet()) {
            String base = "players." + entry.getKey();
            yaml.set(base + ".name", entry.getValue().name);
            for (Map.Entry<String, Map<String, Long>> dungeon : entry.getValue().dungeons.entrySet()) {
                for (Map.Entry<String, Long> value : dungeon.getValue().entrySet()) {
                    yaml.set(base + ".dungeons." + dungeon.getKey() + "." + value.getKey(), value.getValue());
                }
            }
        }
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron guardar las estadisticas", exception);
        }
    }

    // ---------------------------------------------------------------------
    // Registro
    // ---------------------------------------------------------------------

    public void add(UUID uuid, String name, String dungeonId, String key, long amount) {
        Map<String, Long> map = values(uuid, name, dungeonId);
        map.merge(key, amount, Long::sum);
        dirty = true;
    }

    /**
     * Suma una partida completada y guarda el tiempo si es su mejor marca.
     *
     * @return true si es un nuevo record personal.
     */
    public boolean recordCompletion(UUID uuid, String name, String dungeonId, long seconds) {
        Map<String, Long> map = values(uuid, name, dungeonId);
        map.merge(COMPLETIONS, 1L, Long::sum);
        Long best = map.get(BEST_TIME);
        dirty = true;
        if (best == null || best <= 0 || seconds < best) {
            map.put(BEST_TIME, seconds);
            return true;
        }
        return false;
    }

    private Map<String, Long> values(UUID uuid, String name, String dungeonId) {
        PlayerStats stats = players.computeIfAbsent(uuid, ignored -> new PlayerStats(name));
        if (name != null) {
            stats.name = name;
        }
        return stats.dungeons.computeIfAbsent(dungeonId.toLowerCase(Locale.ROOT), ignored -> new HashMap<>());
    }

    // ---------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------

    public long get(UUID uuid, String dungeonId, String key) {
        PlayerStats stats = players.get(uuid);
        if (stats == null) {
            return 0L;
        }
        Map<String, Long> map = stats.dungeons.get(dungeonId.toLowerCase(Locale.ROOT));
        return map == null ? 0L : map.getOrDefault(key, 0L);
    }

    public long total(UUID uuid, String key) {
        PlayerStats stats = players.get(uuid);
        if (stats == null) {
            return 0L;
        }
        long total = 0L;
        for (Map<String, Long> map : stats.dungeons.values()) {
            total += map.getOrDefault(key, 0L);
        }
        return total;
    }

    public boolean hasCompleted(UUID uuid, String dungeonId) {
        return get(uuid, dungeonId, COMPLETIONS) > 0;
    }

    /**
     * Mejores tiempos de una dungeon, de menor a mayor.
     */
    public List<Entry> top(String dungeonId, int limit) {
        String id = dungeonId.toLowerCase(Locale.ROOT);
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<UUID, PlayerStats> entry : players.entrySet()) {
            Map<String, Long> map = entry.getValue().dungeons.get(id);
            if (map == null) {
                continue;
            }
            long best = map.getOrDefault(BEST_TIME, 0L);
            if (best > 0) {
                entries.add(new Entry(entry.getKey(), entry.getValue().name, best,
                        map.getOrDefault(COMPLETIONS, 0L)));
            }
        }
        entries.sort(Comparator.comparingLong(Entry::seconds));
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    public UUID findByName(String name) {
        for (Map.Entry<UUID, PlayerStats> entry : players.entrySet()) {
            if (entry.getValue().name != null && entry.getValue().name.equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public String nameOf(UUID uuid) {
        PlayerStats stats = players.get(uuid);
        return stats == null ? "?" : stats.name;
    }

    public record Entry(UUID uuid, String name, long seconds, long completions) {
    }

    private static final class PlayerStats {
        private String name;
        private final Map<String, Map<String, Long>> dungeons = new HashMap<>();

        private PlayerStats(String name) {
            this.name = name == null ? "?" : name;
        }
    }
}
