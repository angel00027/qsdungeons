package org.qs.qsdungeons.command;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.boss.BossDefinition;
import org.qs.qsdungeons.dungeon.BossBarSettings;
import org.qs.qsdungeons.dungeon.ChestSpawn;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.Region;
import org.qs.qsdungeons.dungeon.RewardBlock;
import org.qs.qsdungeons.loot.LootEntry;
import org.qs.qsdungeons.loot.LootTable;
import org.qs.qsdungeons.spawn.MobType;
import org.qs.qsdungeons.spawn.SpawnPoint;
import org.qs.qsdungeons.util.ConfigValues;
import org.qs.qsdungeons.util.Keys;
import org.qs.qsdungeons.util.Tags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Comandos administrativos para configurar dungeons, spawns, jefes y loot.
 */
public final class AdminCommands {

    private static final String ID_PATTERN = "[a-zA-Z0-9_\\-]{1,32}";

    private final QsDungeons plugin;
    private final SelectionManager selections;

    public AdminCommands(QsDungeons plugin, SelectionManager selections) {
        this.plugin = plugin;
        this.selections = selections;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return false;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "enable" -> toggle(sender, args, true);
            case "disable" -> toggle(sender, args, false);
            case "pos1" -> position(sender, args, true);
            case "pos2" -> position(sender, args, false);
            case "region" -> region(sender, args);
            case "entry" -> entryExit(sender, args, true);
            case "exit" -> entryExit(sender, args, false);
            case "spawn" -> spawn(sender, args);
            case "boss" -> boss(sender, args);
            case "loot" -> loot(sender, args);
            case "chest" -> chest(sender, args);
            case "reward" -> reward(sender, args);
            case "set" -> settings(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Dungeons
    // ------------------------------------------------------------------

    private void create(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/dungeon create <id>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!validId(sender, id)) {
            return;
        }
        if (plugin.getDungeonManager().exists(id)) {
            send(sender, "command.exists", Map.of("dungeon", id));
            return;
        }
        Dungeon dungeon = plugin.getDungeonManager().create(id);
        if (dungeon == null) {
            send(sender, "command.exists", Map.of("dungeon", id));
            return;
        }
        send(sender, "command.created", Map.of("dungeon", id));
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/dungeon delete <id> [confirm]");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(id);
        if (dungeon == null) {
            send(sender, "general.unknown-dungeon", Map.of("dungeon", id));
            return;
        }
        boolean confirm = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
        if (!confirm) {
            send(sender, "command.deleted-confirm", Map.of("dungeon", id));
            return;
        }
        if (plugin.getDungeonManager().delete(id)) {
            send(sender, "command.deleted", Map.of("dungeon", id));
        } else {
            send(sender, "general.error", Map.of("error", "no se pudo borrar la carpeta"));
        }
    }

    private void toggle(CommandSender sender, String[] args, boolean enabled) {
        if (args.length < 2) {
            usage(sender, "/dungeon " + (enabled ? "enable" : "disable") + " <id>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[1]);
        if (dungeon == null) {
            return;
        }
        dungeon.setEnabled(enabled);
        save(dungeon);
        send(sender, enabled ? "general.enabled" : "general.disabled",
                Map.of("dungeon", dungeon.getId()));
    }

    private void position(CommandSender sender, String[] args, boolean first) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        Block block = player.getTargetBlockExact(10, FluidCollisionMode.NEVER);
        if (block == null) {
            block = player.getLocation().getBlock();
        }
        if (first) {
            selections.setFirst(player, block);
        } else {
            selections.setSecond(player, block);
        }
        send(sender, first ? "command.pos1" : "command.pos2",
                Map.of("location", block.getX() + ", " + block.getY() + ", " + block.getZ()));
    }

    private void region(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        Dungeon dungeon = args.length < 2 ? null : dungeon(sender, args[1]);
        if (dungeon == null) {
            if (args.length < 2) {
                usage(sender, "/dungeon region <id>");
            }
            return;
        }
        if (!selections.isComplete(player)) {
            send(sender, "command.no-region", Map.of());
            return;
        }
        if (selections.getFirst(player).getWorld() != selections.getSecond(player).getWorld()) {
            send(sender, "command.different-worlds", Map.of());
            return;
        }
        Region region = selections.getRegion(player);
        if (region == null) {
            send(sender, "command.different-worlds", Map.of());
            return;
        }
        dungeon.setRegion(region);
        save(dungeon);
        send(sender, "command.region-set", Map.of(
                "dungeon", dungeon.getId(),
                "region", region.describe()));
    }

    private void entryExit(CommandSender sender, String[] args, boolean entry) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            usage(sender, "/dungeon " + (entry ? "entry" : "exit") + " <id>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[1]);
        if (dungeon == null) {
            return;
        }
        Location location = player.getLocation().clone();
        if (entry) {
            dungeon.setEntry(location);
        } else {
            dungeon.setExit(location);
        }
        save(dungeon);
        send(sender, entry ? "command.entry-set" : "command.exit-set",
                Map.of("dungeon", dungeon.getId()));
    }

    private void settings(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/dungeon set <id> <ajuste> <valor>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[1]);
        if (dungeon == null) {
            return;
        }
        if (args.length < 3) {
            send(sender, "command.setting-list", Map.of(
                    "keys", String.join(", ", dungeon.getSettings().keys())));
            return;
        }
        if (args.length < 4) {
            usage(sender, "/dungeon set <id> <ajuste> <valor>");
            return;
        }
        String key = args[2].toLowerCase(Locale.ROOT);
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        if (!dungeon.getSettings().set(key, value)) {
            send(sender, "command.setting-unknown", Map.of("key", key));
            return;
        }
        save(dungeon);
        send(sender, "command.setting-changed", Map.of(
                "dungeon", dungeon.getId(),
                "key", key,
                "value", value));
    }

    // ------------------------------------------------------------------
    // Spawns
    // ------------------------------------------------------------------

    private void spawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/dungeon spawn <add|remove|list|set|test> ...");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "add" -> spawnAdd(sender, args);
            case "remove" -> spawnRemove(sender, args);
            case "list" -> spawnList(sender, args);
            case "set" -> spawnSet(sender, args);
            case "test" -> spawnTest(sender, args);
            default -> usage(sender, "/dungeon spawn <add|remove|list|set|test> ...");
        }
    }

    private void spawnTest(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || args.length < 4) {
            if (player != null) {
                usage(sender, "/dungeon spawn test <dungeon> <spawnId>");
            }
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        SpawnPoint point = dungeon == null ? null : dungeon.getSpawn(args[3].toLowerCase(Locale.ROOT));
        if (point == null) {
            send(sender, "command.spawn-not-found", Map.of("spawn", args[3]));
            return;
        }
        org.qs.qsdungeons.dungeon.instance.DungeonInstance preview =
                new org.qs.qsdungeons.dungeon.instance.DungeonInstance(plugin, dungeon);
        Location oldLocation = point.getLocation();
        point.setLocation(player.getLocation().clone());
        org.bukkit.entity.LivingEntity entity = plugin.getMobSpawner().spawn(preview, point);
        point.setLocation(oldLocation);
        if (entity == null) {
            send(sender, "command.spawn-test-failed", Map.of("spawn", point.getId()));
            return;
        }
        if (entity instanceof Mob mob) {
            mob.setAI(false);
        }
        Tags.set(entity, Keys.TEST, "true");
        send(sender, "command.spawn-test", Map.of("spawn", point.getId(), "entity", point.getEntity()));
    }

    private void spawnAdd(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 4) {
            usage(sender, "/dungeon spawn add <dungeon> <spawnId> [entidad] [cantidad]");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        String spawnId = args[3].toLowerCase(Locale.ROOT);
        if (!validId(sender, spawnId)) {
            return;
        }
        String rawEntity = args.length >= 5 ? args[4] : "ZOMBIE";
        int amount = args.length >= 6
                ? ConfigValues.parseInt(args[5], 1)
                : 1;

        SpawnPoint point = dungeon.getSpawns().get(spawnId);
        if (point == null) {
            point = new SpawnPoint(spawnId);
            dungeon.getSpawns().put(spawnId, point);
        }
        point.setLocation(player.getLocation().clone());
        point.setAmount(amount);
        point.setMaxAlive(amount);
        applyMobType(point, rawEntity);
        save(dungeon);
        send(sender, "command.spawn-added", Map.of(
                "spawn", spawnId,
                "dungeon", dungeon.getId(),
                "amount", String.valueOf(point.getAmount()),
                "type", point.getEntity()));
    }

    private void spawnRemove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            usage(sender, "/dungeon spawn remove <dungeon> <spawnId>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        String spawnId = args[3].toLowerCase(Locale.ROOT);
        if (dungeon.getSpawns().remove(spawnId) == null) {
            send(sender, "command.spawn-not-found", Map.of("spawn", spawnId));
            return;
        }
        save(dungeon);
        send(sender, "command.spawn-removed", Map.of("spawn", spawnId));
    }

    private void spawnList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/dungeon spawn list <dungeon>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        send(sender, "command.spawn-list", Map.of(
                "spawns", dungeon.getSpawns().isEmpty() ? "-" : String.join(", ", dungeon.getSpawns().keySet())));
    }

    private void spawnSet(CommandSender sender, String[] args) {
        if (args.length < 6) {
            usage(sender, "/dungeon spawn set <dungeon> <spawnId> <ajuste> <valor>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        SpawnPoint point = dungeon.getSpawns().get(args[3].toLowerCase(Locale.ROOT));
        if (point == null) {
            send(sender, "command.spawn-not-found", Map.of("spawn", args[3]));
            return;
        }
        String key = args[4].toLowerCase(Locale.ROOT);
        String value = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
        if (!applyEquipmentSetting(point.getEquipment(), key, value, sender)
                && !applySpawnSetting(point, key, value)) {
            send(sender, "command.setting-unknown", Map.of("key", key));
            return;
        }
        save(dungeon);
        send(sender, "command.setting-changed", Map.of(
                "dungeon", dungeon.getId(),
                "key", "spawn." + key,
                "value", value));
    }

    private boolean applySpawnSetting(SpawnPoint point, String key, String value) {
        try {
            switch (key) {
                case "type" -> point.setType(MobType.from(value, point.getType()));
                case "entity" -> applyMobType(point, value);
                case "mythic" -> {
                    point.setType(MobType.MYTHIC);
                    point.setEntity(value);
                }
                case "amount" -> point.setAmount(Integer.parseInt(value));
                case "max-alive" -> point.setMaxAlive(Integer.parseInt(value));
                case "respawn-seconds" -> point.setRespawnSeconds(Integer.parseInt(value));
                case "spawn-interval-seconds" -> point.setSpawnIntervalSeconds(Integer.parseInt(value));
                case "radius" -> point.setRadius(Double.parseDouble(value));
                case "min-player-distance" -> point.setMinPlayerDistance(Integer.parseInt(value));
                case "require-ground" -> point.setRequireGround(Boolean.parseBoolean(value));
                case "health" -> point.setHealth(Double.parseDouble(value));
                case "damage" -> point.setDamage(Double.parseDouble(value));
                case "speed" -> point.setSpeed(Double.parseDouble(value));
                case "follow-range" -> point.setFollowRange(Double.parseDouble(value));
                case "name" -> point.setName(value);
                case "glowing" -> point.setGlowing(Boolean.parseBoolean(value));
                case "baby" -> point.setBaby(Boolean.parseBoolean(value));
                case "ai" -> point.setAi(Boolean.parseBoolean(value));
                case "persistent" -> point.setPersistent(Boolean.parseBoolean(value));
                case "clear-vanilla-drops" -> point.setClearVanillaDrops(Boolean.parseBoolean(value));
                case "burn-in-daylight" -> point.setBurnInDaylight(Boolean.parseBoolean(value));
                case "loot-table" -> point.setLootTable(value);
                case "location" -> {
                    Player player = plugin.getServer().getPlayerExact(value);
                    if (player == null) {
                        return false;
                    }
                    point.setLocation(player.getLocation().clone());
                }
                default -> {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean applyEquipmentSetting(Map<EquipmentSlot, ItemStack> equipment,
                                          String key, String value, CommandSender sender) {
        if (!key.startsWith("equipment.")) {
            return false;
        }
        String rawSlot = key.substring("equipment.".length()).replace('-', '_').toUpperCase(Locale.ROOT);
        EquipmentSlot slot;
        try {
            slot = EquipmentSlot.valueOf(rawSlot);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (value.equalsIgnoreCase("clear")) {
            equipment.remove(slot);
            return true;
        }
        if (!value.equalsIgnoreCase("hand") || !(sender instanceof Player player)) {
            return false;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            return false;
        }
        equipment.put(slot, item.clone());
        return true;
    }

    // ------------------------------------------------------------------
    // Jefes
    // ------------------------------------------------------------------

    private void boss(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/dungeon boss <create|remove|list|set|loc|test> ...");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> bossCreate(sender, args);
            case "remove" -> bossRemove(sender, args);
            case "list" -> bossList(sender, args);
            case "set" -> bossSet(sender, args);
            case "loc" -> bossLocation(sender, args);
            case "test" -> bossTest(sender, args);
            default -> usage(sender, "/dungeon boss <create|remove|list|set|loc|test> ...");
        }
    }

    private void bossTest(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null || args.length < 4) {
            if (player != null) {
                usage(sender, "/dungeon boss test <dungeon> <bossId>");
            }
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        BossDefinition boss = dungeon == null ? null : dungeon.getBoss(args[3].toLowerCase(Locale.ROOT));
        if (boss == null) {
            send(sender, "command.boss-not-found", Map.of("boss", args[3]));
            return;
        }
        org.qs.qsdungeons.dungeon.instance.DungeonInstance preview =
                new org.qs.qsdungeons.dungeon.instance.DungeonInstance(plugin, dungeon);
        Location oldLocation = boss.getLocation();
        boss.setLocation(player.getLocation().clone());
        org.bukkit.entity.LivingEntity entity = plugin.getBossService().spawn(preview, boss);
        boss.setLocation(oldLocation);
        if (entity == null) {
            send(sender, "command.boss-test-failed", Map.of("boss", boss.getId()));
            return;
        }
        if (entity instanceof Mob mob) {
            mob.setAI(false);
        }
        Tags.set(entity, Keys.TEST, "true");
        send(sender, "command.boss-test", Map.of("boss", boss.getId(), "entity", boss.getEntity()));
    }

    private void bossCreate(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 4) {
            usage(sender, "/dungeon boss create <dungeon> <bossId> [entidad]");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        String bossId = args[3].toLowerCase(Locale.ROOT);
        if (!validId(sender, bossId)) {
            return;
        }
        BossDefinition boss = new BossDefinition(bossId);
        boss.setLocation(player.getLocation().clone());
        boss.setEntity(args.length >= 5 ? args[4] : "ZOMBIE");
        applyBossType(boss, boss.getEntity());
        dungeon.getBosses().put(bossId, boss);
        save(dungeon);
        send(sender, "command.boss-created", Map.of(
                "boss", bossId,
                "dungeon", dungeon.getId()));
    }

    private void bossRemove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            usage(sender, "/dungeon boss remove <dungeon> <bossId>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        String bossId = args[3].toLowerCase(Locale.ROOT);
        if (dungeon.getBosses().remove(bossId) == null) {
            send(sender, "command.boss-not-found", Map.of("boss", bossId));
            return;
        }
        plugin.getRepository().deleteBoss(dungeon, bossId);
        send(sender, "command.boss-removed", Map.of("boss", bossId));
    }

    private void bossList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/dungeon boss list <dungeon>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        send(sender, "command.boss-list", Map.of(
                "bosses", dungeon.getBosses().isEmpty() ? "-" : String.join(", ", dungeon.getBosses().keySet())));
    }

    private void bossLocation(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 4) {
            usage(sender, "/dungeon boss loc <dungeon> <bossId>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        BossDefinition boss = dungeon.getBosses().get(args[3].toLowerCase(Locale.ROOT));
        if (boss == null) {
            send(sender, "command.boss-not-found", Map.of("boss", args[3]));
            return;
        }
        boss.setLocation(player.getLocation().clone());
        save(dungeon);
        send(sender, "command.boss-location", Map.of("boss", boss.getId()));
    }

    private void bossSet(CommandSender sender, String[] args) {
        if (args.length < 6) {
            usage(sender, "/dungeon boss set <dungeon> <bossId> <ajuste> <valor>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        BossDefinition boss = dungeon.getBosses().get(args[3].toLowerCase(Locale.ROOT));
        if (boss == null) {
            send(sender, "command.boss-not-found", Map.of("boss", args[3]));
            return;
        }
        String key = args[4].toLowerCase(Locale.ROOT);
        String value = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
        if (!applyEquipmentSetting(boss.getEquipment(), key, value, sender)
                && !applyBossSetting(boss, key, value)) {
            send(sender, "command.setting-unknown", Map.of("key", key));
            return;
        }
        save(dungeon);
        send(sender, "command.setting-changed", Map.of(
                "dungeon", dungeon.getId(),
                "key", "boss." + key,
                "value", value));
    }

    private boolean applyBossSetting(BossDefinition boss, String key, String value) {
        try {
            BossBarSettings bar = boss.getBossBar();
            switch (key) {
                case "type" -> boss.setType(MobType.from(value, boss.getType()));
                case "entity" -> applyBossType(boss, value);
                case "mythic" -> {
                    boss.setType(MobType.MYTHIC);
                    boss.setEntity(value);
                }
                case "health" -> boss.setHealth(Double.parseDouble(value));
                case "damage" -> boss.setDamage(Double.parseDouble(value));
                case "speed" -> boss.setSpeed(Double.parseDouble(value));
                case "follow-range" -> boss.setFollowRange(Double.parseDouble(value));
                case "name", "display-name" -> boss.setDisplayName(value);
                case "glowing" -> boss.setGlowing(Boolean.parseBoolean(value));
                case "baby" -> boss.setBaby(Boolean.parseBoolean(value));
                case "ai" -> boss.setAi(Boolean.parseBoolean(value));
                case "persistent" -> boss.setPersistent(Boolean.parseBoolean(value));
                case "clear-vanilla-drops" -> boss.setClearVanillaDrops(Boolean.parseBoolean(value));
                case "burn-in-daylight" -> boss.setBurnInDaylight(Boolean.parseBoolean(value));
                case "announce-spawn" -> boss.setAnnounceSpawn(Boolean.parseBoolean(value));
                case "announce-death" -> boss.setAnnounceDeath(Boolean.parseBoolean(value));
                case "loot-table" -> boss.setLootTable(value);
                case "bossbar.enabled" -> bar.setEnabled(Boolean.parseBoolean(value));
                case "bossbar.title" -> bar.setTitle(value);
                case "bossbar.color" -> bar.setColor(value);
                case "bossbar.style" -> bar.setStyle(value);
                case "bossbar.radius" -> bar.setRadius(Double.parseDouble(value));
                case "location" -> {
                    Player player = plugin.getServer().getPlayerExact(value);
                    if (player == null) {
                        return false;
                    }
                    boss.setLocation(player.getLocation().clone());
                }
                default -> {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Loot
    // ------------------------------------------------------------------

    private void loot(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/dungeon loot <create|remove|list|info|add|remove-entry|set> ...");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> lootCreate(sender, args);
            case "remove" -> lootRemove(sender, args);
            case "list" -> lootList(sender, args);
            case "info" -> lootInfo(sender, args);
            case "add" -> lootAdd(sender, args);
            case "remove-entry" -> lootRemoveEntry(sender, args);
            case "set" -> lootSet(sender, args);
            default -> usage(sender, "/dungeon loot <create|remove|list|info|add|remove-entry|set> ...");
        }
    }

    private void lootCreate(CommandSender sender, String[] args) {
        if (args.length < 4) {
            usage(sender, "/dungeon loot create <dungeon> <lootId>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        String lootId = args[3].toLowerCase(Locale.ROOT);
        if (!validId(sender, lootId)) {
            return;
        }
        if (dungeon.getLootTables().containsKey(lootId)) {
            send(sender, "command.loot-exists", Map.of("loot", lootId));
            return;
        }
        dungeon.getLootTables().put(lootId, new LootTable(lootId));
        save(dungeon);
        send(sender, "command.loot-created", Map.of("loot", lootId));
    }

    private void lootRemove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            usage(sender, "/dungeon loot remove <dungeon> <lootId>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        String lootId = args[3].toLowerCase(Locale.ROOT);
        if (dungeon.getLootTables().remove(lootId) == null) {
            send(sender, "command.loot-not-found", Map.of("loot", lootId));
            return;
        }
        plugin.getRepository().deleteLootTable(dungeon, lootId);
        send(sender, "command.loot-removed", Map.of("loot", lootId));
    }

    private void lootList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/dungeon loot list <dungeon>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        send(sender, "command.loot-list", Map.of(
                "loot", dungeon.getLootTables().isEmpty() ? "-" : String.join(", ", dungeon.getLootTables().keySet())));
    }

    private void lootInfo(CommandSender sender, String[] args) {
        if (args.length < 4) {
            usage(sender, "/dungeon loot info <dungeon> <lootId>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        LootTable table = dungeon.getLootTable(args[3].toLowerCase(Locale.ROOT));
        if (table == null) {
            send(sender, "command.loot-not-found", Map.of("loot", args[3]));
            return;
        }
        String rolls = table.getMinRolls() == table.getMaxRolls()
                ? String.valueOf(table.getMinRolls())
                : table.getMinRolls() + "-" + table.getMaxRolls();
        send(sender, "command.loot-info", Map.of(
                "loot", table.getId(),
                "entries", String.valueOf(table.getEntries().size()),
                "rolls", rolls));
    }

    private void lootAdd(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 4) {
            usage(sender, "/dungeon loot add <dungeon> <lootId> [probabilidad] [cantidad] [peso]");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        LootTable table = dungeon.getLootTable(args[3].toLowerCase(Locale.ROOT));
        if (table == null) {
            send(sender, "command.loot-not-found", Map.of("loot", args[3]));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            send(sender, "command.loot-empty-hand", Map.of());
            return;
        }
        double chance = 1.0D;
        if (args.length >= 5) {
            try {
                chance = Double.parseDouble(args[4]);
                if (chance > 1.0D) {
                    chance /= 100.0D;
                }
            } catch (NumberFormatException exception) {
                send(sender, "general.invalid-number", Map.of("value", args[4]));
                return;
            }
        }
        int[] amount = ConfigValues.range(args.length >= 6 ? args[5] : null,
                hand.getAmount(), hand.getAmount());
        int weight = args.length >= 7 ? ConfigValues.parseInt(args[6], 1) : 1;

        LootEntry entry = new LootEntry(hand.clone());
        entry.setAmount(amount[0], amount[1]);
        entry.setChance(chance);
        entry.setWeight(weight);
        table.getEntries().add(entry);
        table.markEntriesChanged();
        save(dungeon);
        send(sender, "command.loot-added", Map.of(
                "loot", table.getId(),
                "chance", String.format(Locale.US, "%.0f", entry.getChance() * 100.0D)));
    }

    private void lootRemoveEntry(CommandSender sender, String[] args) {
        if (args.length < 5) {
            usage(sender, "/dungeon loot remove-entry <dungeon> <lootId> <indice>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        LootTable table = dungeon.getLootTable(args[3].toLowerCase(Locale.ROOT));
        if (table == null) {
            send(sender, "command.loot-not-found", Map.of("loot", args[3]));
            return;
        }
        int index = ConfigValues.parseInt(args[4], -1);
        if (index < 1 || index > table.getEntries().size()) {
            send(sender, "general.invalid-number", Map.of("value", args[4]));
            return;
        }
        table.getEntries().remove(index - 1);
        table.markEntriesChanged();
        save(dungeon);
        send(sender, "command.setting-changed", Map.of(
                "dungeon", dungeon.getId(),
                "key", "loot.entry",
                "value", String.valueOf(index)));
    }

    private void lootSet(CommandSender sender, String[] args) {
        if (args.length < 6) {
            usage(sender, "/dungeon loot set <dungeon> <lootId> <rolls|unique> <valor>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        LootTable table = dungeon.getLootTable(args[3].toLowerCase(Locale.ROOT));
        if (table == null) {
            send(sender, "command.loot-not-found", Map.of("loot", args[3]));
            return;
        }
        String key = args[4].toLowerCase(Locale.ROOT);
        String value = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
        switch (key) {
            case "rolls" -> {
                int[] range = ConfigValues.range(value, table.getMinRolls(), table.getMaxRolls());
                table.setRolls(range[0], range[1]);
            }
            case "unique" -> table.setUnique(Boolean.parseBoolean(value));
            default -> {
                send(sender, "command.setting-unknown", Map.of("key", key));
                return;
            }
        }
        save(dungeon);
        send(sender, "command.setting-changed", Map.of(
                "dungeon", dungeon.getId(),
                "key", "loot." + key,
                "value", value));
    }

    // ------------------------------------------------------------------
    // Cofres y bloques premiados
    // ------------------------------------------------------------------

    private void chest(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/dungeon chest <add|remove|list|set> ...");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "add" -> chestAdd(sender, args);
            case "remove" -> chestRemove(sender, args);
            case "list" -> chestList(sender, args);
            case "set" -> chestSet(sender, args);
            default -> usage(sender, "/dungeon chest <add|remove|list|set> ...");
        }
    }

    private void chestAdd(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 5) {
            usage(sender, "/dungeon chest add <dungeon> <chestId> <lootId>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        if (dungeon.getLootTable(args[4].toLowerCase(Locale.ROOT)) == null) {
            send(sender, "command.loot-not-found", Map.of("loot", args[4]));
            return;
        }
        Block block = targetBlock(player);
        if (block == null || !(block.getState() instanceof Chest)) {
            send(sender, "command.chest-invalid", Map.of());
            return;
        }
        String chestId = args[3].toLowerCase(Locale.ROOT);
        if (!validId(sender, chestId)) {
            return;
        }
        ChestSpawn chest = ChestSpawn.capture(chestId, block);
        chest.setLootTable(args[4].toLowerCase(Locale.ROOT));
        dungeon.getChests().put(chestId, chest);
        save(dungeon);
        send(sender, "command.chest-added", Map.of(
                "chest", chestId,
                "loot", chest.getLootTable()));
    }

    private void chestRemove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            usage(sender, "/dungeon chest remove <dungeon> <chestId>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        String chestId = args[3].toLowerCase(Locale.ROOT);
        if (dungeon.getChests().remove(chestId) == null) {
            send(sender, "command.chest-not-found", Map.of("chest", chestId));
            return;
        }
        save(dungeon);
        send(sender, "command.chest-removed", Map.of("chest", chestId));
    }

    private void chestList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/dungeon chest list <dungeon>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        send(sender, "command.chest-list", Map.of(
                "chests", dungeon.getChests().isEmpty() ? "-" : String.join(", ", dungeon.getChests().keySet())));
    }

    private void chestSet(CommandSender sender, String[] args) {
        if (args.length < 6) {
            usage(sender, "/dungeon chest set <dungeon> <chestId> <loot-table|refill-seconds|enabled> <valor>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        ChestSpawn chest = dungeon.getChests().get(args[3].toLowerCase(Locale.ROOT));
        if (chest == null) {
            send(sender, "command.chest-not-found", Map.of("chest", args[3]));
            return;
        }
        String key = args[4].toLowerCase(Locale.ROOT);
        String value = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
        switch (key) {
            case "loot-table" -> {
                if (dungeon.getLootTable(value.toLowerCase(Locale.ROOT)) == null) {
                    send(sender, "command.loot-not-found", Map.of("loot", value));
                    return;
                }
                chest.setLootTable(value.toLowerCase(Locale.ROOT));
            }
            case "refill-seconds" -> chest.setRefillSeconds(ConfigValues.parseDuration(value, chest.getRefillSeconds()));
            case "enabled" -> chest.setEnabled(Boolean.parseBoolean(value));
            default -> {
                send(sender, "command.setting-unknown", Map.of("key", key));
                return;
            }
        }
        save(dungeon);
        send(sender, "command.setting-changed", Map.of(
                "dungeon", dungeon.getId(),
                "key", "chest." + key,
                "value", value));
    }

    private void reward(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/dungeon reward <add|remove|list|set> ...");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "add" -> rewardAdd(sender, args);
            case "remove" -> rewardRemove(sender, args);
            case "list" -> rewardList(sender, args);
            case "set" -> rewardSet(sender, args);
            default -> usage(sender, "/dungeon reward <add|remove|list|set> ...");
        }
    }

    private void rewardAdd(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 5) {
            usage(sender, "/dungeon reward add <dungeon> <rewardId> <lootId>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        if (dungeon.getLootTable(args[4].toLowerCase(Locale.ROOT)) == null) {
            send(sender, "command.loot-not-found", Map.of("loot", args[4]));
            return;
        }
        Block block = targetBlock(player);
        if (block == null || block.getType().isAir()) {
            send(sender, "command.block-target", Map.of());
            return;
        }
        String rewardId = args[3].toLowerCase(Locale.ROOT);
        if (!validId(sender, rewardId)) {
            return;
        }
        RewardBlock reward = RewardBlock.capture(rewardId, block);
        reward.setLootTable(args[4].toLowerCase(Locale.ROOT));
        dungeon.getRewards().put(rewardId, reward);
        save(dungeon);
        send(sender, "command.reward-added", Map.of(
                "reward", rewardId,
                "loot", reward.getLootTable()));
    }

    private void rewardRemove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            usage(sender, "/dungeon reward remove <dungeon> <rewardId>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        String rewardId = args[3].toLowerCase(Locale.ROOT);
        if (dungeon.getRewards().remove(rewardId) == null) {
            send(sender, "command.reward-not-found", Map.of("reward", rewardId));
            return;
        }
        save(dungeon);
        send(sender, "command.reward-removed", Map.of("reward", rewardId));
    }

    private void rewardList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/dungeon reward list <dungeon>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        send(sender, "command.reward-list", Map.of(
                "rewards", dungeon.getRewards().isEmpty() ? "-" : String.join(", ", dungeon.getRewards().keySet())));
    }

    private void rewardSet(CommandSender sender, String[] args) {
        if (args.length < 6) {
            usage(sender, "/dungeon reward set <dungeon> <rewardId> <one-time|respawn-seconds|direct-give|announce|loot-table> <valor>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[2]);
        if (dungeon == null) {
            return;
        }
        RewardBlock reward = dungeon.getRewards().get(args[3].toLowerCase(Locale.ROOT));
        if (reward == null) {
            send(sender, "command.reward-not-found", Map.of("reward", args[3]));
            return;
        }
        String key = args[4].toLowerCase(Locale.ROOT);
        String value = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
        switch (key) {
            case "loot-table" -> {
                if (dungeon.getLootTable(value.toLowerCase(Locale.ROOT)) == null) {
                    send(sender, "command.loot-not-found", Map.of("loot", value));
                    return;
                }
                reward.setLootTable(value.toLowerCase(Locale.ROOT));
            }
            case "one-time" -> reward.setOneTime(Boolean.parseBoolean(value));
            case "respawn-seconds" -> reward.setRespawnSeconds(ConfigValues.parseDuration(value, reward.getRespawnSeconds()));
            case "direct-give" -> reward.setDirectGive(Boolean.parseBoolean(value));
            case "announce" -> reward.setAnnounce(Boolean.parseBoolean(value));
            default -> {
                send(sender, "command.setting-unknown", Map.of("key", key));
                return;
            }
        }
        save(dungeon);
        send(sender, "command.setting-changed", Map.of(
                "dungeon", dungeon.getId(),
                "key", "reward." + key,
                "value", value));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void applyMobType(SpawnPoint point, String rawEntity) {
        String value = rawEntity == null ? "ZOMBIE" : rawEntity.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("mythic:") || lower.startsWith("mm:")) {
            point.setType(MobType.MYTHIC);
            point.setEntity(value.substring(value.indexOf(':') + 1));
        } else {
            point.setType(MobType.VANILLA);
            point.setEntity(value.toUpperCase(Locale.ROOT));
        }
    }

    private void applyBossType(BossDefinition boss, String rawEntity) {
        String value = rawEntity == null ? "ZOMBIE" : rawEntity.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("mythic:") || lower.startsWith("mm:")) {
            boss.setType(MobType.MYTHIC);
            boss.setEntity(value.substring(value.indexOf(':') + 1));
        } else {
            boss.setType(MobType.VANILLA);
            boss.setEntity(value.toUpperCase(Locale.ROOT));
        }
    }

    private Block targetBlock(Player player) {
        Block block = player.getTargetBlockExact(10, FluidCollisionMode.NEVER);
        return block == null ? player.getLocation().getBlock() : block;
    }

    private Dungeon dungeon(CommandSender sender, String rawId) {
        String id = rawId == null ? "" : rawId.toLowerCase(Locale.ROOT);
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(id);
        if (dungeon == null) {
            send(sender, "general.unknown-dungeon", Map.of("dungeon", id));
        }
        return dungeon;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        send(sender, "general.player-only", Map.of());
        return null;
    }

    private boolean validId(CommandSender sender, String id) {
        if (id != null && id.matches(ID_PATTERN)) {
            return true;
        }
        send(sender, "command.invalid-id", Map.of("id", id == null ? "?" : id));
        return false;
    }

    private void save(Dungeon dungeon) {
        plugin.getRepository().save(dungeon);
    }

    private void usage(CommandSender sender, String usage) {
        send(sender, "general.usage", Map.of("usage", usage));
    }

    private void send(CommandSender sender, String key, Map<String, String> replacements) {
        plugin.getMessages().send(sender, key, replacements);
    }

    public SelectionManager getSelections() {
        return selections;
    }

    /**
     * Metodo de ayuda para el tab completer: devuelve las claves configurables
     * de un spawn o jefe.
     */
    public static List<String> spawnSettingKeys() {
        return new ArrayList<>(List.of("type", "entity", "mythic", "amount", "max-alive",
                "respawn-seconds", "spawn-interval-seconds", "radius", "min-player-distance",
                "require-ground", "health", "damage", "speed", "follow-range",
                "name", "glowing", "baby", "ai", "persistent", "clear-vanilla-drops",
                "loot-table", "burn-in-daylight", "location", "equipment.head", "equipment.chest",
                "equipment.legs", "equipment.feet", "equipment.hand", "equipment.off-hand"));
    }

    public static List<String> bossSettingKeys() {
        return new ArrayList<>(List.of("type", "entity", "mythic", "health", "damage", "speed",
                "follow-range", "name", "glowing", "baby", "ai", "persistent",
                "clear-vanilla-drops", "announce-spawn", "announce-death", "loot-table", "burn-in-daylight",
                "location", "equipment.head", "equipment.chest", "equipment.legs", "equipment.feet",
                "equipment.hand", "equipment.off-hand", "bossbar.enabled", "bossbar.title", "bossbar.color",
                "bossbar.style", "bossbar.radius"));
    }

    public static List<String> chestSettingKeys() {
        return new ArrayList<>(List.of("loot-table", "refill-seconds", "enabled"));
    }

    public static List<String> rewardSettingKeys() {
        return new ArrayList<>(List.of("loot-table", "one-time", "respawn-seconds",
                "direct-give", "announce"));
    }

    public static List<String> itemNames() {
        List<String> names = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isItem() && !material.isLegacy()) {
                names.add(material.name().toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }
}
