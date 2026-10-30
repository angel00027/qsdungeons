package org.qs.qsdungeons.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.boss.BossDefinition;
import org.qs.qsdungeons.dungeon.Checkpoint;
import org.qs.qsdungeons.dungeon.ChestSpawn;
import org.qs.qsdungeons.dungeon.DungeonDoor;
import org.qs.qsdungeons.dungeon.DungeonKey;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.DungeonSettings;
import org.qs.qsdungeons.dungeon.Region;
import org.qs.qsdungeons.dungeon.RewardBlock;
import org.qs.qsdungeons.loot.LootTable;
import org.qs.qsdungeons.spawn.SpawnPoint;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Carga y guarda dungeons desde plugins/QSdungeons/dungeons/&lt;id&gt;/
 */
public final class DungeonRepository {

    private final QsDungeons plugin;
    private final File dungeonsFolder;

    public DungeonRepository(QsDungeons plugin) {
        this.plugin = plugin;
        this.dungeonsFolder = new File(plugin.getDataFolder(), "dungeons");
        ensureFolder(dungeonsFolder);
    }

    public QsDungeons getPlugin() {
        return plugin;
    }

    public Map<String, Dungeon> loadAll() {
        Map<String, Dungeon> result = new LinkedHashMap<>();
        File[] folders = dungeonsFolder.listFiles(File::isDirectory);
        if (folders == null) {
            return result;
        }
        for (File folder : folders) {
            Dungeon dungeon = load(folder.getName());
            if (dungeon != null) {
                result.put(dungeon.getId(), dungeon);
            }
        }
        return result;
    }

    public Dungeon load(String id) {
        File folder = getFolder(id);
        File file = new File(folder, "dungeon.yml");
        if (!file.isFile()) {
            return null;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            Dungeon dungeon = new Dungeon(id);
            dungeon.setFolder(folder);
            dungeon.setDisplayName(yaml.getString("display-name", dungeon.getDisplayName()));
            dungeon.setEnabled(yaml.getBoolean("enabled", true));
            dungeon.setRegion(Region.read(yaml, "region"));
            dungeon.setEntry(LocationSerializer.read(yaml, "entry"));
            dungeon.setExit(LocationSerializer.read(yaml, "exit"));
            if (dungeon.getExit() == null) {
                dungeon.setExit(dungeon.getEntry());
            }
            dungeon.setWarp(LocationSerializer.read(yaml, "warp"));
            DungeonSettings defaults = plugin.getConfigManager().getDefaultSettings();
            dungeon.setSettings(DungeonSettings.read(yaml.getConfigurationSection("settings"), defaults));

            ConfigurationSection spawns = yaml.getConfigurationSection("spawns");
            if (spawns != null) {
                for (String spawnId : spawns.getKeys(false)) {
                    SpawnPoint spawn = SpawnPoint.read(spawnId, spawns.getConfigurationSection(spawnId));
                    dungeon.getSpawns().put(spawnId, spawn);
                }
            }
            ConfigurationSection chests = yaml.getConfigurationSection("chests");
            if (chests != null) {
                for (String chestId : chests.getKeys(false)) {
                    ChestSpawn chest = ChestSpawn.read(chestId, chests.getConfigurationSection(chestId));
                    dungeon.getChests().put(chestId, chest);
                }
            }
            ConfigurationSection rewards = yaml.getConfigurationSection("rewards");
            if (rewards != null) {
                for (String rewardId : rewards.getKeys(false)) {
                    RewardBlock reward = RewardBlock.read(rewardId, rewards.getConfigurationSection(rewardId));
                    dungeon.getRewards().put(rewardId, reward);
                }
            }
            ConfigurationSection doors = yaml.getConfigurationSection("doors");
            if (doors != null) {
                for (String doorId : doors.getKeys(false)) {
                    dungeon.getDoors().put(doorId, DungeonDoor.read(doorId, doors.getConfigurationSection(doorId)));
                }
            }
            ConfigurationSection keys = yaml.getConfigurationSection("keys");
            if (keys != null) {
                for (String keyId : keys.getKeys(false)) {
                    dungeon.getKeys().put(keyId, DungeonKey.read(keyId, keys.getConfigurationSection(keyId)));
                }
            }
            ConfigurationSection checkpoints = yaml.getConfigurationSection("checkpoints");
            if (checkpoints != null) {
                for (String checkpointId : checkpoints.getKeys(false)) {
                    dungeon.getCheckpoints().put(checkpointId,
                            Checkpoint.read(checkpointId, checkpoints.getConfigurationSection(checkpointId)));
                }
            }

            loadBosses(dungeon);
            loadLootTables(dungeon);
            return dungeon;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo cargar la dungeon " + id, exception);
            return null;
        }
    }

