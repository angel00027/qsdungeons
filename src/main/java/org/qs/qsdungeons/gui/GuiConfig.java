package org.qs.qsdungeons.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.config.YamlDefaultsUpdater;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Carga menus.yml. Todos los textos, materiales, slots y lore del GUI se
 * pueden cambiar sin recompilar el plugin.
 */
public class GuiConfig {

    private final QsDungeons plugin;
    private File file;
    private YamlConfiguration yaml;
    private final String resourceName;

    public GuiConfig(QsDungeons plugin) {
        this.plugin = plugin;
        this.resourceName = "menus.yml";
    }

    public GuiConfig(QsDungeons plugin, String resourceName) {
        this.plugin = plugin;
        this.resourceName = resourceName;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), resourceName);
        YamlDefaultsUpdater.update(plugin, resourceName);
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        load();
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    public File getFile() {
        return file;
    }

    public String raw(String path) {
        return yaml == null ? null : yaml.getString(path);
    }

    public boolean getBoolean(String path, boolean fallback) {
        return yaml != null && yaml.getBoolean(path, fallback);
    }

    public int getInt(String path, int fallback) {
        return yaml == null ? fallback : yaml.getInt(path, fallback);
    }

    public String getString(String path, String fallback) {
        return yaml == null ? fallback : yaml.getString(path, fallback);
    }

    public List<String> getStringList(String path) {
        return yaml == null ? List.of() : yaml.getStringList(path);
    }

    public String title(String menu, Map<String, String> placeholders) {
        String raw = raw("menus." + menu + ".title");
        if (raw == null || raw.isBlank()) {
            raw = "<dark_gray>" + menu;
        }
        return apply(raw, placeholders);
    }

    public int size(String menu) {
        int size = getInt("menus." + menu + ".size", 54);
        size = Math.max(9, Math.min(54, size));
        return size - (size % 9);
    }

    public ItemSpec item(String menu, String key) {
        ConfigurationSection section = yaml == null
                ? null
                : yaml.getConfigurationSection("menus." + menu + ".items." + key);
        if (section == null) {
            return null;
        }
        if (!section.getBoolean("enabled", true)) {
            return null;
        }
        return new ItemSpec(
                key,
                section.getInt("slot", -1),
                section.getString("material", "STONE"),
                section.getString("name", ""),
                section.getStringList("lore"),
                Math.max(1, section.getInt("amount", 1)),
                section.getBoolean("glow", false),
                section.contains("custom-model-data") ? section.getInt("custom-model-data") : null,
                section.getString("provider", ""),
                section.getString("item-id", ""));
    }

    public ItemSpec fill(String menu) {
        return item(menu, "fill");
    }

    /**
     * Lee una lista de slots con formato "10-16,19-25,28-34" o "10,11,12".
     */
    public List<Integer> listSlots(String menu) {
        String raw = raw("menus." + menu + ".list-slots");
        if (raw == null || raw.isBlank()) {
            raw = "10-16,19-25,28-34";
        }
        List<Integer> slots = new ArrayList<>();
        for (String part : raw.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                if (token.contains("-")) {
                    String[] range = token.split("-", 2);
                    int from = Integer.parseInt(range[0].trim());
                    int to = Integer.parseInt(range[1].trim());
                    for (int slot = Math.min(from, to); slot <= Math.max(from, to); slot++) {
                        if (slot >= 0 && slot < 54) {
                            slots.add(slot);
                        }
                    }
                } else {
                    int slot = Integer.parseInt(token);
                    if (slot >= 0 && slot < 54) {
                        slots.add(slot);
                    }
                }
            } catch (NumberFormatException ignored) {
                // Slot invalido en menus.yml: se ignora.
            }
        }
        return slots;
    }

    public int perPage(String menu) {
        int configured = getInt("menus." + menu + ".per-page", 0);
        if (configured > 0) {
            return configured;
        }
        int slots = listSlots(menu).size();
        return slots <= 0 ? 14 : slots;
    }

    public String text(String path) {
        return raw(path);
    }

    public String apply(String raw, Map<String, String> placeholders) {
        if (raw == null) {
            return "";
        }
        String result = raw;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return result;
    }

    public Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    /**
     * Definicion de un item del GUI. El slot -1 indica que es una plantilla
     * para listas dinamicas.
     */
    public record ItemSpec(String key, int slot, String material, String name,
                           List<String> lore, int amount, boolean glow,
                           Integer customModelData, String provider, String itemId) {
    }
}
