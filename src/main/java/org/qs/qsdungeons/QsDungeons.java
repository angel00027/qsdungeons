package org.qs.qsdungeons;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;
import org.qs.qsdungeons.boss.BossService;
import org.qs.qsdungeons.command.DungeonCommand;
import org.qs.qsdungeons.command.DungeonTabCompleter;
import org.qs.qsdungeons.config.ConfigManager;
import org.qs.qsdungeons.config.Messages;
import org.qs.qsdungeons.dungeon.DungeonManager;
import org.qs.qsdungeons.gui.GuiListener;
import org.qs.qsdungeons.gui.GuiManager;
import org.qs.qsdungeons.listener.DungeonEntityListener;
import org.qs.qsdungeons.listener.DungeonEntryListener;
import org.qs.qsdungeons.listener.DungeonLootListener;
import org.qs.qsdungeons.listener.DungeonProtectionListener;
import org.qs.qsdungeons.loot.LootManager;
import org.qs.qsdungeons.placeholder.DungeonPlaceholderExpansion;
import org.qs.qsdungeons.placeholder.PlaceholderService;
import org.qs.qsdungeons.spawn.MobSpawner;
import org.qs.qsdungeons.spawn.MythicMobsHook;
import org.qs.qsdungeons.storage.DungeonArchive;
import org.qs.qsdungeons.storage.DungeonRepository;
import org.qs.qsdungeons.worldedit.WorldEditHook;

/**
 * Plugin principal de QSDungeons.
 */
public final class QsDungeons extends org.bukkit.plugin.java.JavaPlugin {

    private ConfigManager configManager;
    private Messages messages;
    private DungeonRepository repository;
    private DungeonArchive dungeonArchive;
    private PlaceholderService placeholderService;
    private LootManager lootManager;
    private MobSpawner mobSpawner;
    private MythicMobsHook mythicMobsHook;
    private BossService bossService;
    private WorldEditHook worldEditHook;
    private DungeonManager dungeonManager;
    private GuiManager guiManager;
    private DungeonPlaceholderExpansion placeholderExpansion;
    private BukkitTask tickTask;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("No se pudo crear la carpeta del plugin.");
        }

        this.configManager = new ConfigManager(this);
        this.messages = new Messages(this);
        this.messages.load();
        this.repository = new DungeonRepository(this);
        this.dungeonArchive = new DungeonArchive(this);
        this.placeholderService = new PlaceholderService(this);
        this.lootManager = new LootManager(this);
        this.mobSpawner = new MobSpawner(this);
        this.mythicMobsHook = Bukkit.getPluginManager().isPluginEnabled("MythicMobs")
                ? new MythicMobsHook(this)
                : null;
        this.bossService = new BossService(this);
        this.worldEditHook = Bukkit.getPluginManager().isPluginEnabled("WorldEdit")
                || Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
                ? new WorldEditHook(this)
                : null;
        this.dungeonManager = new DungeonManager(this);
        this.guiManager = new GuiManager(this);

        registerListeners();
        registerCommand();
        registerPlaceholderExpansion();
        this.dungeonManager.load();
        startTicker();

        getLogger().info("QSDungeons habilitado. WorldEdit: "
                + (worldEditHook != null ? "si" : "no")
                + " | MythicMobs: " + (mythicMobsHook != null ? "si" : "no")
                + " | PlaceholderAPI: "
                + (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ? "si" : "no"));
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (dungeonManager != null) {
            try {
                dungeonManager.stopAllInstances();
                dungeonManager.saveAll();
                dungeonManager.saveCooldowns();
            } catch (Exception exception) {
                getLogger().warning("Error guardando el estado al deshabilitar: " + exception.getMessage());
            }
        }
        getLogger().info("QSDungeons deshabilitado.");
    }

    private void registerListeners() {
        PluginManager manager = Bukkit.getPluginManager();
        manager.registerEvents(new DungeonEntryListener(this), this);
        manager.registerEvents(new DungeonLootListener(this), this);
        manager.registerEvents(new DungeonProtectionListener(this), this);
        manager.registerEvents(new DungeonEntityListener(this), this);
        manager.registerEvents(new GuiListener(this), this);
    }

    private void registerCommand() {
        PluginCommand command = getCommand("dungeon");
        if (command == null) {
            getLogger().warning("El comando /dungeon no esta declarado en plugin.yml.");
            return;
        }
        command.setExecutor(new DungeonCommand(this));
        command.setTabCompleter(new DungeonTabCompleter(this));
    }

    private void registerPlaceholderExpansion() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            this.placeholderExpansion = new DungeonPlaceholderExpansion(this);
            if (!placeholderExpansion.register()) {
                getLogger().warning("No se pudo registrar la expansion de PlaceholderAPI.");
            }
        } catch (Throwable throwable) {
            getLogger().warning("PlaceholderAPI no compatible: " + throwable.getMessage());
            this.placeholderExpansion = null;
        }
    }

    private void startTicker() {
        long period = Math.max(5L, configManager.getBossBarUpdateTicks());
        tickTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (dungeonManager != null) {
                dungeonManager.tick();
            }
        }, period, period);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public DungeonRepository getRepository() {
        return repository;
    }

    public DungeonArchive getDungeonArchive() {
        return dungeonArchive;
    }

    public PlaceholderService getPlaceholderService() {
        return placeholderService;
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public MobSpawner getMobSpawner() {
        return mobSpawner;
    }

    public MythicMobsHook getMythicMobsHook() {
        return mythicMobsHook;
    }

    public BossService getBossService() {
        return bossService;
    }

    public WorldEditHook getWorldEditHook() {
        return worldEditHook;
    }

    public DungeonManager getDungeonManager() {
        return dungeonManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }
}
