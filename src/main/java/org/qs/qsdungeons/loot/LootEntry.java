package org.qs.qsdungeons.loot;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.qs.qsdungeons.storage.ItemSerializer;
import org.qs.qsdungeons.storage.YamlMaps;
import org.qs.qsdungeons.util.ConfigValues;
import org.qs.qsdungeons.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Una entrada de una tabla de loot.
 */
public final class LootEntry {

    private ItemStack item;
    private int minAmount;
    private int maxAmount;
    private double chance = 1.0D;
    private int weight = 1;
    private String message = "";
    private final List<String> commands = new ArrayList<>();

    public LootEntry(ItemStack item) {
        this.item = item;
        this.minAmount = item.getAmount();
        this.maxAmount = item.getAmount();
    }

    public static LootEntry read(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        ConfigurationSection itemSection = section.getConfigurationSection("item");
        // En algunas versiones de Paper el mapa anidado se conserva como
        // Map en vez de convertirse automáticamente en ConfigurationSection.
        // Convertirlo explícitamente evita descartar la entrada al recargar.
        if (itemSection == null) {
            Object rawItem = section.get("item");
            if (rawItem instanceof Map<?, ?> map) {
                itemSection = YamlMaps.toSection(map);
            }
        }
        ItemStack item = ItemSerializer.read(itemSection);
        if (item == null) {
            return null;
        }
        LootEntry entry = new LootEntry(item);
        int[] range = ConfigValues.range(section.getString("amount"), item.getAmount(), item.getAmount());
        entry.minAmount = Math.max(1, range[0]);
        entry.maxAmount = Math.max(entry.minAmount, range[1]);
        entry.chance = section.getDouble("chance", 1.0D);
        if (entry.chance > 1.0D) {
            entry.chance /= 100.0D;
        }
        entry.chance = Math.max(0.0D, Math.min(1.0D, entry.chance));
        entry.weight = Math.max(1, section.getInt("weight", 1));
        entry.message = section.getString("message", "");
        entry.commands.addAll(section.getStringList("commands"));
        return entry;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        ConfigurationSection itemSection = YamlMaps.toSection(Map.of());
        ItemSerializer.write(itemSection, item);
        map.put("item", YamlMaps.toMap(itemSection));
        map.put("amount", minAmount == maxAmount ? String.valueOf(minAmount) : minAmount + "-" + maxAmount);
        map.put("chance", chance);
        map.put("weight", weight);
        if (message != null && !message.isBlank()) {
            map.put("message", message);
        }
        if (!commands.isEmpty()) {
            map.put("commands", commands);
        }
        return map;
    }

    public ItemStack create(Random random) {
        ItemStack result = item.clone();
        int amount = minAmount;
        if (maxAmount > minAmount) {
            amount = minAmount + random.nextInt(maxAmount - minAmount + 1);
        }
        result.setAmount(Math.max(1, amount));
        return result;
    }

    public String describe() {
        if (item == null) {
            return "?";
        }
        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return Text.plain(item.getItemMeta().displayName());
        }
        Material material = item.getType();
        String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    public ItemStack getItem() {
        return item;
    }

    public void setAmount(int minAmount, int maxAmount) {
        this.minAmount = Math.max(1, Math.min(minAmount, maxAmount));
        this.maxAmount = Math.max(this.minAmount, maxAmount);
    }

    public void setChance(double chance) {
        this.chance = Math.max(0.0D, Math.min(1.0D, chance));
    }

    public void setWeight(int weight) {
        this.weight = Math.max(1, weight);
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }

    public int getMinAmount() {
        return minAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public double getChance() {
        return chance;
    }

    public int getWeight() {
        return weight;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getCommands() {
        return commands;
    }
}
