package org.qs.qsdungeons.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Convierte mapas YAML anidados a ConfigurationSection y viceversa.
 * Util para guardar listas de entradas de loot de forma bonita.
 */
public final class YamlMaps {

    private YamlMaps() {
    }

    public static ConfigurationSection toSection(Map<?, ?> map) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (map != null) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                yaml.set(String.valueOf(entry.getKey()), deepValue(entry.getValue()));
            }
        }
        return yaml;
    }

    public static Map<String, Object> toMap(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            result.put(key, deepValue(value));
        }
        return result;
    }

    private static Object deepValue(Object value) {
        if (value instanceof ConfigurationSection section) {
            return toMap(section);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object element : list) {
                copy.add(deepValue(element));
            }
            return copy;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepValue(entry.getValue()));
            }
            return copy;
        }
        return value;
    }
}
