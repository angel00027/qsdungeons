package org.qs.qsdungeons.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.qs.qsdungeons.storage.LocationSerializer;
import org.qs.qsdungeons.util.ConfigValues;

import java.util.ArrayList;
import java.util.List;

/**
 * Bloque que entrega una tabla de loot al romperlo.
 */
public final class RewardBlock {

    private String id;
    private Location location;
    private String lootTable = "";
    private Material material = Material.CHEST;
    private String blockData = "";
    private boolean oneTime = true;
    private int respawnSeconds = 0;
    private boolean directGive = false;
    private boolean announce = true;
    private final List<String> commands = new ArrayList<>();

    public RewardBlock(String id) {
        this.id = id;
    }

    public static RewardBlock read(String id, ConfigurationSection section) {
        RewardBlock reward = new RewardBlock(id);
        if (section == null) {
            return reward;
        }
        reward.location = LocationSerializer.read(section.getConfigurationSection("location"));
        reward.lootTable = section.getString("loot-table", "");
        Material material = Material.matchMaterial(section.getString("material", "CHEST"));
        reward.material = material == null || !material.isBlock() ? Material.CHEST : material;
        reward.blockData = section.getString("block-data", "");
        reward.oneTime = section.getBoolean("one-time", true);
        reward.respawnSeconds = ConfigValues.parseDuration(section.getString("respawn-seconds"), 0);
        reward.directGive = section.getBoolean("direct-give", false);
        reward.announce = section.getBoolean("announce", true);
        reward.commands.addAll(section.getStringList("commands"));
        return reward;
    }

    public static RewardBlock capture(String id, Block block) {
        RewardBlock reward = new RewardBlock(id);
        reward.location = block.getLocation();
        reward.material = block.getType();
        reward.blockData = block.getBlockData().getAsString();
        return reward;
    }

    public void write(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        if (location != null) {
            LocationSerializer.write(section, "location", location);
        }
        section.set("loot-table", lootTable);
        section.set("material", material.name());
        if (blockData != null && !blockData.isBlank()) {
            section.set("block-data", blockData);
        }
        section.set("one-time", oneTime);
        section.set("respawn-seconds", respawnSeconds);
        section.set("direct-give", directGive);
        section.set("announce", announce);
        if (!commands.isEmpty()) {
            section.set("commands", commands);
        }
    }

    public BlockData parseBlockData() {
        try {
            if (blockData != null && !blockData.isBlank()) {
                return Bukkit.createBlockData(blockData);
            }
            return material.createBlockData();
        } catch (IllegalArgumentException ignored) {
            return material.createBlockData();
        }
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

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public String getBlockData() {
        return blockData;
    }

    public void setBlockData(String blockData) {
        this.blockData = blockData;
    }

    public boolean isOneTime() {
        return oneTime;
    }

    public void setOneTime(boolean oneTime) {
        this.oneTime = oneTime;
    }

    public int getRespawnSeconds() {
        return respawnSeconds;
    }

    public void setRespawnSeconds(int respawnSeconds) {
        this.respawnSeconds = Math.max(0, respawnSeconds);
    }

    public boolean isDirectGive() {
        return directGive;
    }

    public void setDirectGive(boolean directGive) {
        this.directGive = directGive;
    }

    public boolean isAnnounce() {
        return announce;
    }

    public void setAnnounce(boolean announce) {
        this.announce = announce;
    }

    public List<String> getCommands() {
        return commands;
    }
}
