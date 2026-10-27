package org.qs.qsdungeons.dungeon;

import org.bukkit.Location;
import org.qs.qsdungeons.boss.BossDefinition;
import org.qs.qsdungeons.loot.LootTable;
import org.qs.qsdungeons.spawn.SpawnPoint;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Datos persistentes de una dungeon.
 */
public final class Dungeon {

    private final String id;
    private File folder;
    private String displayName;
    private boolean enabled = true;
    private Region region;
    private Location entry;
    private Location exit;
    private DungeonSettings settings = new DungeonSettings();

    private final Map<String, SpawnPoint> spawns = new LinkedHashMap<>();
    private final Map<String, BossDefinition> bosses = new LinkedHashMap<>();
    private final Map<String, LootTable> lootTables = new LinkedHashMap<>();
    private final Map<String, ChestSpawn> chests = new LinkedHashMap<>();
    private final Map<String, RewardBlock> rewards = new LinkedHashMap<>();

    public Dungeon(String id) {
        this.id = id;
        this.displayName = "<gradient:#8e2de2:#4a00e0><bold>" + id + "</bold></gradient>";
    }

    public String getId() {
        return id;
    }

    public File getFolder() {
        return folder;
    }

    public void setFolder(File folder) {
        this.folder = folder;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public Location getEntry() {
        return entry;
    }

    public void setEntry(Location entry) {
        this.entry = entry;
    }

    public Location getExit() {
        return exit;
    }

    public void setExit(Location exit) {
        this.exit = exit;
    }

    public DungeonSettings getSettings() {
        return settings;
    }

    public void setSettings(DungeonSettings settings) {
        this.settings = settings;
    }

    public Map<String, SpawnPoint> getSpawns() {
        return spawns;
    }

    public Map<String, BossDefinition> getBosses() {
        return bosses;
    }

    public Map<String, LootTable> getLootTables() {
        return lootTables;
    }

    public Map<String, ChestSpawn> getChests() {
        return chests;
    }

    public Map<String, RewardBlock> getRewards() {
        return rewards;
    }

    public SpawnPoint getSpawn(String spawnId) {
        return spawns.get(spawnId);
    }

    public BossDefinition getBoss(String bossId) {
        return bosses.get(bossId);
    }

    public LootTable getLootTable(String lootId) {
        return lootTables.get(lootId);
    }

    public ChestSpawn getChest(String chestId) {
        return chests.get(chestId);
    }

    public RewardBlock getReward(String rewardId) {
        return rewards.get(rewardId);
    }

    public boolean isReady() {
        return enabled && region != null && entry != null;
    }
}
