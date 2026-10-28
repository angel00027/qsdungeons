package org.qs.qsdungeons.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.boss.BossDefinition;
import org.qs.qsdungeons.dungeon.BossBarSettings;
import org.qs.qsdungeons.dungeon.ChestSpawn;
import org.qs.qsdungeons.dungeon.CompleteMode;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.DungeonSettings;
import org.qs.qsdungeons.dungeon.DungeonState;
import org.qs.qsdungeons.dungeon.Region;
import org.qs.qsdungeons.dungeon.RewardBlock;
import org.qs.qsdungeons.dungeon.instance.DungeonInstance;
import org.qs.qsdungeons.loot.LootEntry;
import org.qs.qsdungeons.loot.LootTable;
import org.qs.qsdungeons.util.ConfigValues;
import org.qs.qsdungeons.spawn.MobType;
import org.qs.qsdungeons.spawn.SpawnPoint;
import org.qs.qsdungeons.util.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Construye y gestiona todos los menus del editor. El diseno y los textos se
 * leen de menus.yml; las acciones se resuelven por el identificador del item.
 */
public final class GuiManager {

    private static final String[] SETTING_ITEMS = {
            "max-players", "min-players", "time-limit-seconds", "auto-enter", "auto-start",
            "exit-on-leave-region", "leave-on-death", "reset-on-empty", "reset-delay-seconds",
            "end-delay-seconds", "allow-rejoin", "protection", "allow-chest-break", "bosses-after-waves", "entry-permission",
            "cooldown-seconds", "complete-on", "required-kills", "bossbar-enabled",
            "bossbar-title", "bossbar-color", "bossbar-style", "bossbar-radius"
    };

    private static final String[] SPAWN_ITEMS = {
            "type", "entity", "amount", "max-alive", "respawn-seconds", "spawn-interval-seconds",
            "radius", "min-player-distance", "require-ground", "health", "damage", "speed",
            "follow-range", "name", "glowing", "baby", "ai", "persistent",
            "clear-vanilla-drops", "burn-in-daylight", "loot-table", "location"
    };

    private static final String[] BOSS_ITEMS = {
            "type", "entity", "health", "damage", "speed", "follow-range", "name", "glowing",
            "baby", "ai", "persistent", "clear-vanilla-drops", "announce-spawn",
            "announce-death", "burn-in-daylight", "loot-table", "location", "bossbar-enabled", "bossbar-title",
            "bossbar-color", "bossbar-style", "bossbar-radius"
    };

    private static final List<String> BOSS_BAR_COLORS = List.of(
            "PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE");
    private static final List<String> BOSS_BAR_STYLES = List.of(
            "PROGRESS", "NOTCHED_6", "NOTCHED_10", "NOTCHED_12", "NOTCHED_20", "SOLID");

    private final QsDungeons plugin;
    private final GuiConfig config;
    private final GuiConfig playerConfig;
    private final Map<UUID, Prompt> prompts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Location[]> corners = new HashMap<>();
    private final Map<UUID, BukkitTask> playerMenuTasks = new java.util.concurrent.ConcurrentHashMap<>();

    public GuiManager(QsDungeons plugin) {
        this.plugin = plugin;
        this.config = new GuiConfig(plugin);
        this.playerConfig = new GuiConfig(plugin, "player-menus.yml");
        this.config.load();
        this.playerConfig.load();
    }

    public GuiConfig getConfig() {
        return config;
    }

    public void reload() {
        config.reload();
        playerConfig.reload();
    }

    // ------------------------------------------------------------------
    // Prompts de chat
    // ------------------------------------------------------------------

    public Prompt getPrompt(UUID uuid) {
        return prompts.get(uuid);
    }

    public boolean hasPrompt(Player player) {
        return player != null && prompts.containsKey(player.getUniqueId());
    }

    public void startPrompt(Player player, String promptKey, Map<String, String> placeholders,
                            Runnable cancel, Consumer<String> input) {
        if (player == null) {
            return;
        }
        prompts.put(player.getUniqueId(), new Prompt(promptKey, cancel, input));
        player.closeInventory();
        Map<String, String> all = new HashMap<>();
        if (placeholders != null) {
            all.putAll(placeholders);
        }
        all.put("prefix", prefix());
        String raw = config.raw("prompts." + promptKey);
        if (raw == null || raw.isBlank()) {
            raw = "<yellow>Escribe el valor en el chat. Escribe <white>cancelar</white> para salir.";
        }
        plugin.getMessages().sendRaw(player, config.apply(raw, all), null);
    }

    public void acceptPrompt(Player player, String message) {
        if (player == null) {
            return;
        }
        Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        if (!player.isOnline()) {
            return;
        }
        String input = message == null ? "" : message.trim();
        if (input.equalsIgnoreCase("cancelar") || input.equalsIgnoreCase("cancel")) {
            msg(player, "cancelled", Map.of());
            if (prompt.cancel() != null) {
                prompt.cancel().run();
            }
            return;
        }
        try {
            prompt.input().accept(input);
        } catch (Exception exception) {
            msg(player, "invalid-value", Map.of("value", input));
            if (prompt.cancel() != null) {
                prompt.cancel().run();
            }
        }
    }

    public void clearPrompt(Player player) {
        if (player != null) {
            prompts.remove(player.getUniqueId());
        }
    }

    public record Prompt(String key, Runnable cancel, Consumer<String> input) {
    }

    // ------------------------------------------------------------------
    // Menus principales
    // ------------------------------------------------------------------

    public void openMain(Player player) {
        GuiHolder holder = new GuiHolder("main", null, null, 0);
        Inventory inventory = create(holder);
        button(inventory, holder, "main", "dungeons", "open|dungeon-list");
        button(inventory, holder, "main", "create", "create-dungeon");
        button(inventory, holder, "main", "reload", "reload");
        button(inventory, holder, "main", "close", "close");
        open(player, inventory);
    }

    public void openPlayerDungeons(Player player, int page) {
        GuiHolder holder = new GuiHolder("player-dungeons", null, null, page);
        Inventory inventory = create(holder);
        renderPlayerDungeons(player, holder, inventory);
        open(player, inventory);
        startPlayerMenuRefresh(player);
    }