    private void loadBosses(Dungeon dungeon) {
        File folder = new File(dungeon.getFolder(), "bosses");
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String id = stripExtension(file.getName());
                BossDefinition boss = BossDefinition.read(id, yaml);
                dungeon.getBosses().put(id, boss);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Jefe invalido: " + file.getName(), exception);
            }
        }
    }

    private void loadLootTables(Dungeon dungeon) {
        File folder = new File(dungeon.getFolder(), "loot");
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String id = stripExtension(file.getName());
                LootTable table = LootTable.read(id, yaml);
                dungeon.getLootTables().put(id, table);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Tabla de loot invalida: " + file.getName(), exception);
            }
        }
    }

    public void save(Dungeon dungeon) {
        if (dungeon == null) {
            return;
        }
        File folder = getFolder(dungeon.getId());
        ensureFolder(folder);
        ensureFolder(new File(folder, "bosses"));
        ensureFolder(new File(folder, "loot"));
        ensureFolder(new File(folder, "schematics"));
        dungeon.setFolder(folder);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", dungeon.getId());
        yaml.set("display-name", dungeon.getDisplayName());
        yaml.set("enabled", dungeon.isEnabled());
        yaml.set("world", dungeon.getRegion() == null
                ? plugin.getConfigManager().getDefaultWorld()
                : dungeon.getRegion().getWorldName());
        if (dungeon.getRegion() != null) {
            dungeon.getRegion().write(yaml, "region");
        }
        if (dungeon.getEntry() != null) {
            LocationSerializer.write(yaml, "entry", dungeon.getEntry());
        }
        if (dungeon.getExit() != null) {
            LocationSerializer.write(yaml, "exit", dungeon.getExit());
        }
        if (dungeon.getWarp() != null) {
            LocationSerializer.write(yaml, "warp", dungeon.getWarp());
        }
        dungeon.getSettings().write(yaml.createSection("settings"));

        ConfigurationSection spawns = yaml.createSection("spawns");
        for (Map.Entry<String, SpawnPoint> entry : dungeon.getSpawns().entrySet()) {
            entry.getValue().write(spawns.createSection(entry.getKey()));
        }
        ConfigurationSection chests = yaml.createSection("chests");
        for (Map.Entry<String, ChestSpawn> entry : dungeon.getChests().entrySet()) {
            entry.getValue().write(chests.createSection(entry.getKey()));
        }
        ConfigurationSection rewards = yaml.createSection("rewards");
        for (Map.Entry<String, RewardBlock> entry : dungeon.getRewards().entrySet()) {
            entry.getValue().write(rewards.createSection(entry.getKey()));
        }
        ConfigurationSection doors = yaml.createSection("doors");
        for (Map.Entry<String, DungeonDoor> entry : dungeon.getDoors().entrySet()) {
            entry.getValue().write(doors.createSection(entry.getKey()));
        }
        ConfigurationSection keys = yaml.createSection("keys");
        for (Map.Entry<String, DungeonKey> entry : dungeon.getKeys().entrySet()) {
            entry.getValue().write(keys.createSection(entry.getKey()));
        }
        ConfigurationSection checkpoints = yaml.createSection("checkpoints");
        for (Map.Entry<String, Checkpoint> entry : dungeon.getCheckpoints().entrySet()) {
            entry.getValue().write(checkpoints.createSection(entry.getKey()));
        }

        saveYaml(yaml, new File(folder, "dungeon.yml"));
        saveBosses(dungeon);
        saveLootTables(dungeon);
    }

    private void saveBosses(Dungeon dungeon) {
        File folder = new File(dungeon.getFolder(), "bosses");
        ensureFolder(folder);
        for (Map.Entry<String, BossDefinition> entry : dungeon.getBosses().entrySet()) {
            YamlConfiguration yaml = new YamlConfiguration();
            entry.getValue().write(yaml);
            saveYaml(yaml, new File(folder, entry.getKey() + ".yml"));
        }
    }

    private void saveLootTables(Dungeon dungeon) {
        File folder = new File(dungeon.getFolder(), "loot");
        ensureFolder(folder);
        for (Map.Entry<String, LootTable> entry : dungeon.getLootTables().entrySet()) {
            if (entry.getValue().hasUnreadableEntries()) {
                plugin.getLogger().warning("Se conserva la tabla de loot " + entry.getKey()
                        + " porque contiene entradas que no se pudieron leer.");
                continue;
            }
            YamlConfiguration yaml = new YamlConfiguration();
            entry.getValue().write(yaml);
            saveYaml(yaml, new File(folder, entry.getKey() + ".yml"));
        }
    }

    public void saveBoss(Dungeon dungeon, BossDefinition boss) {
        dungeon.getBosses().put(boss.getId(), boss);
        save(dungeon);
    }

    public void saveLootTable(Dungeon dungeon, LootTable table) {
        dungeon.getLootTables().put(table.getId(), table);
        save(dungeon);
    }

    public void deleteBoss(Dungeon dungeon, String bossId) {
        dungeon.getBosses().remove(bossId);
        File file = new File(new File(dungeon.getFolder(), "bosses"), bossId + ".yml");
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("No se pudo borrar " + file.getAbsolutePath());
        }
        save(dungeon);
    }

    public void deleteLootTable(Dungeon dungeon, String lootId) {
        dungeon.getLootTables().remove(lootId);
        File file = new File(new File(dungeon.getFolder(), "loot"), lootId + ".yml");
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("No se pudo borrar " + file.getAbsolutePath());
        }
        save(dungeon);
    }

    public Dungeon create(String id) {
        File folder = getFolder(id);
        if (folder.exists()) {
            return null;
        }
        ensureFolder(folder);
        ensureFolder(new File(folder, "bosses"));
        ensureFolder(new File(folder, "loot"));
        ensureFolder(new File(folder, "schematics"));
        Dungeon dungeon = new Dungeon(id);
        dungeon.setFolder(folder);
        dungeon.setSettings(plugin.getConfigManager().getDefaultSettings());
        save(dungeon);
        return dungeon;
    }

    public boolean delete(String id) {
        File folder = getFolder(id);
        if (!folder.exists()) {
            return false;
        }
        try {
            Files.walk(folder.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "No se pudo borrar la dungeon " + id, exception);
            return false;
        }
    }

    public List<String> listIds() {
        List<String> ids = new ArrayList<>();
        File[] folders = dungeonsFolder.listFiles(File::isDirectory);
        if (folders == null) {
            return ids;
        }
        for (File folder : folders) {
            if (new File(folder, "dungeon.yml").isFile()) {
                ids.add(folder.getName());
            }
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    public File getFolder(String id) {
        return new File(dungeonsFolder, id);
    }

    public File getDungeonsFolder() {
        return dungeonsFolder;
    }

    public File getSchematicsFolder(Dungeon dungeon) {
        File folder = new File(dungeon.getFolder(), plugin.getConfigManager().getSchematicsFolderName());
        ensureFolder(folder);
        return folder;
    }

    public File getExportsFolder() {
        File folder = new File(plugin.getDataFolder(), plugin.getConfigManager().getExportsFolderName());
        ensureFolder(folder);
        return folder;
    }

    public File getDataFolder() {
        File folder = new File(plugin.getDataFolder(), "data");
        ensureFolder(folder);
        return folder;
    }

    public void saveAll(Collection<Dungeon> dungeons) {
        for (Dungeon dungeon : dungeons) {
            save(dungeon);
        }
    }

    private void saveYaml(YamlConfiguration yaml, File file) {
        try {
            ensureFolder(file.getParentFile());
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar " + file.getAbsolutePath(), exception);
        }
    }

    private static void ensureFolder(File folder) {
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            // El logger se encarga en la capa superior; esto evita bucles.
        }
    }

    private static String stripExtension(String name) {
        int index = name.lastIndexOf('.');
        return index > 0 ? name.substring(0, index) : name;
    }
}
