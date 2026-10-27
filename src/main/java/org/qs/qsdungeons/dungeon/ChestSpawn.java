package org.qs.qsdungeons.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.qs.qsdungeons.storage.LocationSerializer;

/**
 * Cofre que se rellena con una tabla de loot.
 */
public final class ChestSpawn {

    private String id;
    private Location location;
    private String lootTable = "";
    private String blockData = "";
    private int refillSeconds = 0;
    private boolean enabled = true;

    public ChestSpawn(String id) {
        this.id = id;
    }

    public static ChestSpawn read(String id, ConfigurationSection section) {
        ChestSpawn chest = new ChestSpawn(id);
        if (section == null) {
            return chest;
        }
        chest.location = LocationSerializer.read(section.getConfigurationSection("location"));
        chest.lootTable = section.getString("loot-table", "");
        chest.blockData = section.getString("block-data", "");
        chest.refillSeconds = Math.max(0, section.getInt("refill-seconds", 0));
        chest.enabled = section.getBoolean("enabled", true);
        return chest;
    }

    public static ChestSpawn capture(String id, Block block) {
        ChestSpawn chest = new ChestSpawn(id);
        chest.location = block.getLocation();
        chest.blockData = block.getBlockData().getAsString();
        return chest;
    }

    public void write(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        if (location != null) {
            LocationSerializer.write(section, "location", location);
        }
        section.set("loot-table", lootTable);
        if (blockData != null && !blockData.isBlank()) {
            section.set("block-data", blockData);
        }
        section.set("refill-seconds", refillSeconds);
        section.set("enabled", enabled);
    }

    public BlockData parseBlockData() {
        try {
            if (blockData != null && !blockData.isBlank()) {
                return Bukkit.createBlockData(blockData);
            }
        } catch (IllegalArgumentException ignored) {
            // Si el block-data guardado ya no es valido se usa un cofre normal.
        }
        return org.bukkit.Material.CHEST.createBlockData();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getLootTable() {
        return lootTable;
    }

    public void setLootTable(String lootTable) {
        this.lootTable = lootTable;
    }

    public String getBlockData() {
        return blockData;
    }

    public void setBlockData(String blockData) {
        this.blockData = blockData;
    }

    public int getRefillSeconds() {
        return refillSeconds;
    }

    public void setRefillSeconds(int refillSeconds) {
        this.refillSeconds = Math.max(0, refillSeconds);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