    private void renderPlayerDungeons(Player player, GuiHolder holder, Inventory inventory) {
        List<ListEntry> entries = new ArrayList<>();
        List<String> templates = new ArrayList<>();
        String dungeonMode = playerConfig.getString("dungeons.mode", "all")
                .toLowerCase(Locale.ROOT);
        List<String> configuredDungeons = playerConfig.getStringList("dungeons.ids").stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).toList();
        for (Dungeon dungeon : sortedDungeons()) {
            if (!dungeon.isEnabled()) {
                continue;
            }
            boolean listed = configuredDungeons.contains(dungeon.getId().toLowerCase(Locale.ROOT));
            if (dungeonMode.equals("whitelist") && !listed
                    || dungeonMode.equals("blacklist") && listed) {
                continue;
            }
            DungeonInstance instance = plugin.getDungeonManager().getInstance(dungeon.getId());
            long cooldown = plugin.getDungeonManager().getCooldownRemaining(player, dungeon);
            Map<String, String> ph = new LinkedHashMap<>();
            ph.put("dungeon", dungeon.getId());
            ph.put("name", dungeon.getDisplayName());
            ph.put("state", stateLabel(instance));
            ph.put("enabled", yesNo(dungeon.isEnabled()));
            ph.put("ready", yesNo(dungeon.isReady()));
            ph.put("players", String.valueOf(instance == null ? 0 : instance.getPlayerCount()));
            ph.put("max_players", String.valueOf(dungeon.getSettings().getMaxPlayers()));
            ph.put("cooldown", cooldown <= 0 ? "Disponible"
                    : plugin.getPlaceholderService().formatDuration(cooldown));
            ph.put("cooldown_seconds", String.valueOf(Math.max(0L, cooldown)));
            ph.put("available", cooldown > 0 ? "No (cooldown)" : dungeon.isReady() ? "Si" : "No");
            ph.put("world", dungeon.getRegion() == null ? "-" : dungeon.getRegion().getWorldName());
            entries.add(new ListEntry(dungeon.getId(), ph));
            templates.add(cooldown > 0 ? "dungeon-entry-cooldown"
                    : instance != null && instance.isRunning()
                    ? "dungeon-entry-active" : "dungeon-entry-available");
        }
        holder.clearActions();
        for (int slot : playerConfig.listSlots("player-dungeons")) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, null);
            }
        }
        fillPlayerDynamic(inventory, holder, entries, templates);
        navigation(inventory, holder, "player-dungeons", "main", holder.getPage());
    }

    private void startPlayerMenuRefresh(Player player) {
        stopPlayerMenuRefresh(player);
        UUID uuid = player.getUniqueId();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()
                    || !(player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder holder)
                    || !holder.getMenu().equals("player-dungeons")) {
                stopPlayerMenuRefresh(player);
                return;
            }
            renderPlayerDungeons(player, holder, holder.getInventory());
        }, 20L, 20L);
        playerMenuTasks.put(uuid, task);
    }

    public void stopPlayerMenuRefresh(Player player) {
        if (player == null) {
            return;
        }
        BukkitTask task = playerMenuTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public void openDungeonList(Player player, int page) {
        GuiHolder holder = new GuiHolder("dungeon-list", null, null, page);
        Inventory inventory = create(holder);
        List<ListEntry> entries = new ArrayList<>();
        for (Dungeon dungeon : sortedDungeons()) {
            DungeonInstance instance = plugin.getDungeonManager().getInstance(dungeon.getId());
            Map<String, String> ph = new LinkedHashMap<>();
            ph.put("dungeon", dungeon.getId());
            ph.put("name", dungeon.getDisplayName());
            ph.put("state", stateLabel(instance));
            ph.put("players", String.valueOf(instance == null ? 0 : instance.getPlayerCount()));
            ph.put("max_players", String.valueOf(dungeon.getSettings().getMaxPlayers()));
            ph.put("enabled", yesNo(dungeon.isEnabled()));
            ph.put("ready", yesNo(dungeon.isReady()));
            ph.put("world", dungeon.getRegion() == null ? "-" : dungeon.getRegion().getWorldName());
            ph.put("spawns", String.valueOf(dungeon.getSpawns().size()));
            ph.put("bosses", String.valueOf(dungeon.getBosses().size()));
            ph.put("chests", String.valueOf(dungeon.getChests().size()));
            ph.put("loot", String.valueOf(dungeon.getLootTables().size()));
            ph.put("rewards", String.valueOf(dungeon.getRewards().size()));
            entries.add(new ListEntry(dungeon.getId(), ph));
        }
        fillDynamic(inventory, holder, "dungeon-list", "dungeon-entry", entries, "dungeon.entry");
        navigation(inventory, holder, "dungeon-list", "main", page);
        button(inventory, holder, "dungeon-list", "create", "create-dungeon");
        open(player, inventory);
    }

    public void openDungeonEditor(Player player, Dungeon dungeon) {
        if (dungeon == null) {
            openDungeonList(player, 0);
            return;
        }
        GuiHolder holder = new GuiHolder("dungeon-editor", dungeon.getId(), null, 0);
        Inventory inventory = create(holder);
        Map<String, String> ph = base(dungeon);
        button(inventory, holder, "dungeon-editor", "info", "none", ph);
        button(inventory, holder, "dungeon-editor", "settings", "open|settings", ph);
        button(inventory, holder, "dungeon-editor", "spawns", "open|spawns", ph);
        button(inventory, holder, "dungeon-editor", "bosses", "open|bosses", ph);
        button(inventory, holder, "dungeon-editor", "loot", "open|loot-list", ph);
        button(inventory, holder, "dungeon-editor", "chests", "open|chests", ph);
        button(inventory, holder, "dungeon-editor", "rewards", "open|rewards", ph);
        button(inventory, holder, "dungeon-editor", "toggle", "dungeon.toggle", ph);
        button(inventory, holder, "dungeon-editor", "pos1", "dungeon.pos1", ph);
        button(inventory, holder, "dungeon-editor", "pos2", "dungeon.pos2", ph);
        button(inventory, holder, "dungeon-editor", "save-region", "dungeon.region", ph);
        button(inventory, holder, "dungeon-editor", "entry", "dungeon.entry-point", ph);
        button(inventory, holder, "dungeon-editor", "exit", "dungeon.exit-point", ph);
        button(inventory, holder, "dungeon-editor", "start", "dungeon.start", ph);
        button(inventory, holder, "dungeon-editor", "stop", "dungeon.stop", ph);
        button(inventory, holder, "dungeon-editor", "reset", "dungeon.reset", ph);
        button(inventory, holder, "dungeon-editor", "delete", "dungeon.delete", ph);
        button(inventory, holder, "dungeon-editor", "back", "open|dungeon-list", ph);
        open(player, inventory);
    }

    public void openSettings(Player player, Dungeon dungeon) {
        if (dungeon == null) {
            openDungeonList(player, 0);
            return;
        }
        GuiHolder holder = new GuiHolder("settings", dungeon.getId(), null, 0);
        Inventory inventory = create(holder);
        for (String itemKey : SETTING_ITEMS) {
            String settingKey = settingKey(itemKey);
            Map<String, String> ph = base(dungeon);
            ph.put("key", settingKey);
            ph.put("value", settingValue(dungeon.getSettings(), settingKey));
            button(inventory, holder, "settings", itemKey, "setting|" + settingKey, ph);
        }
        button(inventory, holder, "settings", "back", "open|dungeon-editor", base(dungeon));
        open(player, inventory);
    }

    public void openSpawns(Player player, Dungeon dungeon) {
        openSpawns(player, dungeon, 0);
    }

    public void openSpawns(Player player, Dungeon dungeon, int page) {
        if (dungeon == null) {
            openDungeonList(player, 0);
            return;
        }
        GuiHolder holder = new GuiHolder("spawns", dungeon.getId(), null, page);
        Inventory inventory = create(holder);
        List<ListEntry> entries = new ArrayList<>();
        for (SpawnPoint spawn : sorted(dungeon.getSpawns().values(), SpawnPoint::getId)) {
            entries.add(new ListEntry(spawn.getId(), spawnPlaceholders(spawn)));
        }
        fillDynamic(inventory, holder, "spawns", "spawn-entry", entries, "spawn.entry");
        navigation(inventory, holder, "spawns", "dungeon-editor", page);
        button(inventory, holder, "spawns", "create", "spawn.create", base(dungeon));
        open(player, inventory);
    }

    public void openSpawnEditor(Player player, Dungeon dungeon, SpawnPoint spawn) {
        if (dungeon == null || spawn == null) {
            openSpawns(player, dungeon);
            return;
        }
        GuiHolder holder = new GuiHolder("spawn-editor", dungeon.getId(), spawn.getId(), 0);
        Inventory inventory = create(holder);
        for (String itemKey : SPAWN_ITEMS) {
            Map<String, String> ph = spawnPlaceholders(spawn);
            ph.put("key", itemKey);
            ph.put("value", spawnValue(spawn, itemKey));
            button(inventory, holder, "spawn-editor", itemKey, "spawn.field|" + itemKey, ph);
        }
        button(inventory, holder, "spawn-editor", "delete", "spawn.delete", spawnPlaceholders(spawn));
        button(inventory, holder, "spawn-editor", "equipment", "spawn.equipment", spawnPlaceholders(spawn));
        button(inventory, holder, "spawn-editor", "back", "open|spawns", spawnPlaceholders(spawn));
        open(player, inventory);
    }

    public void openBosses(Player player, Dungeon dungeon) {
        openBosses(player, dungeon, 0);
    }

    public void openBosses(Player player, Dungeon dungeon, int page) {
        if (dungeon == null) {
            openDungeonList(player, 0);
            return;
        }
        GuiHolder holder = new GuiHolder("bosses", dungeon.getId(), null, page);
        Inventory inventory = create(holder);
        List<ListEntry> entries = new ArrayList<>();
        for (BossDefinition boss : sorted(dungeon.getBosses().values(), BossDefinition::getId)) {
            entries.add(new ListEntry(boss.getId(), bossPlaceholders(boss)));
        }
        fillDynamic(inventory, holder, "bosses", "boss-entry", entries, "boss.entry");
        navigation(inventory, holder, "bosses", "dungeon-editor", page);
        button(inventory, holder, "bosses", "create", "boss.create", base(dungeon));
        open(player, inventory);
    }

    public void openBossEditor(Player player, Dungeon dungeon, BossDefinition boss) {
        if (dungeon == null || boss == null) {
            openBosses(player, dungeon);
            return;
        }
        GuiHolder holder = new GuiHolder("boss-editor", dungeon.getId(), boss.getId(), 0);
        Inventory inventory = create(holder);
        for (String itemKey : BOSS_ITEMS) {
            Map<String, String> ph = bossPlaceholders(boss);
            ph.put("key", itemKey);
            ph.put("value", bossValue(boss, itemKey));
            button(inventory, holder, "boss-editor", itemKey, "boss.field|" + itemKey, ph);
        }
        button(inventory, holder, "boss-editor", "delete", "boss.delete", bossPlaceholders(boss));
        button(inventory, holder, "boss-editor", "equipment", "boss.equipment", bossPlaceholders(boss));
        button(inventory, holder, "boss-editor", "back", "open|bosses", bossPlaceholders(boss));
        open(player, inventory);
    }

    public void openEquipmentEditor(Player player, Dungeon dungeon, SpawnPoint spawn,
                                    BossDefinition boss) {
        if (dungeon == null || (spawn == null && boss == null)) {
            openDungeonList(player, 0);
            return;
        }
        boolean spawnMode = spawn != null;
        String targetId = spawnMode ? spawn.getId() : boss.getId();
        GuiHolder holder = new GuiHolder("equipment", dungeon.getId(), targetId, 0);
        holder.getData().put("equipment-mode", spawnMode ? "spawn" : "boss");
        Inventory inventory = create(holder);
        Map<EquipmentSlot, ItemStack> equipment = spawnMode
                ? spawn.getEquipment() : boss.getEquipment();
        List<String> equipmentSlots = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            String key = equipmentKey(slot);
            GuiConfig.ItemSpec spec = config.item("equipment", key);
            if (spec == null || spec.slot() < 0 || spec.slot() >= inventory.getSize()) {
                continue;
            }
            equipmentSlots.add(String.valueOf(spec.slot()));
            ItemStack item = equipment.get(slot);
            if (item == null) {
                item = new ItemStack(Material.AIR);
            } else {
                item = item.clone();
            }
            inventory.setItem(spec.slot(), item);
        }
        holder.getData().put("equipment-slots", String.join(",", equipmentSlots));
        button(inventory, holder, "equipment", "back",
                "open|" + (spawnMode ? "spawn-editor" : "boss-editor"),
                spawnMode ? spawnPlaceholders(spawn) : bossPlaceholders(boss));
        open(player, inventory);
    }

    public void saveEquipment(GuiHolder holder) {
        if (holder == null || !"equipment".equals(holder.getMenu())) {
            return;
        }
        Dungeon dungeon = dungeon(holder.getDungeonId());
        if (dungeon == null || holder.getInventory() == null) {
            return;
        }
        boolean spawnMode = "spawn".equals(holder.getData().get("equipment-mode"));
        SpawnPoint spawn = spawnMode ? dungeon.getSpawn(holder.getTargetId()) : null;
        BossDefinition boss = spawnMode ? null : dungeon.getBoss(holder.getTargetId());
        if (spawn == null && boss == null) {
            return;
        }
        Map<EquipmentSlot, ItemStack> equipment = spawnMode ? spawn.getEquipment() : boss.getEquipment();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            GuiConfig.ItemSpec spec = config.item("equipment", equipmentKey(slot));
            if (spec == null) {
                continue;
            }
            ItemStack item = holder.getInventory().getItem(spec.slot());
            ItemStack placeholder = build(spec, Map.of("slot", slotLabel(slot)));
            if (item == null || item.getType().isAir()
                    || placeholder != null && item.isSimilar(placeholder)) {
                equipment.remove(slot);
            } else {
                equipment.put(slot, item.clone());
            }
        }
        save(dungeon);
    }

    public void openEntityPicker(Player player, Dungeon dungeon, SpawnPoint spawn, BossDefinition boss, int page) {
        String targetId = spawn != null ? spawn.getId() : boss == null ? null : boss.getId();
        String mode = spawn != null ? "spawn" : "boss";
        String returnMenu = spawn != null ? "spawn-editor" : "boss-editor";
        GuiHolder holder = new GuiHolder("entity-picker", dungeon == null ? null : dungeon.getId(), targetId, page);
        holder.getData().put("entity-mode", mode);
        if (boss != null) {
            holder.getData().put("current", boss.getEntity());
        } else if (spawn != null) {
            holder.getData().put("current", spawn.getEntity());
        }
        Inventory inventory = create(holder);
        List<ListEntry> entries = new ArrayList<>();
        for (EntityType type : org.bukkit.Registry.ENTITY_TYPE) {
            if (!type.isSpawnable() || type.getEntityClass() == null
                    || !org.bukkit.entity.LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
                continue;
            }
            Map<String, String> ph = new LinkedHashMap<>();
            ph.put("entity", type.name());
            ph.put("entity_type", config.raw("format.vanilla"));
            ph.put("entity_id", type.name());
            entries.add(new ListEntry(type.name(), ph));
        }
        if (plugin.getMythicMobsHook() != null) {
            for (String id : plugin.getMythicMobsHook().listMobIds()) {
                Map<String, String> ph = new LinkedHashMap<>();
                ph.put("entity", "mythic:" + id);
                ph.put("entity_type", config.raw("format.mythic"));
                ph.put("entity_id", id);
                entries.add(new ListEntry("mythic:" + id, ph));
            }
        }
        entries.sort(Comparator.comparing(ListEntry::id, String.CASE_INSENSITIVE_ORDER));
        fillDynamic(inventory, holder, "entity-picker", "entity-entry", entries, "entity.pick");
        navigation(inventory, holder, "entity-picker", returnMenu, page);
        button(inventory, holder, "entity-picker", "back", "open|" + returnMenu, Map.of());
        open(player, inventory);
    }

    public void openLootList(Player player, Dungeon dungeon) {
        openLootList(player, dungeon, 0);
    }

    public void openLootList(Player player, Dungeon dungeon, int page) {
        if (dungeon == null) {
            openDungeonList(player, 0);
            return;
        }
        GuiHolder holder = new GuiHolder("loot-list", dungeon.getId(), null, page);
        Inventory inventory = create(holder);
        List<ListEntry> entries = new ArrayList<>();
        for (LootTable table : sorted(dungeon.getLootTables().values(), LootTable::getId)) {
            entries.add(new ListEntry(table.getId(), lootPlaceholders(table)));
        }
        fillDynamic(inventory, holder, "loot-list", "loot-entry", entries, "loot.entry");
        navigation(inventory, holder, "loot-list", "dungeon-editor", page);
        button(inventory, holder, "loot-list", "create", "loot.create", base(dungeon));
        open(player, inventory);
    }

    public void openLootEditor(Player player, Dungeon dungeon, LootTable table, int page) {
        if (dungeon == null || table == null) {
            openLootList(player, dungeon);
            return;
        }
        GuiHolder holder = new GuiHolder("loot-editor", dungeon.getId(), table.getId(), page);
        Inventory inventory = create(holder);
        Map<String, String> ph = lootPlaceholders(table);
        button(inventory, holder, "loot-editor", "info", "none", ph);
        button(inventory, holder, "loot-editor", "rolls", "loot.field|rolls", ph);
        button(inventory, holder, "loot-editor", "unique", "loot.field|unique", ph);
        GuiConfig.ItemSpec inputSpec = config.item("loot-editor", "loot-input");
        if (inputSpec != null && inputSpec.slot() >= 0 && inputSpec.slot() < inventory.getSize()) {
            holder.getData().put("loot-input-slot", String.valueOf(inputSpec.slot()));
            ItemStack empty = new ItemStack(Material.AIR);
            inventory.setItem(inputSpec.slot(), empty);
            holder.getData().put("loot-input-empty", "AIR");
        }
        List<ListEntry> entries = new ArrayList<>();
        List<LootEntry> lootEntries = table.getEntries();
        for (int index = 0; index < lootEntries.size(); index++) {
            LootEntry entry = lootEntries.get(index);
            Map<String, String> itemPh = lootEntryPlaceholders(entry, index);
            entries.add(new ListEntry(String.valueOf(index), itemPh));
        }
        fillDynamic(inventory, holder, "loot-editor", "loot-item-entry", entries, "loot.entry");
        navigation(inventory, holder, "loot-editor", "loot-list", page);
        button(inventory, holder, "loot-editor", "delete", "loot.delete", ph);
        button(inventory, holder, "loot-editor", "back", "open|loot-list", ph);
        open(player, inventory);
    }

    private void openLootEntryEditor(Player player, Dungeon dungeon, LootTable table, int index) {
        if (dungeon == null || table == null || index < 0 || index >= table.getEntries().size()) {
            openLootEditor(player, dungeon, table, 0);
            return;
        }
        GuiHolder holder = new GuiHolder("loot-entry-editor", dungeon.getId(), table.getId(), 0);
        holder.getData().put("entry-index", String.valueOf(index));
        Inventory inventory = create(holder);
        LootEntry entry = table.getEntries().get(index);
        Map<String, String> ph = lootPlaceholders(table);
        ph.putAll(lootEntryPlaceholders(entry, index));
        button(inventory, holder, "loot-entry-editor", "item", "none", ph);
        button(inventory, holder, "loot-entry-editor", "amount", "loot.entry-field|amount", ph);
        button(inventory, holder, "loot-entry-editor", "chance", "loot.entry-field|chance", ph);
        button(inventory, holder, "loot-entry-editor", "weight", "loot.entry-field|weight", ph);
        button(inventory, holder, "loot-entry-editor", "message", "loot.entry-field|message", ph);
        button(inventory, holder, "loot-entry-editor", "back", "open|loot-editor", ph);
        open(player, inventory);
    }

    public void handleEquipmentClick(Player player, GuiHolder holder,
                                     org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            event.setCancelled(!event.isShiftClick());
            return;
        }
        boolean allowed = java.util.Arrays.stream(holder.getData()
                        .getOrDefault("equipment-slots", "").split(","))
                .anyMatch(value -> value.equals(String.valueOf(event.getRawSlot())));
        if (!allowed) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        ItemStack current = event.getView().getTopInventory().getItem(event.getRawSlot());
        GuiConfig.ItemSpec emptySpec = null;
        EquipmentSlot emptySlot = null;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            GuiConfig.ItemSpec candidate = config.item("equipment", equipmentKey(slot));
            if (candidate != null && candidate.slot() == event.getRawSlot()) {
                emptySpec = candidate;
                emptySlot = slot;
                break;
            }
        }
        if (current != null && emptySpec != null) {
            ItemStack placeholder = build(emptySpec, Map.of("slot", slotLabel(emptySlot)));
            if (placeholder != null && current.isSimilar(placeholder)) {
                current = null;
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            event.getView().getTopInventory().setItem(event.getRawSlot(), cursor.clone());
            player.setItemOnCursor(current == null ? new ItemStack(Material.AIR) : current.clone());
        } else if (current != null && !current.getType().isAir()) {
            event.getView().getTopInventory().setItem(event.getRawSlot(), new ItemStack(Material.AIR));
            player.setItemOnCursor(current.clone());
        }
        saveEquipment(holder);
    }

    public void handleEquipmentDrag(Player player, GuiHolder holder,
                                    org.bukkit.event.inventory.InventoryDragEvent event) {
        event.setCancelled(true);
        String allowed = holder.getData().getOrDefault("equipment-slots", "");
        Integer target = event.getRawSlots().stream()
                .filter(slot -> java.util.Arrays.stream(allowed.split(","))
                        .anyMatch(value -> value.equals(String.valueOf(slot))))
                .findFirst().orElse(null);
        if (target == null || event.getOldCursor() == null
                || event.getOldCursor().getType().isAir()) {
            return;
        }
        event.getView().getTopInventory().setItem(target, event.getOldCursor().clone());
        player.setItemOnCursor(new ItemStack(Material.AIR));
        saveEquipment(holder);
    }

    public void handleLootClick(Player player, GuiHolder holder,
                                org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            event.setCancelled(event.isShiftClick());
            return;
        }
        boolean allowed = String.valueOf(event.getRawSlot()).equals(
                holder.getData().getOrDefault("loot-input-slot", "-1"));
        event.setCancelled(true);
        if (!allowed) {
            return;
        }
        ItemStack current = event.getView().getTopInventory().getItem(event.getRawSlot());
        GuiConfig.ItemSpec inputSpec = config.item("loot-editor", "loot-input");
        ItemStack placeholder = inputSpec == null ? null : build(inputSpec, Map.of());
        if (placeholder != null && current != null && current.isSimilar(placeholder)) {
            current = null;
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            event.getView().getTopInventory().setItem(event.getRawSlot(), cursor.clone());
            player.setItemOnCursor(current == null ? new ItemStack(Material.AIR) : current.clone());
        } else if (current != null && !current.getType().isAir()) {
            event.getView().getTopInventory().setItem(event.getRawSlot(), new ItemStack(Material.AIR));
            player.setItemOnCursor(current.clone());
        }
    }

    public void handleLootDrag(Player player, GuiHolder holder,
                               org.bukkit.event.inventory.InventoryDragEvent event) {
        event.setCancelled(true);
        String allowed = holder.getData().getOrDefault("loot-input-slot", "-1");
        Integer target = event.getRawSlots().stream()
                .filter(slot -> String.valueOf(slot).equals(allowed))
                .findFirst().orElse(null);
        if (target == null || event.getOldCursor() == null
                || event.getOldCursor().getType().isAir()) {
            return;
        }
        event.getView().getTopInventory().setItem(target, event.getOldCursor().clone());
        player.setItemOnCursor(new ItemStack(Material.AIR));
    }

    public void addLootFromGui(GuiHolder holder) {
        if (holder == null || !"loot-editor".equals(holder.getMenu())) {
            return;
        }
        String rawSlot = holder.getData().get("loot-input-slot");
        if (rawSlot == null || holder.getInventory() == null) {
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(rawSlot);
        } catch (NumberFormatException ignored) {
            return;
        }
        ItemStack item = holder.getInventory().getItem(slot);
        if (item == null || item.getType().isAir()) {
            return;
        }
        GuiConfig.ItemSpec inputSpec = config.item("loot-editor", "loot-input");
        ItemStack empty = inputSpec == null ? null : build(inputSpec, Map.of());
        if (empty != null && item.isSimilar(empty)) {
            return;
        }
        Dungeon dungeon = dungeon(holder.getDungeonId());
        LootTable table = dungeon == null ? null : dungeon.getLootTable(holder.getTargetId());
        if (table == null) {
            return;
        }
        table.getEntries().add(new LootEntry(item.clone()));
        table.markEntriesChanged();
        save(dungeon);
        holder.getInventory().setItem(slot, new ItemStack(Material.AIR));
    }

    /** Reconstruye el editor para mostrar inmediatamente la entrada añadida. */
    public void addLootFromGui(Player player, GuiHolder holder) {
        if (player == null || holder == null) {
            return;
        }
        Dungeon dungeon = dungeon(holder.getDungeonId());
        LootTable table = dungeon == null ? null : dungeon.getLootTable(holder.getTargetId());
        int before = table == null ? -1 : table.getEntries().size();
        addLootFromGui(holder);
        if (table != null && table.getEntries().size() > before) {
            openLootEditor(player, dungeon, table, holder.getPage());
        }
    }

    public void openLootPicker(Player player, Dungeon dungeon, String applyType, String applyId, int page) {
        if (dungeon == null) {
            openDungeonList(player, 0);
            return;
        }
        GuiHolder holder = new GuiHolder("loot-picker", dungeon.getId(), applyId, page);
        holder.getData().put("apply-type", applyType);
        Inventory inventory = create(holder);
        List<ListEntry> entries = new ArrayList<>();
        for (LootTable table : sorted(dungeon.getLootTables().values(), LootTable::getId)) {
            entries.add(new ListEntry(table.getId(), lootPlaceholders(table)));
        }
        fillDynamic(inventory, holder, "loot-picker", "loot-pick-entry", entries, "loot.pick");
        navigation(inventory, holder, "loot-picker", returnMenuFor(applyType), page);
        button(inventory, holder, "loot-picker", "clear", "loot.clear", Map.of());
        button(inventory, holder, "loot-picker", "back", "open|" + returnMenuFor(applyType), Map.of());
        open(player, inventory);
    }

    public void openChests(Player player, Dungeon dungeon) {
        openChests(player, dungeon, 0);
    }

    public void openChests(Player player, Dungeon dungeon, int page) {
        if (dungeon == null) {
            openDungeonList(player, 0);
            return;
        }
        GuiHolder holder = new GuiHolder("chests", dungeon.getId(), null, page);
        Inventory inventory = create(holder);
        List<ListEntry> entries = new ArrayList<>();
        for (ChestSpawn chest : sorted(dungeon.getChests().values(), ChestSpawn::getId)) {
            entries.add(new ListEntry(chest.getId(), chestPlaceholders(chest)));
        }
        fillDynamic(inventory, holder, "chests", "chest-entry", entries, "chest.entry");
        navigation(inventory, holder, "chests", "dungeon-editor", page);
        button(inventory, holder, "chests", "create", "chest.create", base(dungeon));
        open(player, inventory);
    }

    public void openChestEditor(Player player, Dungeon dungeon, ChestSpawn chest) {
        if (dungeon == null || chest == null) {
            openChests(player, dungeon);
            return;
        }
        GuiHolder holder = new GuiHolder("chest-editor", dungeon.getId(), chest.getId(), 0);
        Inventory inventory = create(holder);
        Map<String, String> ph = chestPlaceholders(chest);
        button(inventory, holder, "chest-editor", "loot-table", "chest.field|loot-table", ph);
        button(inventory, holder, "chest-editor", "refill-seconds", "chest.field|refill-seconds", ph);
        button(inventory, holder, "chest-editor", "enabled", "chest.field|enabled", ph);
        button(inventory, holder, "chest-editor", "location", "chest.field|location", ph);
        button(inventory, holder, "chest-editor", "delete", "chest.delete", ph);
        button(inventory, holder, "chest-editor", "back", "open|chests", ph);
        open(player, inventory);
    }

    public void openRewards(Player player, Dungeon dungeon) {
        openRewards(player, dungeon, 0);
    }

    public void openRewards(Player player, Dungeon dungeon, int page) {
        if (dungeon == null) {
            openDungeonList(player, 0);
            return;
        }
        GuiHolder holder = new GuiHolder("rewards", dungeon.getId(), null, page);
        Inventory inventory = create(holder);
        List<ListEntry> entries = new ArrayList<>();
        for (RewardBlock reward : sorted(dungeon.getRewards().values(), RewardBlock::getId)) {
            entries.add(new ListEntry(reward.getId(), rewardPlaceholders(reward)));
        }
        fillDynamic(inventory, holder, "rewards", "reward-entry", entries, "reward.entry");
        navigation(inventory, holder, "rewards", "dungeon-editor", page);
        button(inventory, holder, "rewards", "create", "reward.create", base(dungeon));
        open(player, inventory);
    }

    public void openRewardEditor(Player player, Dungeon dungeon, RewardBlock reward) {
        if (dungeon == null || reward == null) {
            openRewards(player, dungeon);
            return;
        }
        GuiHolder holder = new GuiHolder("reward-editor", dungeon.getId(), reward.getId(), 0);
        Inventory inventory = create(holder);
        Map<String, String> ph = rewardPlaceholders(reward);
        button(inventory, holder, "reward-editor", "loot-table", "reward.field|loot-table", ph);
        button(inventory, holder, "reward-editor", "one-time", "reward.field|one-time", ph);
        button(inventory, holder, "reward-editor", "respawn-seconds", "reward.field|respawn-seconds", ph);
        button(inventory, holder, "reward-editor", "direct-give", "reward.field|direct-give", ph);
        button(inventory, holder, "reward-editor", "announce", "reward.field|announce", ph);
        button(inventory, holder, "reward-editor", "location", "reward.field|location", ph);
        button(inventory, holder, "reward-editor", "delete", "reward.delete", ph);
        button(inventory, holder, "reward-editor", "back", "open|rewards", ph);
        open(player, inventory);
    }

    public void openConfirm(Player player, Dungeon dungeon, String title, String action,
                            String target, String returnMenu) {
        GuiHolder holder = new GuiHolder("confirm", dungeon == null ? null : dungeon.getId(), target, 0);
        holder.getData().put("confirm-title", title);
        holder.getData().put("confirm-action", action);
        holder.getData().put("confirm-return", returnMenu);
        Inventory inventory = create(holder);
        Map<String, String> ph = new LinkedHashMap<>();
        ph.put("title", title);
        ph.put("target", target == null ? "" : target);
        button(inventory, holder, "confirm", "confirm", "confirm.yes", ph);
        button(inventory, holder, "confirm", "cancel", "confirm.no", ph);
        open(player, inventory);
    }

    // ------------------------------------------------------------------
    // Acciones de clic
    // ------------------------------------------------------------------

    public void handleClick(Player player, GuiHolder holder, int slot, boolean rightClick,
                            boolean shiftClick) {
        String bound = holder.actionAt(slot);
        if (bound == null || bound.isBlank()) {
            return;
        }
        String[] parts = bound.split("\\|", 2);
        String action = parts[0];
        String payload = parts.length > 1 ? parts[1] : "";
        Dungeon dungeon = dungeon(holder.getDungeonId());
        try {
            switch (action) {
                case "close" -> player.closeInventory();
                case "open" -> openMenu(player, payload, holder.getDungeonId(), holder.getTargetId(),
                        holder.getPage());
                case "player.dungeon.entry" -> {
                    Dungeon target = dungeon(payload);
                    player.closeInventory();
                    if (target != null) {
                        plugin.getDungeonManager().join(player, target);
                    }
                }
                case "reload" -> {
                    plugin.getDungeonManager().reload();
                    msg(player, "reloaded", Map.of());
                    openMain(player);
                }
                case "create-dungeon" -> promptCreateDungeon(player);
                case "page" -> {
                    int page = parseInt(payload, 0);
                    if (holder.getMenu().equals("entity-picker")) {
                        String mode = holder.getData().getOrDefault("entity-mode", "spawn");
                        if (mode.equals("boss")) {
                            openEntityPicker(player, dungeon, null,
                                    dungeon == null ? null : dungeon.getBoss(holder.getTargetId()), page);
                        } else {
                            openEntityPicker(player, dungeon,
                                    dungeon == null ? null : dungeon.getSpawn(holder.getTargetId()), null, page);
                        }
                    } else if (holder.getMenu().equals("loot-picker")) {
                        openLootPicker(player, dungeon,
                                holder.getData().getOrDefault("apply-type", "spawn"),
                                holder.getTargetId(), page);
                    } else {
                        openMenu(player, holder.getMenu(), holder.getDungeonId(),
                                holder.getTargetId(), page);
                    }
                }
                case "dungeon.entry" -> {
                    Dungeon target = dungeon(payload);
                    if (target == null) {
                        openDungeonList(player, holder.getPage());
                    } else if (shiftClick && rightClick) {
                        openConfirm(player, target, target.getId(), "delete-dungeon", target.getId(),
                                "dungeon-list");
                    } else if (rightClick) {
                        target.setEnabled(!target.isEnabled());
                        save(target);
                        openDungeonList(player, holder.getPage());
                    } else {
                        openDungeonEditor(player, target);
                    }
                }
                case "dungeon.toggle" -> {
                    if (dungeon != null) {
                        dungeon.setEnabled(!dungeon.isEnabled());
                        save(dungeon);
                        msg(player, "saved", Map.of("dungeon", dungeon.getId()));
                        openDungeonEditor(player, dungeon);
                    }
                }
                case "dungeon.delete" -> {
                    if (dungeon != null) {
                        openConfirm(player, dungeon, dungeon.getId(), "delete-dungeon", dungeon.getId(),
                                "dungeon-editor");
                    }
                }
                case "dungeon.start" -> {
                    if (dungeon != null) {
                        plugin.getDungeonManager().start(dungeon, player);
                        openDungeonEditor(player, dungeon);
                    }
                }
                case "dungeon.stop" -> {
                    if (dungeon != null) {
                        plugin.getDungeonManager().stop(dungeon, player, false);
                        openDungeonEditor(player, dungeon);
                    }
                }
                case "dungeon.reset" -> {
                    if (dungeon != null) {
                        plugin.getDungeonManager().reset(dungeon);
                        msg(player, "saved", Map.of("dungeon", dungeon.getId()));
                        openDungeonEditor(player, dungeon);
                    }
                }
                case "spawn.equipment" -> {
                    if (dungeon != null) {
                        openEquipmentEditor(player, dungeon, dungeon.getSpawn(holder.getTargetId()), null);
                    }
                }
                case "dungeon.entry-point" -> {
                    if (dungeon != null) {
                        dungeon.setEntry(player.getLocation().clone());
                        save(dungeon);
                        msg(player, "position-set", Map.of("dungeon", dungeon.getId()));
                        openDungeonEditor(player, dungeon);
                    }
                }
                case "dungeon.exit-point" -> {
                    if (dungeon != null) {
                        dungeon.setExit(player.getLocation().clone());
                        save(dungeon);
                        msg(player, "position-set", Map.of("dungeon", dungeon.getId()));
                        openDungeonEditor(player, dungeon);
                    }
                }
                case "dungeon.pos1", "dungeon.pos2" -> {
                    if (dungeon != null) {
                        Location[] selection = corners.computeIfAbsent(player.getUniqueId(),
                                ignored -> new Location[2]);
                        selection[action.endsWith("pos1") ? 0 : 1] = player.getLocation().clone();
                        msg(player, "position-set", Map.of("dungeon", dungeon.getId()));
                        openDungeonEditor(player, dungeon);
                    }
                }
                case "dungeon.region" -> {
                    if (dungeon != null) {
                        Location[] selection = corners.get(player.getUniqueId());
                        Region region = selection == null ? null : Region.from(selection[0], selection[1]);
                        if (region == null) {
                            msg(player, "region-missing", Map.of("dungeon", dungeon.getId()));
                        } else {
                            dungeon.setRegion(region);
                            save(dungeon);
                            msg(player, "region-saved", Map.of("dungeon", dungeon.getId()));
                        }
                        openDungeonEditor(player, dungeon);
                    }
                }
                case "setting" -> {
                    if (dungeon != null) {
                        if (rightClick) {
                            promptSetting(player, dungeon, payload);
                        } else {
                            cycleSetting(player, dungeon, payload);
                        }
                    }
                }
                case "spawn.entry" -> {
                    if (dungeon == null) {
                        openDungeonList(player, holder.getPage());
                    } else if (rightClick) {
                        openConfirm(player, dungeon, payload, "delete-spawn", payload, "spawns");
                    } else {
                        openSpawnEditor(player, dungeon, dungeon.getSpawn(payload));
                    }
                }
                case "spawn.create" -> {
                    if (dungeon != null) {
                        promptCreateSpawn(player, dungeon);
                    }
                }
                case "spawn.delete" -> {
                    if (dungeon != null) {
                        openConfirm(player, dungeon, holder.getTargetId(), "delete-spawn",
                                holder.getTargetId(), "spawn-editor");
                    }
                }
                case "spawn.field" -> {
                    if (dungeon != null) {
                        handleSpawnField(player, dungeon, dungeon.getSpawn(holder.getTargetId()),
                                payload, rightClick);
                    }
                }
                case "boss.entry" -> {
                    if (dungeon == null) {
                        openDungeonList(player, holder.getPage());
                    } else if (rightClick) {
                        openConfirm(player, dungeon, payload, "delete-boss", payload, "bosses");
                    } else {
                        openBossEditor(player, dungeon, dungeon.getBoss(payload));
                    }
                }
                case "boss.create" -> {
                    if (dungeon != null) {
                        promptCreateBoss(player, dungeon);
                    }
                }
                case "boss.delete" -> {
                    if (dungeon != null) {
                        openConfirm(player, dungeon, holder.getTargetId(), "delete-boss",
                                holder.getTargetId(), "boss-editor");
                    }
                }
                case "boss.equipment" -> {
                    if (dungeon != null) {
                        openEquipmentEditor(player, dungeon, null, dungeon.getBoss(holder.getTargetId()));
                    }
                }
                case "boss.field" -> {
                    if (dungeon != null) {
                        handleBossField(player, dungeon, dungeon.getBoss(holder.getTargetId()),
                                payload, rightClick);
                    }
                }
                case "entity.pick" -> {
                    if (dungeon != null) {
                        String mode = holder.getData().getOrDefault("entity-mode", "spawn");
                        if (mode.equals("boss")) {
                            BossDefinition boss = dungeon.getBoss(holder.getTargetId());
                            if (boss != null) {
                                applyBossEntity(boss, payload);
                                save(dungeon);
                                msg(player, "saved", Map.of("dungeon", dungeon.getId()));
                                openBossEditor(player, dungeon, boss);
                            }
                        } else {
                            SpawnPoint spawn = dungeon.getSpawn(holder.getTargetId());
                            if (spawn != null) {
                                applySpawnEntity(spawn, payload);
                                save(dungeon);
                                msg(player, "saved", Map.of("dungeon", dungeon.getId()));
                                openSpawnEditor(player, dungeon, spawn);
                            }
                        }
                    }
                }
                case "loot.entry" -> {
                    if (dungeon == null) {
                        openDungeonList(player, holder.getPage());
                    } else if (holder.getMenu().equals("loot-list")) {
                        LootTable table = dungeon.getLootTable(payload);
                        if (rightClick) {
                            if (table != null) {
                                openConfirm(player, dungeon, table.getId(), "delete-loot",
                                        table.getId(), "loot-list");
                            } else {
                                openLootList(player, dungeon, holder.getPage());
                            }
                        } else {
                            openLootEditor(player, dungeon, table, 0);
                        }
                    } else if (rightClick) {
                        LootTable table = dungeon.getLootTable(holder.getTargetId());
                        int index = parseInt(payload, -1);
                        if (table != null && index >= 0 && index < table.getEntries().size()) {
                            openConfirm(player, dungeon, table.getId() + " #" + (index + 1),
                                    "delete-loot-entry", table.getId() + "::" + index, "loot-editor");
                        } else {
                            openLootEditor(player, dungeon, table, holder.getPage());
                        }
                    } else {
                        LootTable table = dungeon.getLootTable(holder.getTargetId());
                        openLootEntryEditor(player, dungeon, table, parseInt(payload, -1));
                    }
                }
                case "loot.create" -> {
                    if (dungeon != null) {
                        promptCreateLoot(player, dungeon);
                    }
                }
                case "loot.delete" -> {
                    if (dungeon != null) {
                        openConfirm(player, dungeon, holder.getTargetId(), "delete-loot",
                                holder.getTargetId(), "loot-editor");
                    }
                }
                case "loot.field" -> {
                    if (dungeon != null) {
                        LootTable table = dungeon.getLootTable(holder.getTargetId());
                        if (table == null) {
                            openLootList(player, dungeon, holder.getPage());
                            return;
                        }
                        if ("unique".equals(payload)) {
                            table.setUnique(!table.isUnique());
                            save(dungeon);
                            openLootEditor(player, dungeon, table, holder.getPage());
                        } else {
                            promptLootRolls(player, dungeon, table);
                        }
                    }
                }
                case "loot.entry-field" -> handleLootEntryField(player, dungeon, holder, payload, rightClick);
                case "loot.add-held" -> {
                    if (dungeon != null) {
                        LootTable table = dungeon.getLootTable(holder.getTargetId());
                        if (table != null) {
                            ItemStack hand = player.getInventory().getItemInMainHand();
                            if (hand.getType().isAir()) {
                                msg(player, "empty-hand", Map.of());
                            } else {
                                table.getEntries().add(new LootEntry(hand.clone()));
                                table.markEntriesChanged();
                                save(dungeon);
                                msg(player, "saved", Map.of("dungeon", dungeon.getId()));
                            }
                            openLootEditor(player, dungeon, table, holder.getPage());
                        }
                    }
                }
                case "loot.remove-entry" -> {
                    if (dungeon != null) {
                        LootTable table = dungeon.getLootTable(holder.getTargetId());
                        int index = parseInt(payload, -1);
                        if (table != null && index >= 0 && index < table.getEntries().size()) {
                            table.getEntries().remove(index);
                            table.markEntriesChanged();
                            save(dungeon);
                        }
                        if (table != null) {
                            openLootEditor(player, dungeon, table, holder.getPage());
                        }
                    }
                }
                case "loot.pick" -> {
                    if (dungeon != null) {
                        applyLootChoice(player, dungeon, holder, payload);
                    }
                }
                case "loot.clear" -> {
                    if (dungeon != null) {
                        applyLootChoice(player, dungeon, holder, "");
                    }
                }
                case "chest.entry" -> {
                    if (dungeon == null) {
                        openDungeonList(player, holder.getPage());
                    } else if (rightClick) {
                        openConfirm(player, dungeon, payload, "delete-chest", payload, "chests");
                    } else {
                        openChestEditor(player, dungeon, dungeon.getChest(payload));
                    }
                }
                case "chest.create" -> {
                    if (dungeon != null) {
                        promptCreateChest(player, dungeon);
                    }
                }
                case "chest.delete" -> {
                    if (dungeon != null) {
                        openConfirm(player, dungeon, holder.getTargetId(), "delete-chest",
                                holder.getTargetId(), "chest-editor");
                    }
                }
                case "chest.field" -> {
                    if (dungeon != null) {
                        handleChestField(player, dungeon, dungeon.getChest(holder.getTargetId()),
                                payload, rightClick);
                    }
                }
                case "reward.entry" -> {
                    if (dungeon == null) {
                        openDungeonList(player, holder.getPage());
                    } else if (rightClick) {
                        openConfirm(player, dungeon, payload, "delete-reward", payload, "rewards");
                    } else {
                        openRewardEditor(player, dungeon, dungeon.getReward(payload));
                    }
                }
                case "reward.create" -> {
                    if (dungeon != null) {
                        promptCreateReward(player, dungeon);
                    }
                }
                case "reward.delete" -> {
                    if (dungeon != null) {
                        openConfirm(player, dungeon, holder.getTargetId(), "delete-reward",
                                holder.getTargetId(), "reward-editor");
                    }
                }
                case "reward.field" -> {
                    if (dungeon != null) {
                        handleRewardField(player, dungeon, dungeon.getReward(holder.getTargetId()),
                                payload, rightClick);
                    }
                }
                case "confirm.yes" -> confirmDelete(player, holder, dungeon);
                case "confirm.no" -> {
                    String actionName = holder.getData().getOrDefault("confirm-action", "");
                    if (actionName.equals("delete-loot-entry")) {
                        String[] confirmParts = holder.getTargetId().split("::", 2);
                        LootTable table = dungeon == null || confirmParts.length == 0
                                ? null : dungeon.getLootTable(confirmParts[0]);
                        openLootEditor(player, dungeon, table, 0);
                    } else {
                        openMenu(player, holder.getData().getOrDefault("confirm-return",
                                "main"), holder.getDungeonId(), holder.getTargetId(), 0);
                    }
                }
                default -> {
                    // Accion no reconocida: no se hace nada.
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Error procesando clic del GUI: " + exception.getMessage());
            msg(player, "error", Map.of());
            if (dungeon != null) {
                openDungeonEditor(player, dungeon);
            }
        }
    }

    private void confirmDelete(Player player, GuiHolder holder, Dungeon dungeon) {
        String action = holder.getData().getOrDefault("confirm-action", "");
        String target = holder.getTargetId();
        String returnMenu = holder.getData().getOrDefault("confirm-return", "main");
        if (dungeon == null && !action.equals("delete-dungeon")) {
            openMain(player);
            return;
        }
        switch (action) {
            case "delete-dungeon" -> {
                plugin.getDungeonManager().delete(target);
                msg(player, "deleted", Map.of("dungeon", target));
                openDungeonList(player, 0);
                return;
            }
            case "delete-spawn" -> {
                dungeon.getSpawns().remove(target);
                save(dungeon);
                msg(player, "deleted", Map.of("dungeon", target));
                openSpawns(player, dungeon);
                return;
            }
            case "delete-boss" -> {
                dungeon.getBosses().remove(target);
                plugin.getRepository().deleteBoss(dungeon, target);
                msg(player, "deleted", Map.of("dungeon", target));
                openBosses(player, dungeon);
                return;
            }
            case "delete-loot" -> {
                dungeon.getLootTables().remove(target);
                plugin.getRepository().deleteLootTable(dungeon, target);
                msg(player, "deleted", Map.of("dungeon", target));
                openLootList(player, dungeon);
                return;
            }
            case "delete-loot-entry" -> {
                String[] parts = target == null ? new String[0] : target.split("::", 2);
                LootTable table = parts.length == 0 ? null : dungeon.getLootTable(parts[0]);
                int index = parts.length < 2 ? -1 : parseInt(parts[1], -1);
                if (table != null && index >= 0 && index < table.getEntries().size()) {
                    table.getEntries().remove(index);
                    table.markEntriesChanged();
                    save(dungeon);
                }
                openLootEditor(player, dungeon, table, 0);
                return;
            }
            case "delete-chest" -> {
                dungeon.getChests().remove(target);
                save(dungeon);
                msg(player, "deleted", Map.of("dungeon", target));
                openChests(player, dungeon);
                return;
            }
            case "delete-reward" -> {
                dungeon.getRewards().remove(target);
                save(dungeon);
                msg(player, "deleted", Map.of("dungeon", target));
                openRewards(player, dungeon);
                return;
            }
            default -> {
            }
        }
        openMenu(player, returnMenu, dungeon == null ? null : dungeon.getId(), null, 0);
    }

    // ------------------------------------------------------------------
    // Creacion mediante prompt
    // ------------------------------------------------------------------

    private void promptCreateDungeon(Player player) {
        startPrompt(player, "dungeon-id", Map.of(),
                () -> openDungeonList(player, 0),
                input -> {
                    String id = input.toLowerCase(Locale.ROOT);
                    if (!validId(id)) {
                        msg(player, "invalid-id", Map.of("value", input));
                        openDungeonList(player, 0);
                        return;
                    }
                    Dungeon dungeon = plugin.getDungeonManager().create(id);
                    if (dungeon == null) {
                        msg(player, "exists", Map.of("dungeon", id));
                        openDungeonList(player, 0);
                        return;
                    }
                    msg(player, "created", Map.of("dungeon", id));
                    openDungeonEditor(player, dungeon);
                });
    }

    private void promptCreateSpawn(Player player, Dungeon dungeon) {
        startPrompt(player, "spawn-id", Map.of("dungeon", dungeon.getId()),
                () -> openSpawns(player, dungeon),
                input -> {
                    String id = input.toLowerCase(Locale.ROOT);
                    if (!validId(id)) {
                        msg(player, "invalid-id", Map.of("value", input));
                        openSpawns(player, dungeon);
                        return;
                    }
                    if (dungeon.getSpawns().containsKey(id)) {
                        msg(player, "exists", Map.of("dungeon", id));
                        openSpawns(player, dungeon);
                        return;
                    }
                    SpawnPoint spawn = new SpawnPoint(id);
                    spawn.setLocation(player.getLocation().clone());
                    dungeon.getSpawns().put(id, spawn);
                    save(dungeon);
                    msg(player, "created", Map.of("dungeon", id));
                    openSpawnEditor(player, dungeon, spawn);
                });
    }

    private void promptCreateBoss(Player player, Dungeon dungeon) {
        startPrompt(player, "boss-id", Map.of("dungeon", dungeon.getId()),
                () -> openBosses(player, dungeon),
                input -> {
                    String id = input.toLowerCase(Locale.ROOT);
                    if (!validId(id)) {
                        msg(player, "invalid-id", Map.of("value", input));
                        openBosses(player, dungeon);
                        return;
                    }
                    if (dungeon.getBosses().containsKey(id)) {
                        msg(player, "exists", Map.of("dungeon", id));
                        openBosses(player, dungeon);
                        return;
                    }
                    BossDefinition boss = new BossDefinition(id);
                    boss.setLocation(player.getLocation().clone());
                    dungeon.getBosses().put(id, boss);
                    save(dungeon);
                    msg(player, "created", Map.of("dungeon", id));
                    openBossEditor(player, dungeon, boss);
                });
    }

    private void promptCreateLoot(Player player, Dungeon dungeon) {
        startPrompt(player, "loot-id", Map.of("dungeon", dungeon.getId()),
                () -> openLootList(player, dungeon),
                input -> {
                    String id = input.toLowerCase(Locale.ROOT);
                    if (!validId(id)) {
                        msg(player, "invalid-id", Map.of("value", input));
                        openLootList(player, dungeon);
                        return;
                    }
                    if (dungeon.getLootTables().containsKey(id)) {
                        msg(player, "exists", Map.of("dungeon", id));
                        openLootList(player, dungeon);
                        return;
                    }
                    LootTable table = new LootTable(id);
                    dungeon.getLootTables().put(id, table);
                    plugin.getRepository().saveLootTable(dungeon, table);
                    msg(player, "created", Map.of("dungeon", id));
                    openLootEditor(player, dungeon, table, 0);
                });
    }

    private void promptCreateChest(Player player, Dungeon dungeon) {
        Block block = player.getTargetBlockExact(10);
        if (block == null || !(block.getState() instanceof Chest)) {
            msg(player, "target-not-chest", Map.of());
            openChests(player, dungeon);
            return;
        }
        startPrompt(player, "chest-id", Map.of("dungeon", dungeon.getId()),
                () -> openChests(player, dungeon),
                input -> {
                    String id = input.toLowerCase(Locale.ROOT);
                    if (!validId(id)) {
                        msg(player, "invalid-id", Map.of("value", input));
                        openChests(player, dungeon);
                        return;
                    }
                    if (dungeon.getChests().containsKey(id)) {
                        msg(player, "exists", Map.of("dungeon", id));
                        openChests(player, dungeon);
                        return;
                    }
                    ChestSpawn chest = ChestSpawn.capture(id, block);
                    dungeon.getChests().put(id, chest);
                    save(dungeon);
                    msg(player, "created", Map.of("dungeon", id));
                    openChestEditor(player, dungeon, chest);
                });
    }

    private void promptCreateReward(Player player, Dungeon dungeon) {
        Block block = player.getTargetBlockExact(10);
        if (block == null) {
            msg(player, "target-not-block", Map.of());
            openRewards(player, dungeon);
            return;
        }
        startPrompt(player, "reward-id", Map.of("dungeon", dungeon.getId()),
                () -> openRewards(player, dungeon),
                input -> {
                    String id = input.toLowerCase(Locale.ROOT);
                    if (!validId(id)) {
                        msg(player, "invalid-id", Map.of("value", input));
                        openRewards(player, dungeon);
                        return;
                    }
                    if (dungeon.getRewards().containsKey(id)) {
                        msg(player, "exists", Map.of("dungeon", id));
                        openRewards(player, dungeon);
                        return;
                    }
                    RewardBlock reward = RewardBlock.capture(id, block);
                    dungeon.getRewards().put(id, reward);
                    save(dungeon);
                    msg(player, "created", Map.of("dungeon", id));
                    openRewardEditor(player, dungeon, reward);
                });
    }

    // ------------------------------------------------------------------
    // Edicion de ajustes
    // ------------------------------------------------------------------

    private void promptSetting(Player player, Dungeon dungeon, String key) {
        startPrompt(player, "value", Map.of("key", key, "current",
                        settingValue(dungeon.getSettings(), key)),
                () -> openSettings(player, dungeon),
                input -> {
                    if (!dungeon.getSettings().set(key, input)) {
                        msg(player, "invalid-value", Map.of("value", input));
                    } else {
                        save(dungeon);
                        msg(player, "saved", Map.of("dungeon", dungeon.getId()));
                    }
                    openSettings(player, dungeon);
                });
    }

    private void cycleSetting(Player player, Dungeon dungeon, String key) {
        DungeonSettings settings = dungeon.getSettings();
        String value = settingValue(settings, key);
        if (key.equals("complete-on")) {
            CompleteMode[] modes = CompleteMode.values();
            CompleteMode current = settings.getCompleteOn();
            settings.set(key, modes[(current.ordinal() + 1) % modes.length].name());
        } else if (key.equals("bossbar.color")) {
            settings.set(key, next(BOSS_BAR_COLORS, settings.getBossBar().getColor()));
        } else if (key.equals("bossbar.style")) {
            settings.set(key, next(BOSS_BAR_STYLES, settings.getBossBar().getStyle()));
        } else if (isBooleanKey(key)) {
            settings.set(key, String.valueOf(!booleanSettingValue(settings, key)));
        } else if (isTextKey(key)) {
            promptSetting(player, dungeon, key);
            return;
        } else {
            settings.set(key, nextNumber(key, value));
        }
        save(dungeon);
        openSettings(player, dungeon);
    }

    /**
     * Lee el valor booleano real. No debe usarse settingValue() aquí porque
     * ese método devuelve texto MiniMessage como "<green>Si</green>".
     */
    private boolean booleanSettingValue(DungeonSettings settings, String key) {
        return switch (key) {
            case "auto-enter" -> settings.isAutoEnter();
            case "auto-start" -> settings.isAutoStart();
            case "exit-on-leave-region" -> settings.isExitOnLeaveRegion();
            case "leave-on-death" -> settings.isLeaveOnDeath();
            case "reset-on-empty" -> settings.isResetOnEmpty();
            case "allow-rejoin" -> settings.isAllowRejoin();
            case "protection" -> settings.isProtection();
            case "allow-chest-break" -> settings.isAllowChestBreak();
            case "bosses-after-waves" -> settings.isBossesAfterWaves();
            case "bossbar.enabled" -> settings.getBossBar().isEnabled();
            default -> false;
        };
    }

    private void promptLootRolls(Player player, Dungeon dungeon, LootTable table) {
        startPrompt(player, "rolls", Map.of("loot", table.getId()),
                () -> openLootEditor(player, dungeon, table, 0),
                input -> {
                    String[] parts = input.split("-", 2);
                    try {
                        int min = Integer.parseInt(parts[0].trim());
                        int max = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : min;
                        table.setRolls(Math.max(1, min), Math.max(Math.max(1, min), max));
                        save(dungeon);
                        msg(player, "saved", Map.of("dungeon", dungeon.getId()));
                    } catch (NumberFormatException exception) {
                        msg(player, "invalid-value", Map.of("value", input));
                    }
                    openLootEditor(player, dungeon, table, 0);
                });
    }

    private void handleLootEntryField(Player player, Dungeon dungeon, GuiHolder holder,
                                      String field, boolean rightClick) {
        if (dungeon == null) {
            return;
        }
        LootTable table = dungeon.getLootTable(holder.getTargetId());
        int index = parseInt(holder.getData().get("entry-index"), -1);
        if (table == null || index < 0 || index >= table.getEntries().size()) {
            openLootEditor(player, dungeon, table, 0);
            return;
        }
        LootEntry entry = table.getEntries().get(index);
        if (field.equals("message") || rightClick) {
            String current = switch (field) {
                case "amount" -> entry.getMinAmount() == entry.getMaxAmount()
                        ? String.valueOf(entry.getMinAmount())
                        : entry.getMinAmount() + "-" + entry.getMaxAmount();
                case "chance" -> String.valueOf(entry.getChance());
                case "weight" -> String.valueOf(entry.getWeight());
                default -> entry.getMessage();
            };
            startPrompt(player, "value", Map.of("key", field, "current", current),
                    () -> openLootEntryEditor(player, dungeon, table, index), input -> {
                        if (!setLootEntryField(entry, field, input)) {
                            msg(player, "invalid-value", Map.of("value", input));
                        } else {
                            save(dungeon);
                            msg(player, "saved", Map.of("dungeon", dungeon.getId()));
                        }
                        openLootEntryEditor(player, dungeon, table, index);
                    });
            return;
        }
        switch (field) {
            case "chance" -> entry.setChance(entry.getChance() >= 1.0D ? 0.0D : entry.getChance() + 0.05D);
            case "weight" -> entry.setWeight(entry.getWeight() + 1);
            case "amount" -> entry.setAmount(entry.getMinAmount() + 1, entry.getMaxAmount() + 1);
            default -> {
                return;
            }
        }
        save(dungeon);
        openLootEntryEditor(player, dungeon, table, index);
    }

    private boolean setLootEntryField(LootEntry entry, String field, String value) {
        try {
            switch (field) {
                case "amount" -> {
                    String[] parts = value.split("[-.]{1,2}", 2);
                    int min = Integer.parseInt(parts[0].trim());
                    int max = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : min;
                    entry.setAmount(min, max);
                }
                case "chance" -> {
                    double chance = Double.parseDouble(value.replace(',', '.'));
                    if (chance > 1.0D) {
                        chance /= 100.0D;
                    }
                    entry.setChance(chance);
                }
                case "weight" -> entry.setWeight(Integer.parseInt(value));
                case "message" -> entry.setMessage(value);
                default -> { return false; }
            }
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void handleSpawnField(Player player, Dungeon dungeon, SpawnPoint spawn,
                                  String field, boolean rightClick) {
        if (spawn == null) {
            openSpawns(player, dungeon);
            return;
        }
        if (field.equals("entity")) {
            openEntityPicker(player, dungeon, spawn, null, 0);
            return;
        }
        if (field.equals("loot-table")) {
            openLootPicker(player, dungeon, "spawn", spawn.getId(), 0);
            return;
        }
        if (field.equals("location")) {
            spawn.setLocation(player.getLocation().clone());
            save(dungeon);
            msg(player, "position-set", Map.of("dungeon", dungeon.getId()));
            openSpawnEditor(player, dungeon, spawn);
            return;
        }
        if (rightClick || field.equals("name")) {
            startPrompt(player, "value", Map.of("key", field, "current", spawnValue(spawn, field)),
                    () -> openSpawnEditor(player, dungeon, spawn),
                    input -> {
                        if (!setSpawnField(spawn, field, input)) {
                            msg(player, "invalid-value", Map.of("value", input));
                        } else {
                            save(dungeon);
                            msg(player, "saved", Map.of("dungeon", dungeon.getId()));
                        }
                        openSpawnEditor(player, dungeon, spawn);
                    });
            return;
        }
        cycleSpawnField(spawn, field);
        save(dungeon);
        openSpawnEditor(player, dungeon, spawn);
    }

    private void cycleSpawnField(SpawnPoint spawn, String field) {
        switch (field) {
            case "type" -> spawn.setType(spawn.getType() == MobType.MYTHIC ? MobType.VANILLA : MobType.MYTHIC);
            case "amount" -> spawn.setAmount(spawn.getAmount() + 1);
            case "max-alive" -> spawn.setMaxAlive(spawn.getMaxAlive() + 1);
            case "respawn-seconds" -> spawn.setRespawnSeconds(spawn.getRespawnSeconds() + 5);
            case "spawn-interval-seconds" -> spawn.setSpawnIntervalSeconds(spawn.getSpawnIntervalSeconds() + 1);
            case "radius" -> spawn.setRadius(spawn.getRadius() + 1.0D);
            case "min-player-distance" -> spawn.setMinPlayerDistance(spawn.getMinPlayerDistance() + 4);
            case "require-ground" -> spawn.setRequireGround(!spawn.isRequireGround());
            case "health" -> spawn.setHealth(spawn.getHealth() + 10.0D);
            case "damage" -> spawn.setDamage(spawn.getDamage() + 1.0D);
            case "speed" -> spawn.setSpeed(spawn.getSpeed() + 0.05D);
            case "follow-range" -> spawn.setFollowRange(spawn.getFollowRange() + 4.0D);
            case "glowing" -> spawn.setGlowing(!spawn.isGlowing());
            case "baby" -> spawn.setBaby(!spawn.isBaby());
            case "ai" -> spawn.setAi(!spawn.isAi());
            case "persistent" -> spawn.setPersistent(!spawn.isPersistent());
            case "clear-vanilla-drops" -> spawn.setClearVanillaDrops(!spawn.isClearVanillaDrops());
            case "burn-in-daylight" -> spawn.setBurnInDaylight(!spawn.isBurnInDaylight());
            default -> {
            }
        }
    }

    private boolean setSpawnField(SpawnPoint spawn, String field, String value) {
        try {
            switch (field) {
                case "type" -> spawn.setType(MobType.from(value, spawn.getType()));
                case "entity" -> applySpawnEntity(spawn, value);
                case "amount" -> spawn.setAmount(Integer.parseInt(value));
                case "max-alive" -> spawn.setMaxAlive(Integer.parseInt(value));
                case "respawn-seconds" -> spawn.setRespawnSeconds(ConfigValues.parseDuration(value, spawn.getRespawnSeconds()));
                case "spawn-interval-seconds" -> spawn.setSpawnIntervalSeconds(ConfigValues.parseDuration(value, spawn.getSpawnIntervalSeconds()));
                case "radius" -> spawn.setRadius(Double.parseDouble(value));
                case "min-player-distance" -> spawn.setMinPlayerDistance(Integer.parseInt(value));
                case "require-ground" -> spawn.setRequireGround(Boolean.parseBoolean(value));
                case "health" -> spawn.setHealth(Double.parseDouble(value));
                case "damage" -> spawn.setDamage(Double.parseDouble(value));
                case "speed" -> spawn.setSpeed(Double.parseDouble(value));
                case "follow-range" -> spawn.setFollowRange(Double.parseDouble(value));
                case "name" -> spawn.setName(value);
                case "glowing" -> spawn.setGlowing(Boolean.parseBoolean(value));
                case "baby" -> spawn.setBaby(Boolean.parseBoolean(value));
                case "ai" -> spawn.setAi(Boolean.parseBoolean(value));
                case "persistent" -> spawn.setPersistent(Boolean.parseBoolean(value));
                case "clear-vanilla-drops" -> spawn.setClearVanillaDrops(Boolean.parseBoolean(value));
                case "burn-in-daylight" -> spawn.setBurnInDaylight(Boolean.parseBoolean(value));
                case "loot-table" -> spawn.setLootTable(value);
                default -> {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void handleBossField(Player player, Dungeon dungeon, BossDefinition boss,
                                 String itemKey, boolean rightClick) {
        if (boss == null) {
            openBosses(player, dungeon);
            return;
        }
        String field = bossKey(itemKey);
        if (field.equals("entity")) {
            openEntityPicker(player, dungeon, null, boss, 0);
            return;
        }
        if (field.equals("loot-table")) {
            openLootPicker(player, dungeon, "boss", boss.getId(), 0);
            return;
        }
        if (field.equals("location")) {
            boss.setLocation(player.getLocation().clone());
            save(dungeon);
            msg(player, "position-set", Map.of("dungeon", dungeon.getId()));
            openBossEditor(player, dungeon, boss);
            return;
        }
        if (rightClick || field.equals("name") || field.equals("bossbar.title")) {
            startPrompt(player, "value", Map.of("key", field, "current", bossValue(boss, itemKey)),
                    () -> openBossEditor(player, dungeon, boss),
                    input -> {
                        if (!setBossField(boss, field, input)) {
                            msg(player, "invalid-value", Map.of("value", input));
                        } else {
                            save(dungeon);
                            msg(player, "saved", Map.of("dungeon", dungeon.getId()));
                        }
                        openBossEditor(player, dungeon, boss);
                    });
            return;
        }
        cycleBossField(boss, field);
        save(dungeon);
        openBossEditor(player, dungeon, boss);
    }

    private void cycleBossField(BossDefinition boss, String field) {
        switch (field) {
            case "type" -> boss.setType(boss.getType() == MobType.MYTHIC ? MobType.VANILLA : MobType.MYTHIC);
            case "health" -> boss.setHealth(boss.getHealth() + 10.0D);
            case "damage" -> boss.setDamage(boss.getDamage() + 1.0D);
            case "speed" -> boss.setSpeed(boss.getSpeed() + 0.05D);
            case "follow-range" -> boss.setFollowRange(boss.getFollowRange() + 4.0D);
            case "glowing" -> boss.setGlowing(!boss.isGlowing());
            case "baby" -> boss.setBaby(!boss.isBaby());
            case "ai" -> boss.setAi(!boss.isAi());
            case "persistent" -> boss.setPersistent(!boss.isPersistent());
            case "clear-vanilla-drops" -> boss.setClearVanillaDrops(!boss.isClearVanillaDrops());
            case "announce-spawn" -> boss.setAnnounceSpawn(!boss.isAnnounceSpawn());
            case "announce-death" -> boss.setAnnounceDeath(!boss.isAnnounceDeath());
            case "burn-in-daylight" -> boss.setBurnInDaylight(!boss.isBurnInDaylight());
            case "bossbar.enabled" -> boss.getBossBar().setEnabled(!boss.getBossBar().isEnabled());
            case "bossbar.color" -> boss.getBossBar().setColor(next(BOSS_BAR_COLORS, boss.getBossBar().getColor()));
            case "bossbar.style" -> boss.getBossBar().setStyle(next(BOSS_BAR_STYLES, boss.getBossBar().getStyle()));
            case "bossbar.radius" -> boss.getBossBar().setRadius(boss.getBossBar().getRadius() + 8.0D);
            default -> {
            }
        }
    }

    private boolean setBossField(BossDefinition boss, String field, String value) {
        try {
            BossBarSettings bar = boss.getBossBar();
            switch (field) {
                case "type" -> boss.setType(MobType.from(value, boss.getType()));
                case "entity" -> applyBossEntity(boss, value);
                case "health" -> boss.setHealth(Double.parseDouble(value));
                case "damage" -> boss.setDamage(Double.parseDouble(value));
                case "speed" -> boss.setSpeed(Double.parseDouble(value));
                case "follow-range" -> boss.setFollowRange(Double.parseDouble(value));
                case "name" -> boss.setDisplayName(value);
                case "glowing" -> boss.setGlowing(Boolean.parseBoolean(value));
                case "baby" -> boss.setBaby(Boolean.parseBoolean(value));
                case "ai" -> boss.setAi(Boolean.parseBoolean(value));
                case "persistent" -> boss.setPersistent(Boolean.parseBoolean(value));
                case "clear-vanilla-drops" -> boss.setClearVanillaDrops(Boolean.parseBoolean(value));
                case "announce-spawn" -> boss.setAnnounceSpawn(Boolean.parseBoolean(value));
                case "announce-death" -> boss.setAnnounceDeath(Boolean.parseBoolean(value));
                case "burn-in-daylight" -> boss.setBurnInDaylight(Boolean.parseBoolean(value));
                case "loot-table" -> boss.setLootTable(value);
                case "bossbar.enabled" -> bar.setEnabled(Boolean.parseBoolean(value));
                case "bossbar.title" -> bar.setTitle(value);
                case "bossbar.color" -> bar.setColor(value.toUpperCase(Locale.ROOT));
                case "bossbar.style" -> bar.setStyle(value.toUpperCase(Locale.ROOT));
                case "bossbar.radius" -> bar.setRadius(Double.parseDouble(value));
                default -> {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void handleChestField(Player player, Dungeon dungeon, ChestSpawn chest,
                                  String field, boolean rightClick) {
        if (chest == null) {
            openChests(player, dungeon);
            return;
        }
        if (field.equals("loot-table")) {
            openLootPicker(player, dungeon, "chest", chest.getId(), 0);
            return;
        }
        if (field.equals("location")) {
            Block block = player.getTargetBlockExact(10);
            if (block == null || !(block.getState() instanceof Chest)) {
                msg(player, "target-not-chest", Map.of());
                openChestEditor(player, dungeon, chest);
                return;
            }
            chest.setLocation(block.getLocation().clone());
            chest.setBlockData(block.getBlockData().getAsString());
            save(dungeon);
            msg(player, "position-set", Map.of("dungeon", dungeon.getId()));
            openChestEditor(player, dungeon, chest);
            return;
        }
        if (rightClick) {
            startPrompt(player, "value", Map.of("key", field, "current",
                            field.equals("enabled") ? yesNo(chest.isEnabled()) : duration(chest.getRefillSeconds())),
                    () -> openChestEditor(player, dungeon, chest),
                    input -> {
                        try {
                            switch (field) {
                                 case "refill-seconds" -> chest.setRefillSeconds(ConfigValues.parseDuration(input, chest.getRefillSeconds()));
                                case "enabled" -> chest.setEnabled(Boolean.parseBoolean(input));
                                default -> {
                                }
                            }
                            save(dungeon);
                            msg(player, "saved", Map.of("dungeon", dungeon.getId()));
                        } catch (NumberFormatException exception) {
                            msg(player, "invalid-value", Map.of("value", input));
                        }
                        openChestEditor(player, dungeon, chest);
                    });
            return;
        }
        if (field.equals("enabled")) {
            chest.setEnabled(!chest.isEnabled());
        } else if (field.equals("refill-seconds")) {
            chest.setRefillSeconds(chest.getRefillSeconds() + 30);
        }
        save(dungeon);
        openChestEditor(player, dungeon, chest);
    }

    private void handleRewardField(Player player, Dungeon dungeon, RewardBlock reward,
                                   String field, boolean rightClick) {
        if (reward == null) {
            openRewards(player, dungeon);
            return;
        }
        if (field.equals("loot-table")) {
            openLootPicker(player, dungeon, "reward", reward.getId(), 0);
            return;
        }
        if (field.equals("location")) {
            Block block = player.getTargetBlockExact(10);
            if (block == null) {
                msg(player, "target-not-block", Map.of());
            } else {
                reward.setLocation(block.getLocation());
                reward.setMaterial(block.getType());
                reward.setBlockData(block.getBlockData().getAsString());
                save(dungeon);
                msg(player, "position-set", Map.of("dungeon", dungeon.getId()));
            }
            openRewardEditor(player, dungeon, reward);
            return;
        }
        if (rightClick) {
            startPrompt(player, "value", Map.of("key", field, "current", rewardValue(reward, field)),
                    () -> openRewardEditor(player, dungeon, reward),
                    input -> {
                        try {
                            switch (field) {
                                 case "respawn-seconds" -> reward.setRespawnSeconds(ConfigValues.parseDuration(input, reward.getRespawnSeconds()));
                                case "one-time" -> reward.setOneTime(Boolean.parseBoolean(input));
                                case "direct-give" -> reward.setDirectGive(Boolean.parseBoolean(input));
                                case "announce" -> reward.setAnnounce(Boolean.parseBoolean(input));
                                default -> {
                                }
                            }
                            save(dungeon);
                            msg(player, "saved", Map.of("dungeon", dungeon.getId()));
                        } catch (NumberFormatException exception) {
                            msg(player, "invalid-value", Map.of("value", input));
                        }
                        openRewardEditor(player, dungeon, reward);
                    });
            return;
        }
        switch (field) {
            case "one-time" -> reward.setOneTime(!reward.isOneTime());
            case "direct-give" -> reward.setDirectGive(!reward.isDirectGive());
            case "announce" -> reward.setAnnounce(!reward.isAnnounce());
            case "respawn-seconds" -> reward.setRespawnSeconds(reward.getRespawnSeconds() + 30);
            default -> {
            }
        }
        save(dungeon);
        openRewardEditor(player, dungeon, reward);
    }

    private void applyLootChoice(Player player, Dungeon dungeon, GuiHolder holder, String lootId) {
        String type = holder.getData().getOrDefault("apply-type", "");
        String id = holder.getTargetId();
        switch (type) {
            case "spawn" -> {
                SpawnPoint spawn = dungeon.getSpawn(id);
                if (spawn != null) {
                    spawn.setLootTable(lootId);
                    save(dungeon);
                    openSpawnEditor(player, dungeon, spawn);
                }
            }
            case "boss" -> {
                BossDefinition boss = dungeon.getBoss(id);
                if (boss != null) {
                    boss.setLootTable(lootId);
                    save(dungeon);
                    openBossEditor(player, dungeon, boss);
                }
            }
            case "chest" -> {
                ChestSpawn chest = dungeon.getChest(id);
                if (chest != null) {
                    chest.setLootTable(lootId);
                    save(dungeon);
                    openChestEditor(player, dungeon, chest);
                }
            }
            case "reward" -> {
                RewardBlock reward = dungeon.getReward(id);
                if (reward != null) {
                    reward.setLootTable(lootId);
                    save(dungeon);
                    openRewardEditor(player, dungeon, reward);
                }
            }
            default -> openDungeonEditor(player, dungeon);
        }
        msg(player, "saved", Map.of("dungeon", dungeon.getId()));
    }

    // ------------------------------------------------------------------
    // Apertura generica
    // ------------------------------------------------------------------

    private void openMenu(Player player, String menu, String dungeonId, String targetId, int page) {
        Dungeon dungeon = dungeon(dungeonId);
        switch (menu) {
            case "main" -> openMain(player);
            case "dungeon-list" -> openDungeonList(player, page);
            case "player-dungeons" -> openPlayerDungeons(player, page);
            case "dungeon-editor" -> openDungeonEditor(player, dungeon);
            case "settings" -> openSettings(player, dungeon);
            case "spawns" -> openSpawns(player, dungeon, page);
            case "spawn-editor" -> openSpawnEditor(player, dungeon,
                    dungeon == null ? null : dungeon.getSpawn(targetId));
            case "bosses" -> openBosses(player, dungeon, page);
            case "boss-editor" -> openBossEditor(player, dungeon,
                    dungeon == null ? null : dungeon.getBoss(targetId));
            case "loot-list" -> openLootList(player, dungeon, page);
            case "loot-editor" -> openLootEditor(player, dungeon,
                    dungeon == null ? null : dungeon.getLootTable(targetId), page);
            case "chests" -> openChests(player, dungeon, page);
            case "chest-editor" -> openChestEditor(player, dungeon,
                    dungeon == null ? null : dungeon.getChest(targetId));
            case "rewards" -> openRewards(player, dungeon, page);
            case "reward-editor" -> openRewardEditor(player, dungeon,
                    dungeon == null ? null : dungeon.getReward(targetId));
            default -> openMain(player);
        }
    }

    // ------------------------------------------------------------------
    // Utilidades de construccion
    // ------------------------------------------------------------------

    private Inventory create(GuiHolder holder) {
        String menu = holder.getMenu();
        GuiConfig source = menuConfig(menu);
        Dungeon dungeon = dungeon(holder.getDungeonId());
        Map<String, String> titlePlaceholders = titlePlaceholders(menu, dungeon, holder.getTargetId());
        Inventory inventory = Bukkit.createInventory(holder, source.size(menu),
                Text.parse(source.title(menu, titlePlaceholders)));
        holder.setInventory(inventory);
        GuiConfig.ItemSpec fill = source.fill(menu);
        if (fill != null) {
            ItemStack item = build(source, fill, Map.of());
            if (item != null) {
                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    inventory.setItem(slot, item.clone());
                }
            }
        }
        return inventory;
    }

    private void button(Inventory inventory, GuiHolder holder, String menu, String key,
                        String action) {
        button(inventory, holder, menu, key, action, Map.of());
    }

    private void button(Inventory inventory, GuiHolder holder, String menu, String key,
                        String action, Map<String, String> placeholders) {
        GuiConfig source = menuConfig(menu);
        GuiConfig.ItemSpec spec = source.item(menu, key);
        if (spec == null || spec.slot() < 0 || spec.slot() >= inventory.getSize()) {
            return;
        }
        ItemStack item = build(source, spec, placeholders);
        if (item != null) {
            inventory.setItem(spec.slot(), item);
            holder.bind(spec.slot(), action);
        }
    }

    private void fillDynamic(Inventory inventory, GuiHolder holder, String menu, String templateKey,
                             List<ListEntry> entries, String action) {
        GuiConfig source = menuConfig(menu);
        GuiConfig.ItemSpec template = source.item(menu, templateKey);
        if (template == null) {
            return;
        }
        List<Integer> slots = source.listSlots(menu);
        if (slots.isEmpty()) {
            return;
        }
        int perPage = slots.size();
        int start = holder.getPage() * perPage;
        if (start >= entries.size() && holder.getPage() > 0) {
            start = Math.max(0, (entries.size() - 1) / perPage * perPage);
        }
        if (entries.isEmpty()) {
            GuiConfig.ItemSpec empty = source.item(menu, "empty");
            if (empty == null) {
                empty = template;
            }
            Map<String, String> ph = new HashMap<>();
            ph.put("total", "0");
            ItemStack item = build(source, empty, ph);
            if (item != null) {
                inventory.setItem(slots.get(0), item);
            }
            return;
        }
        for (int index = start; index < entries.size(); index++) {
            int slotIndex = index - start;
            if (slotIndex >= slots.size()) {
                break;
            }
            ListEntry entry = entries.get(index);
            ItemStack item = build(source, template, entry.placeholders());
            if (item == null) {
                continue;
            }
            int slot = slots.get(slotIndex);
            inventory.setItem(slot, item);
            holder.bind(slot, action + "|" + entry.id());
        }
    }

    private void fillPlayerDynamic(Inventory inventory, GuiHolder holder,
                                   List<ListEntry> entries, List<String> templateKeys) {
        GuiConfig source = playerConfig;
        List<Integer> slots = source.listSlots("player-dungeons");
        if (slots.isEmpty()) {
            return;
        }
        if (entries.isEmpty()) {
            GuiConfig.ItemSpec empty = source.item("player-dungeons", "empty");
            ItemStack item = build(source, empty, Map.of("total", "0"));
            if (item != null) {
                inventory.setItem(slots.get(0), item);
            }
            return;
        }
        int start = holder.getPage() * slots.size();
        if (start >= entries.size() && holder.getPage() > 0) {
            start = Math.max(0, (entries.size() - 1) / slots.size() * slots.size());
        }
        for (int index = start; index < entries.size(); index++) {
            int slotIndex = index - start;
            if (slotIndex >= slots.size()) {
                break;
            }
            String key = index < templateKeys.size() ? templateKeys.get(index) : "dungeon-entry";
            GuiConfig.ItemSpec spec = source.item("player-dungeons", key);
            if (spec == null) {
                spec = source.item("player-dungeons", "dungeon-entry");
            }
            ItemStack item = build(source, spec, entries.get(index).placeholders());
            if (item == null) {
                continue;
            }
            int slot = slots.get(slotIndex);
            inventory.setItem(slot, item);
            holder.bind(slot, "player.dungeon.entry|" + entries.get(index).id());
        }
    }

    private void navigation(Inventory inventory, GuiHolder holder, String menu, String returnMenu,
                            int page) {
        Dungeon dungeon = dungeon(holder.getDungeonId());
        Map<String, String> ph = base(dungeon);
        ph.put("page", String.valueOf(page + 1));
        button(inventory, holder, menu, "prev", "page|" + Math.max(0, page - 1), ph);
        button(inventory, holder, menu, "next", "page|" + (page + 1), ph);
        button(inventory, holder, menu, "back", "open|" + returnMenu, ph);
    }

    private GuiConfig menuConfig(String menu) {
        return "player-dungeons".equals(menu) ? playerConfig : config;
    }

    private ItemStack build(GuiConfig.ItemSpec spec, Map<String, String> placeholders) {
        return build(config, spec, placeholders);
    }

    private ItemStack build(GuiConfig source, GuiConfig.ItemSpec spec,
                            Map<String, String> placeholders) {
        if (spec == null) {
            return null;
        }
        Map<String, String> ph = placeholders == null ? Map.of() : placeholders;
        String materialName = source.apply(spec.material(), ph);
        ItemStack provided = providerItem(spec.provider(), spec.itemId(), materialName);
        ItemStack stack;
        if (provided != null) {
            stack = provided;
            stack.setAmount(Math.max(1, Math.min(64, spec.amount())));
        } else {
            Material material = source.material(materialName, Material.STONE);
            if (material == Material.AIR) {
                return null;
            }
            stack = new ItemStack(material, Math.max(1, Math.min(64, spec.amount())));
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        String name = source.apply(spec.name(), ph);
        meta.displayName(Text.parse(name == null || name.isBlank() ? " " : name));
        List<Component> lore = new ArrayList<>();
        for (String line : spec.lore()) {
            lore.add(Text.parse(source.apply(line, ph)));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        if (spec.glow()) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (spec.customModelData() != null) {
            meta.setCustomModelData(spec.customModelData());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack providerItem(String provider, String itemId, String material) {
        String selectedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        String selectedId = itemId == null ? "" : itemId.trim();
        if (selectedId.isEmpty() && material != null) {
            int separator = material.indexOf(':');
            if (separator > 0) {
                selectedProvider = material.substring(0, separator).toLowerCase(Locale.ROOT);
                selectedId = material.substring(separator + 1);
            }
        }
        if (selectedId.isEmpty()) {
            return null;
        }
        try {
            if (selectedProvider.equals("nexo")) {
                Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
                Object result = api.getMethod("itemFromId", String.class).invoke(null, selectedId);
                return result instanceof ItemStack item ? item : null;
            }
            if (selectedProvider.equals("oraxen")) {
                Class<?> api = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
                Object result = api.getMethod("getItemById", String.class).invoke(null, selectedId);
                return result instanceof ItemStack item ? item : null;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // El proveedor es opcional; si no existe se usa material vanilla.
        }
        return null;
    }

    private void open(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            return;
        }
        player.openInventory(inventory);
    }

    // ------------------------------------------------------------------
    // Placeholders y valores
    // ------------------------------------------------------------------

    private Map<String, String> base(Dungeon dungeon) {
        Map<String, String> ph = new LinkedHashMap<>();
        if (dungeon == null) {
            ph.put("dungeon", "-");
            ph.put("dungeon_name", "-");
            return ph;
        }
        DungeonInstance instance = plugin.getDungeonManager().getInstance(dungeon.getId());
        ph.put("dungeon", dungeon.getId());
        ph.put("dungeon_name", dungeon.getDisplayName());
        ph.put("name", dungeon.getDisplayName());
        ph.put("enabled", yesNo(dungeon.isEnabled()));
        ph.put("ready", yesNo(dungeon.isReady()));
        ph.put("state", stateLabel(instance));
        ph.put("players", String.valueOf(instance == null ? 0 : instance.getPlayerCount()));
        ph.put("max_players", String.valueOf(dungeon.getSettings().getMaxPlayers()));
        ph.put("min_players", String.valueOf(dungeon.getSettings().getMinPlayers()));
        ph.put("region", dungeon.getRegion() == null ? "-" : dungeon.getRegion().describe());
        ph.put("entry", location(dungeon.getEntry()));
        ph.put("exit", location(dungeon.getExit()));
        ph.put("spawns", String.valueOf(dungeon.getSpawns().size()));
        ph.put("bosses", String.valueOf(dungeon.getBosses().size()));
        ph.put("chests", String.valueOf(dungeon.getChests().size()));
        ph.put("rewards", String.valueOf(dungeon.getRewards().size()));
        ph.put("loot", String.valueOf(dungeon.getLootTables().size()));
        return ph;
    }

    private Map<String, String> titlePlaceholders(String menu, Dungeon dungeon, String targetId) {
        Map<String, String> ph = base(dungeon);
        if (dungeon == null || targetId == null) {
            return ph;
        }
        switch (menu) {
            case "spawn-editor" -> {
                SpawnPoint spawn = dungeon.getSpawn(targetId);
                if (spawn != null) {
                    ph.putAll(spawnPlaceholders(spawn));
                }
            }
            case "boss-editor" -> {
                BossDefinition boss = dungeon.getBoss(targetId);
                if (boss != null) {
                    ph.putAll(bossPlaceholders(boss));
                }
            }
            case "equipment" -> {
                SpawnPoint spawn = dungeon.getSpawn(targetId);
                BossDefinition boss = dungeon.getBoss(targetId);
                if (spawn != null) {
                    ph.putAll(spawnPlaceholders(spawn));
                    ph.put("target", spawn.getId());
                } else if (boss != null) {
                    ph.putAll(bossPlaceholders(boss));
                    ph.put("target", boss.getId());
                }
            }
            case "loot-editor", "loot-picker" -> {
                LootTable table = dungeon.getLootTable(targetId);
                if (table != null) {
                    ph.putAll(lootPlaceholders(table));
                }
            }
            case "loot-entry-editor" -> {
                LootTable table = dungeon.getLootTable(targetId);
                if (table != null) {
                    ph.putAll(lootPlaceholders(table));
                }
            }
            case "chest-editor" -> {
                ChestSpawn chest = dungeon.getChest(targetId);
                if (chest != null) {
                    ph.putAll(chestPlaceholders(chest));
                }
            }
            case "reward-editor" -> {
                RewardBlock reward = dungeon.getReward(targetId);
                if (reward != null) {
                    ph.putAll(rewardPlaceholders(reward));
                }
            }
            default -> {
            }
        }
        return ph;
    }

    private Map<String, String> spawnPlaceholders(SpawnPoint spawn) {
        Map<String, String> ph = new LinkedHashMap<>();
        ph.put("spawn", spawn.getId());
        ph.put("entity", spawn.getEntity());
        ph.put("type", spawn.getType().name());
        ph.put("amount", String.valueOf(spawn.getAmount()));
        ph.put("max_alive", String.valueOf(spawn.getMaxAlive()));
         ph.put("respawn", duration(spawn.getRespawnSeconds()));
         ph.put("interval", duration(spawn.getSpawnIntervalSeconds()));
        ph.put("radius", number(spawn.getRadius()));
        ph.put("min_player_distance", String.valueOf(spawn.getMinPlayerDistance()));
        ph.put("location", location(spawn.getLocation()));
        ph.put("loot_table", empty(spawn.getLootTable()));
        ph.put("name", empty(spawn.getName()));
        ph.put("health", number(spawn.getHealth()));
        ph.put("damage", number(spawn.getDamage()));
        ph.put("speed", number(spawn.getSpeed()));
        ph.put("follow_range", number(spawn.getFollowRange()));
        ph.put("glowing", yesNo(spawn.isGlowing()));
        ph.put("baby", yesNo(spawn.isBaby()));
        ph.put("ai", yesNo(spawn.isAi()));
        ph.put("persistent", yesNo(spawn.isPersistent()));
        ph.put("require_ground", yesNo(spawn.isRequireGround()));
        ph.put("clear_drops", yesNo(spawn.isClearVanillaDrops()));
        return ph;
    }

    private Map<String, String> bossPlaceholders(BossDefinition boss) {
        Map<String, String> ph = new LinkedHashMap<>();
        ph.put("boss", boss.getId());
        ph.put("entity", boss.getEntity());
        ph.put("type", boss.getType().name());
        ph.put("name", empty(boss.getDisplayName()));
        ph.put("health", number(boss.getHealth()));
        ph.put("damage", number(boss.getDamage()));
        ph.put("speed", number(boss.getSpeed()));
        ph.put("follow_range", number(boss.getFollowRange()));
        ph.put("location", location(boss.getLocation()));
        ph.put("loot_table", empty(boss.getLootTable()));
        ph.put("glowing", yesNo(boss.isGlowing()));
        ph.put("baby", yesNo(boss.isBaby()));
        ph.put("ai", yesNo(boss.isAi()));
        ph.put("persistent", yesNo(boss.isPersistent()));
        ph.put("clear_drops", yesNo(boss.isClearVanillaDrops()));
        ph.put("announce_spawn", yesNo(boss.isAnnounceSpawn()));
        ph.put("announce_death", yesNo(boss.isAnnounceDeath()));
        ph.put("bossbar_enabled", yesNo(boss.getBossBar().isEnabled()));
        ph.put("bossbar_title", empty(boss.getBossBar().getTitle()));
        ph.put("bossbar_color", boss.getBossBar().getColor());
        ph.put("bossbar_style", boss.getBossBar().getStyle());
        ph.put("bossbar_radius", number(boss.getBossBar().getRadius()));
        return ph;
    }

    private Map<String, String> lootPlaceholders(LootTable table) {
        Map<String, String> ph = new LinkedHashMap<>();
        ph.put("loot", table.getId());
        ph.put("entries", String.valueOf(table.getEntries().size()));
        ph.put("rolls", table.getMinRolls() == table.getMaxRolls()
                ? String.valueOf(table.getMinRolls())
                : table.getMinRolls() + "-" + table.getMaxRolls());
        ph.put("unique", yesNo(table.isUnique()));
        return ph;
    }

    private Map<String, String> lootEntryPlaceholders(LootEntry entry, int index) {
        Map<String, String> ph = new LinkedHashMap<>();
        String item = entry.getItem() == null ? "?" : entry.getItem().getType().name();
        ph.put("index", String.valueOf(index + 1));
        ph.put("item", item);
        ph.put("amount", entry.getMinAmount() == entry.getMaxAmount()
                ? String.valueOf(entry.getMinAmount())
                : entry.getMinAmount() + "-" + entry.getMaxAmount());
        ph.put("chance", number(entry.getChance() * 100.0D) + "%");
        ph.put("weight", String.valueOf(entry.getWeight()));
        ph.put("message", empty(entry.getMessage()));
        return ph;
    }

    private Map<String, String> chestPlaceholders(ChestSpawn chest) {
        Map<String, String> ph = new LinkedHashMap<>();
        ph.put("chest", chest.getId());
        ph.put("loot_table", empty(chest.getLootTable()));
         ph.put("refill", duration(chest.getRefillSeconds()));
        ph.put("enabled", yesNo(chest.isEnabled()));
        ph.put("location", location(chest.getLocation()));
        return ph;
    }

    private Map<String, String> rewardPlaceholders(RewardBlock reward) {
        Map<String, String> ph = new LinkedHashMap<>();
        ph.put("reward", reward.getId());
        ph.put("loot_table", empty(reward.getLootTable()));
        ph.put("material", reward.getMaterial().name());
        ph.put("one_time", yesNo(reward.isOneTime()));
         ph.put("respawn", duration(reward.getRespawnSeconds()));
        ph.put("direct_give", yesNo(reward.isDirectGive()));
        ph.put("announce", yesNo(reward.isAnnounce()));
        ph.put("location", location(reward.getLocation()));
        return ph;
    }

    private String settingValue(DungeonSettings settings, String key) {
        return switch (key) {
            case "max-players" -> String.valueOf(settings.getMaxPlayers());
            case "min-players" -> String.valueOf(settings.getMinPlayers());
            case "time-limit-seconds" -> duration(settings.getTimeLimitSeconds());
            case "auto-enter" -> yesNo(settings.isAutoEnter());
            case "auto-start" -> yesNo(settings.isAutoStart());
            case "exit-on-leave-region" -> yesNo(settings.isExitOnLeaveRegion());
            case "leave-on-death" -> yesNo(settings.isLeaveOnDeath());
            case "reset-on-empty" -> yesNo(settings.isResetOnEmpty());
            case "reset-delay-seconds" -> duration(settings.getResetDelaySeconds());
            case "end-delay-seconds" -> duration(settings.getEndDelaySeconds());
            case "allow-rejoin" -> yesNo(settings.isAllowRejoin());
            case "protection" -> yesNo(settings.isProtection());
            case "allow-chest-break" -> yesNo(settings.isAllowChestBreak());
            case "bosses-after-waves" -> yesNo(settings.isBossesAfterWaves());
            case "entry-permission" -> empty(settings.getEntryPermission());
            case "cooldown-seconds" -> duration(settings.getCooldownSeconds());
            case "complete-on" -> settings.getCompleteOn().name();
            case "required-kills" -> String.valueOf(settings.getRequiredKills());
            case "bossbar.enabled" -> yesNo(settings.getBossBar().isEnabled());
            case "bossbar.title" -> empty(settings.getBossBar().getTitle());
            case "bossbar.color" -> settings.getBossBar().getColor();
            case "bossbar.style" -> settings.getBossBar().getStyle();
            case "bossbar.radius" -> number(settings.getBossBar().getRadius());
            default -> "-";
        };
    }

    private String spawnValue(SpawnPoint spawn, String key) {
        return switch (key) {
            case "type" -> spawn.getType().name();
            case "entity" -> spawn.getEntity();
            case "amount" -> String.valueOf(spawn.getAmount());
            case "max-alive" -> String.valueOf(spawn.getMaxAlive());
            case "respawn-seconds" -> duration(spawn.getRespawnSeconds());
            case "spawn-interval-seconds" -> duration(spawn.getSpawnIntervalSeconds());
            case "radius" -> number(spawn.getRadius());
            case "min-player-distance" -> String.valueOf(spawn.getMinPlayerDistance());
            case "require-ground" -> yesNo(spawn.isRequireGround());
            case "health" -> number(spawn.getHealth());
            case "damage" -> number(spawn.getDamage());
            case "speed" -> number(spawn.getSpeed());
            case "follow-range" -> number(spawn.getFollowRange());
            case "name" -> empty(spawn.getName());
            case "glowing" -> yesNo(spawn.isGlowing());
            case "baby" -> yesNo(spawn.isBaby());
            case "ai" -> yesNo(spawn.isAi());
            case "persistent" -> yesNo(spawn.isPersistent());
            case "clear-vanilla-drops" -> yesNo(spawn.isClearVanillaDrops());
            case "burn-in-daylight" -> yesNo(spawn.isBurnInDaylight());
            case "loot-table" -> empty(spawn.getLootTable());
            case "location" -> location(spawn.getLocation());
            default -> "-";
        };
    }

    private String bossValue(BossDefinition boss, String itemKey) {
        return switch (itemKey) {
            case "type" -> boss.getType().name();
            case "entity" -> boss.getEntity();
            case "health" -> number(boss.getHealth());
            case "damage" -> number(boss.getDamage());
            case "speed" -> number(boss.getSpeed());
            case "follow-range" -> number(boss.getFollowRange());
            case "name" -> empty(boss.getDisplayName());
            case "glowing" -> yesNo(boss.isGlowing());
            case "baby" -> yesNo(boss.isBaby());
            case "ai" -> yesNo(boss.isAi());
            case "persistent" -> yesNo(boss.isPersistent());
            case "clear-vanilla-drops" -> yesNo(boss.isClearVanillaDrops());
            case "announce-spawn" -> yesNo(boss.isAnnounceSpawn());
            case "announce-death" -> yesNo(boss.isAnnounceDeath());
            case "burn-in-daylight" -> yesNo(boss.isBurnInDaylight());
            case "loot-table" -> empty(boss.getLootTable());
            case "location" -> location(boss.getLocation());
            case "bossbar-enabled" -> yesNo(boss.getBossBar().isEnabled());
            case "bossbar-title" -> empty(boss.getBossBar().getTitle());
            case "bossbar-color" -> boss.getBossBar().getColor();
            case "bossbar-style" -> boss.getBossBar().getStyle();
            case "bossbar-radius" -> number(boss.getBossBar().getRadius());
            default -> "-";
        };
    }

    private String rewardValue(RewardBlock reward, String field) {
        return switch (field) {
            case "one-time" -> yesNo(reward.isOneTime());
            case "respawn-seconds" -> duration(reward.getRespawnSeconds());
            case "direct-give" -> yesNo(reward.isDirectGive());
            case "announce" -> yesNo(reward.isAnnounce());
            default -> "-";
        };
    }

    // ------------------------------------------------------------------
    // Mapeos y helpers
    // ------------------------------------------------------------------

    private static String settingKey(String itemKey) {
        if (itemKey.startsWith("bossbar-")) {
            return "bossbar." + itemKey.substring("bossbar-".length());
        }
        return itemKey;
    }

    private static String bossKey(String itemKey) {
        if (itemKey.startsWith("bossbar-")) {
            return "bossbar." + itemKey.substring("bossbar-".length());
        }
        return itemKey;
    }

    private static String equipmentKey(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "head";
            case CHEST -> "chest";
            case LEGS -> "legs";
            case FEET -> "feet";
            case HAND -> "hand";
            case OFF_HAND -> "off-hand";
            default -> slot.name().toLowerCase(Locale.ROOT).replace('_', '-');
        };
    }

    private static String slotLabel(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "Cabeza";
            case CHEST -> "Pechera";
            case LEGS -> "Pantalones";
            case FEET -> "Botas";
            case HAND -> "Mano principal";
            case OFF_HAND -> "Mano secundaria";
            default -> slot.name();
        };
    }

    private static boolean isBooleanKey(String key) {
        return key.equals("auto-enter") || key.equals("auto-start") || key.equals("exit-on-leave-region")
                || key.equals("leave-on-death") || key.equals("reset-on-empty")
                || key.equals("allow-rejoin") || key.equals("protection")
                || key.equals("allow-chest-break") || key.equals("bosses-after-waves")
                || key.equals("bossbar.enabled");
    }

    private static boolean isTextKey(String key) {
        return key.equals("entry-permission") || key.equals("bossbar.title");
    }

    private String nextNumber(String key, String current) {
        double value;
        try {
            value = Double.parseDouble(current.replace(',', '.'));
        } catch (NumberFormatException exception) {
            value = 0.0D;
        }
        double step = switch (key) {
            case "time-limit-seconds" -> 60.0D;
            case "cooldown-seconds" -> 30.0D;
            case "reset-delay-seconds", "end-delay-seconds" -> 5.0D;
            case "bossbar.radius" -> 8.0D;
            default -> 1.0D;
        };
        double next = value + step;
        if (key.equals("bossbar.radius")) {
            return number(next);
        }
        return String.valueOf((int) next);
    }

    private static String next(List<String> values, String current) {
        int index = 0;
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).equalsIgnoreCase(current)) {
                index = i;
                break;
            }
        }
        return values.get((index + 1) % values.size());
    }

    private void applySpawnEntity(SpawnPoint spawn, String raw) {
        String value = raw == null ? "ZOMBIE" : raw.trim();
        if (value.toLowerCase(Locale.ROOT).startsWith("mythic:")
                || value.toLowerCase(Locale.ROOT).startsWith("mm:")) {
            spawn.setType(MobType.MYTHIC);
            spawn.setEntity(value.substring(value.indexOf(':') + 1));
        } else {
            spawn.setType(MobType.VANILLA);
            spawn.setEntity(value.toUpperCase(Locale.ROOT));
        }
    }

    private void applyBossEntity(BossDefinition boss, String raw) {
        String value = raw == null ? "ZOMBIE" : raw.trim();
        if (value.toLowerCase(Locale.ROOT).startsWith("mythic:")
                || value.toLowerCase(Locale.ROOT).startsWith("mm:")) {
            boss.setType(MobType.MYTHIC);
            boss.setEntity(value.substring(value.indexOf(':') + 1));
        } else {
            boss.setType(MobType.VANILLA);
            boss.setEntity(value.toUpperCase(Locale.ROOT));
        }
    }

    private Dungeon dungeon(String id) {
        return id == null ? null : plugin.getDungeonManager().getDungeon(id);
    }

    private void save(Dungeon dungeon) {
        if (dungeon != null) {
            plugin.getRepository().save(dungeon);
        }
    }

    private List<Dungeon> sortedDungeons() {
        List<Dungeon> list = new ArrayList<>(plugin.getDungeonManager().getDungeons());
        list.sort(Comparator.comparing(Dungeon::getId, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private static <T> List<T> sorted(Collection<T> values, java.util.function.Function<T, String> key) {
        List<T> list = new ArrayList<>(values);
        list.sort(Comparator.comparing(key, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private String stateLabel(DungeonInstance instance) {
        DungeonState state = instance == null ? DungeonState.IDLE : instance.getState();
        String raw = plugin.getMessages().raw("placeholder.state." + state.name().toLowerCase(Locale.ROOT));
        return raw == null ? state.name() : raw;
    }

    private String duration(long seconds) {
        return plugin.getPlaceholderService().formatDuration(seconds);
    }

    private String yesNo(boolean value) {
        String raw = config.raw(value ? "format.yes" : "format.no");
        return raw == null ? (value ? "Si" : "No") : raw;
    }

    private String location(Location location) {
        if (location == null || location.getWorld() == null) {
            return empty("");
        }
        return location.getWorld().getName() + " " + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ();
    }

    private String empty(String value) {
        if (value == null || value.isBlank()) {
            String raw = config.raw("format.none");
            return raw == null ? "-" : raw;
        }
        return value;
    }

    private String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String returnMenuFor(String applyType) {
        return switch (applyType) {
            case "spawn" -> "spawn-editor";
            case "boss" -> "boss-editor";
            case "chest" -> "chest-editor";
            case "reward" -> "reward-editor";
            default -> "dungeon-editor";
        };
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[a-zA-Z0-9_\\-]{1,32}");
    }

    private String prefix() {
        String prefix = plugin.getMessages().prefix();
        return prefix == null ? "" : prefix;
    }

    private void msg(Player player, String key, Map<String, String> placeholders) {
        if (player == null) {
            return;
        }
        String raw = config.raw("messages." + key);
        if (raw == null || raw.isBlank()) {
            raw = "<gray>" + key;
        }
        Map<String, String> all = new HashMap<>();
        if (placeholders != null) {
            all.putAll(placeholders);
        }
        all.put("prefix", prefix());
        plugin.getMessages().sendRaw(player, config.apply(raw, all), null);
    }

    private record ListEntry(String id, Map<String, String> placeholders) {
    }
}
