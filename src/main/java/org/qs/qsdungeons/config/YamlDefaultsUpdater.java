package org.qs.qsdungeons.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.qs.qsdungeons.QsDungeons;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;

/**
 * Completa archivos YAML existentes con las claves nuevas del recurso incluido
 * en el plugin. Nunca reemplaza valores que el servidor ya haya configurado.
 */
public final class YamlDefaultsUpdater {

    private YamlDefaultsUpdater() {
    }

    public static boolean update(QsDungeons plugin, String resourceName) {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
            return true;
        }

        try (InputStream stream = plugin.getResource(resourceName)) {
            if (stream == null) {
                plugin.getLogger().warning("No existe el recurso YAML " + resourceName + " dentro del plugin.");
                return false;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
            boolean changed = mergeMissing(current, defaults, "");
            if (changed) {
                current.save(file);
                plugin.getLogger().info("Se agregaron claves nuevas a " + resourceName + ".");
            }
            return changed;
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "No se pudo actualizar automaticamente " + resourceName, exception);
            return false;
        }
    }

    private static boolean mergeMissing(ConfigurationSection target,
                                        ConfigurationSection defaults, String path) {
        boolean changed = false;
        for (Map.Entry<String, Object> entry : defaults.getValues(false).entrySet()) {
            String key = entry.getKey();
            String fullPath = path.isEmpty() ? key : path + "." + key;
            Object defaultValue = entry.getValue();
            if (defaultValue instanceof ConfigurationSection defaultSection) {
                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (targetSection == null) {
                    targetSection = target.createSection(key);
                    changed = true;
                }
                changed |= mergeMissing(targetSection, defaultSection, fullPath);
            } else if (!target.contains(key)) {
                target.set(key, defaultValue);
                changed = true;
            }
        }
        return changed;
    }
}
