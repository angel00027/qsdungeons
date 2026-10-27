package org.qs.qsdungeons.config;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.DungeonSettings;

/**
 * Acceso tipado a config.yml.
 */
public final class ConfigManager {

    private final QsDungeons plugin;

    public ConfigManager(QsDungeons plugin) {
        this.plugin = plugin;
        YamlDefaultsUpdater.update(plugin, "config.yml");
    }

    public void reload() {
        YamlDefaultsUpdater.update(plugin, "config.yml");
        plugin.reloadConfig();
    }

    public DungeonSettings getDefaultSettings() {
        DungeonSettings defaults = new DungeonSettings();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("defaults");
        return DungeonSettings.read(section, defaults);
    }

    public String getDefaultWorld() {
        return plugin.getConfig().getString("settings.default-world", "world");
    }

    public boolean isDebug() {
        return plugin.getConfig().getBoolean("settings.debug", false);
    }

    public boolean isGenerateExample() {
        return plugin.getConfig().getBoolean("settings.generate-example-dungeon", true);
    }

    public int getBossBarUpdateTicks() {
        return Math.max(5, plugin.getConfig().getInt("settings.bossbar-update-ticks", 20));
    }

    public double getDefaultNearRadius() {
        return plugin.getConfig().getDouble("settings.default-near-radius", 64.0D);
    }

    public void playSound(Player player, String key) {
        if (player == null) {
            return;
        }
        String raw = plugin.getConfig().getString("sounds." + key);
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            Sound sound = resolveSound(raw);
            if (sound == null) {
                throw new IllegalArgumentException("Sonido desconocido: " + raw);
            }
            player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
        } catch (IllegalArgumentException ignored) {
            if (isDebug()) {
                plugin.getLogger().warning("Sonido invalido en config: " + raw);
            }
        }
    }

    /**
     * Acepta tanto claves de registro ({@code minecraft:block.portal.travel})
     * como los nombres clasicos de Bukkit ({@code BLOCK_PORTAL_TRAVEL}).
     */
    @SuppressWarnings({"deprecation", "removal"})
    private Sound resolveSound(String raw) {
        String value = raw.trim();
        if (value.contains(":") || value.contains(".")) {
            NamespacedKey key = NamespacedKey.fromString(value.toLowerCase(java.util.Locale.ROOT));
            return key == null ? null : Registry.SOUNDS.get(key);
        }
        return Sound.valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }

    public boolean fillChestsOnStart() {
        return plugin.getConfig().getBoolean("loot.fill-chests-on-start", true);
    }

    public boolean clearChestsOnStart() {
        return plugin.getConfig().getBoolean("loot.clear-chests-on-start", true);
    }

    public boolean announceReward() {
        return plugin.getConfig().getBoolean("loot.announce-reward", true);
    }

    public boolean dropNaturally() {
        return plugin.getConfig().getBoolean("loot.drop-naturally", false);
    }

    public boolean clearVanillaDrops() {
        return plugin.getConfig().getBoolean("loot.clear-vanilla-drops", true);
    }

    public String getExportsFolderName() {
        return plugin.getConfig().getString("import-export.exports-folder", "exports");
    }

    public String getSchematicsFolderName() {
        return plugin.getConfig().getString("import-export.schematics-folder", "schematics");
    }

    public boolean includeEntities() {
        return plugin.getConfig().getBoolean("import-export.include-entities", true);
    }

    public boolean ignoreAirOnPaste() {
        return plugin.getConfig().getBoolean("import-export.ignore-air-on-paste", false);
    }

    public long getMaxUnzipBytes() {
        return plugin.getConfig().getLong("import-export.max-unzip-bytes", 50L * 1024L * 1024L);
    }

    public String getTimeFormat() {
        return plugin.getConfig().getString("placeholders.time-format", "mm:ss");
    }

    public String getProgressBarSymbol() {
        return plugin.getConfig().getString("placeholders.progress-bar.symbol", "|");
    }

    public int getProgressBarLength() {
        return Math.max(3, plugin.getConfig().getInt("placeholders.progress-bar.length", 20));
    }

    public String getProgressBarCompletedColor() {
        return plugin.getConfig().getString("placeholders.progress-bar.completed-color", "<green>");
    }

    public String getProgressBarRemainingColor() {
        return plugin.getConfig().getString("placeholders.progress-bar.remaining-color", "<dark_gray>");
    }
}
