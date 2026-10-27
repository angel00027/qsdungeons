package org.qs.qsdungeons.loot;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.qs.qsdungeons.storage.YamlMaps;
import org.qs.qsdungeons.util.ConfigValues;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Tabla de loot con sistema de peso, probabilidad y tiradas.
 */
public final class LootTable {

    private String id;
    private int minRolls = 1;
    private int maxRolls = 2;
    private boolean unique = false;
    private final List<LootEntry> entries = new ArrayList<>();
    private boolean unreadableEntries;

    public LootTable(String id) {
        this.id = id;
    }

    public static LootTable read(String id, ConfigurationSection section) {
        LootTable table = new LootTable(id);
        if (section == null) {
            return table;
        }
        int[] rolls = ConfigValues.range(section.getString("rolls"), 1, 2);
        table.minRolls = Math.max(0, rolls[0]);
        table.maxRolls = Math.max(table.minRolls, rolls[1]);
        table.unique = section.getBoolean("unique", false);
        // Bukkit normalmente devuelve esta lista mediante getMapList(), pero
        // algunas versiones de SnakeYAML/Paper conservan los mapas como
        // objetos genéricos. Leer el valor crudo evita que una tabla válida
        // aparezca vacía después de reiniciar el servidor.
        Object rawEntries = section.get("entries");
        boolean hadRawEntries = false;
        if (rawEntries instanceof List<?> list) {
            hadRawEntries = !list.isEmpty();
            for (Object rawEntry : list) {
                if (rawEntry instanceof Map<?, ?> map) {
                    addEntry(table, LootEntry.read(YamlMaps.toSection(map)));
                } else if (rawEntry instanceof ConfigurationSection entrySection) {
                    addEntry(table, LootEntry.read(entrySection));
                }
            }
        } else {
            ConfigurationSection entriesSection = section.getConfigurationSection("entries");
            if (entriesSection != null) {
                hadRawEntries = !entriesSection.getKeys(false).isEmpty();
                for (String key : entriesSection.getKeys(false)) {
                    addEntry(table, LootEntry.read(entriesSection.getConfigurationSection(key)));
                }
            }
        }
        table.unreadableEntries = hadRawEntries && table.entries.isEmpty();
        return table;
    }

    private static void addEntry(LootTable table, LootEntry entry) {
        if (entry != null) {
            table.entries.add(entry);
        }
    }

    public void write(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        section.set("id", id);
        section.set("rolls", minRolls == maxRolls
                ? String.valueOf(minRolls)
                : minRolls + "-" + maxRolls);
        section.set("unique", unique);
        List<Map<String, Object>> list = new ArrayList<>();
        for (LootEntry entry : entries) {
            list.add(entry.toMap());
        }
        section.set("entries", list);
    }

    public List<ItemStack> roll(Random random) {
        List<ItemStack> result = new ArrayList<>();
        for (RolledEntry rolled : rollEntries(random)) {
            result.add(rolled.item());
        }
        return result;
    }

    /**
     * Devuelve las entradas elegidas junto con el item generado. Asi el
     * llamador puede ejecutar solo los mensajes/comandos de las entradas que
     * realmente han salido.
     */
    public List<RolledEntry> rollEntries(Random random) {
        List<RolledEntry> result = new ArrayList<>();
        if (entries.isEmpty() || maxRolls <= 0) {
            return result;
        }
        int rolls = minRolls;
        if (maxRolls > minRolls) {
            rolls = minRolls + random.nextInt(maxRolls - minRolls + 1);
        }
        Set<LootEntry> alreadyPicked = new HashSet<>();
        for (int i = 0; i < rolls; i++) {
            LootEntry picked = pick(random, alreadyPicked);
            if (picked == null) {
                continue;
            }
            result.add(new RolledEntry(picked, picked.create(random)));
            if (unique) {
                alreadyPicked.add(picked);
            }
        }
        return result;
    }

    private LootEntry pick(Random random, Set<LootEntry> alreadyPicked) {
        List<LootEntry> candidates = new ArrayList<>();
        int totalWeight = 0;
        for (LootEntry entry : entries) {
            if (unique && alreadyPicked.contains(entry)) {
                continue;
            }
            if (entry.getChance() <= 0.0D) {
                continue;
            }
            if (random.nextDouble() > entry.getChance()) {
                continue;
            }
            candidates.add(entry);
            totalWeight += entry.getWeight();
        }
        if (candidates.isEmpty()) {
            return null;
        }
        int roll = random.nextInt(totalWeight);
        for (LootEntry entry : candidates) {
            roll -= entry.getWeight();
            if (roll < 0) {
                return entry;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getMinRolls() {
        return minRolls;
    }

    public void setRolls(int minRolls, int maxRolls) {
        this.minRolls = Math.max(0, Math.min(minRolls, maxRolls));
        this.maxRolls = Math.max(this.minRolls, maxRolls);
    }

    public int getMaxRolls() {
        return maxRolls;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public List<LootEntry> getEntries() {
        return entries;
    }

    /**
     * Indica que el archivo tenía entradas, pero alguna incompatibilidad de
     * formato impidió leerlas. Se usa para no sobrescribirlas con entries: [].
     */
    public boolean hasUnreadableEntries() {
        return unreadableEntries;
    }

    public void markEntriesChanged() {
        unreadableEntries = false;
    }

    /**
     * Entrada elegida en una tirada concreta.
     */
    public record RolledEntry(LootEntry entry, ItemStack item) {
    }
}
