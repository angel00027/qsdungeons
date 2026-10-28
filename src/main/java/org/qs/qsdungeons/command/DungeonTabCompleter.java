package org.qs.qsdungeons.command;

import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.boss.BossDefinition;
import org.qs.qsdungeons.dungeon.ChestSpawn;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.RewardBlock;
import org.qs.qsdungeons.loot.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Autocompletado de /dungeon.
 */
public final class DungeonTabCompleter implements TabCompleter {

    private static final List<String> BASE = List.of("help", "list", "info", "join", "leave", "menu");
    private static final List<String> ADMIN = List.of("create", "delete", "enable", "disable",
            "pos1", "pos2", "region", "entry", "exit", "spawn", "boss", "loot", "chest",
            "reward", "set", "start", "stop", "reset", "reload", "gui", "cooldown-location",
            "cooldown-protection", "protection");
    private static final List<String> TRANSFER = List.of("export", "import", "save", "paste",
            "wand", "structures");

    private final QsDungeons plugin;

    public DungeonTabCompleter(QsDungeons plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> all = new ArrayList<>(BASE);
            if (sender.hasPermission("qsdungeons.admin")) {
                all.addAll(ADMIN);
                all.addAll(TRANSFER);
            }
            return filter(all, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawn" -> {
                return spawnCompletion(args);
            }
            case "boss" -> {
                return bossCompletion(args);
            }
            case "loot" -> {
                return lootCompletion(args);
            }
            case "chest" -> {
                return chestCompletion(args);
            }
            case "reward" -> {
                return rewardCompletion(args);
            }
            case "set" -> {
                return setCompletion(args);
            }
            case "gui" -> {
                if (args.length == 2) {
                    return dungeonIds(args[1]);
                }
                return List.of();
            }
            case "menu" -> {
                return List.of();
            }
            case "cooldown-location" -> {
                return filter(List.of("clear"), args[args.length - 1]);
            }
            case "cooldown-protection", "protection" -> {
                return filter(List.of("on", "off", "toggle"), args[args.length - 1]);
            }
            case "export" -> {
                if (args.length == 2) {
                    return dungeonIds(args[1]);
                }
                if (args.length == 3) {
                    return filter(List.of("config", "all"), args[2]);
                }
                return List.of();
            }
            case "import" -> {
                if (args.length == 2) {
                    return dungeonIds(args[1]);
                }
                if (args.length == 3) {
                    return filter(plugin.getDungeonArchive().listExports(), args[2]);
                }
                return List.of();
            }
            case "save" -> {
                if (args.length == 2) {
                    return dungeonIds(args[1]);
                }
                return List.of();
            }
            case "paste", "structures" -> {
                if (args.length == 2) {
                    return dungeonIds(args[1]);
                }
                if (args.length == 3 && sub.equals("paste")) {
                    Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[1]);
                    if (dungeon == null || plugin.getWorldEditHook() == null) {
                        return List.of();
                    }
                    return filter(plugin.getWorldEditHook().listStructures(dungeon), args[2]);
                }
                return List.of();
            }
            case "delete" -> {
                if (args.length == 2) {
                    return dungeonIds(args[1]);
                }
                if (args.length == 3) {
                    return filter(List.of("confirm"), args[2]);
                }
                return List.of();
            }
            case "create", "pos1", "pos2", "wand", "reload", "help", "list", "leave" -> {
                return List.of();
            }
            default -> {
                if (args.length == 2) {
                    return dungeonIds(args[1]);
                }
                return List.of();
            }
        }
    }

    private List<String> spawnCompletion(String[] args) {
        if (args.length == 2) {
            return filter(List.of("add", "remove", "list", "set", "test"), args[1]);
        }
        if (args.length == 3) {
            return dungeonIds(args[2]);
        }
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[2]);
        boolean adding = args[1].equalsIgnoreCase("add");
        if (args.length == 4) {
            if (adding) {
                // El ID de un spawn nuevo es libre: no conviene sugerir los existentes.
                return List.of();
            }
            return dungeon == null ? List.of() : filter(new ArrayList<>(dungeon.getSpawns().keySet()), args[3]);
        }
        if (args.length == 5) {
            if (adding) {
                return entityHints(args[4]);
            }
            return filter(AdminCommands.spawnSettingKeys(), args[4]);
        }
        if (args.length == 6) {
            if (adding) {
                return filter(List.of("1", "2", "3", "5", "10", "15", "20"), args[5]);
            }
            return filter(valueHints(args[4]), args[5]);
        }
        return List.of();
    }

    private List<String> bossCompletion(String[] args) {
        if (args.length == 2) {
            return filter(List.of("create", "remove", "list", "set", "loc", "test"), args[1]);
        }
        if (args.length == 3) {
            return dungeonIds(args[2]);
        }
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[2]);
        boolean creating = args[1].equalsIgnoreCase("create");
        if (args.length == 4) {
            if (creating) {
                return List.of();
            }
            return dungeon == null ? List.of() : filter(new ArrayList<>(dungeon.getBosses().keySet()), args[3]);
        }
        if (args.length == 5) {
            if (creating) {
                return entityHints(args[4]);
            }
            return filter(AdminCommands.bossSettingKeys(), args[4]);
        }
        if (args.length == 6) {
            return filter(valueHints(args[4]), args[5]);
        }
        return List.of();
    }

    private List<String> lootCompletion(String[] args) {
        if (args.length == 2) {
            return filter(List.of("create", "remove", "list", "info", "add", "remove-entry", "set"), args[1]);
        }
        if (args.length == 3) {
            return dungeonIds(args[2]);
        }
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[2]);
        if (args.length == 4 && dungeon != null) {
            if (args[1].equalsIgnoreCase("create")) {
                return List.of();
            }
            return filter(new ArrayList<>(dungeon.getLootTables().keySet()), args[3]);
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("set")) {
            return filter(List.of("rolls", "unique"), args[4]);
        }
        if (args.length == 6 && args[1].equalsIgnoreCase("set") && args[4].equalsIgnoreCase("unique")) {
            return filter(List.of("true", "false"), args[5]);
        }
        return List.of();
    }

    private List<String> chestCompletion(String[] args) {
        if (args.length == 2) {
            return filter(List.of("add", "remove", "list", "set"), args[1]);
        }
        if (args.length == 3) {
            return dungeonIds(args[2]);
        }
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[2]);
        if (dungeon == null) {
            return List.of();
        }
        if (args.length == 4) {
            if (args[1].equalsIgnoreCase("add")) {
                return List.of();
            }
            return filter(new ArrayList<>(dungeon.getChests().keySet()), args[3]);
        }
        if (args.length == 5) {
            if (args[1].equalsIgnoreCase("add")) {
                return filter(new ArrayList<>(dungeon.getLootTables().keySet()), args[4]);
            }
            return filter(AdminCommands.chestSettingKeys(), args[4]);
        }
        if (args.length == 6) {
            if (args[4].equalsIgnoreCase("loot-table")) {
                return filter(new ArrayList<>(dungeon.getLootTables().keySet()), args[5]);
            }
            return filter(valueHints(args[4]), args[5]);
        }
        return List.of();
    }

    private List<String> rewardCompletion(String[] args) {
        if (args.length == 2) {
            return filter(List.of("add", "remove", "list", "set"), args[1]);
        }
        if (args.length == 3) {
            return dungeonIds(args[2]);
        }
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[2]);
        if (dungeon == null) {
            return List.of();
        }
        if (args.length == 4) {
            if (args[1].equalsIgnoreCase("add")) {
                return List.of();
            }
            return filter(new ArrayList<>(dungeon.getRewards().keySet()), args[3]);
        }
        if (args.length == 5) {
            if (args[1].equalsIgnoreCase("add")) {
                return filter(new ArrayList<>(dungeon.getLootTables().keySet()), args[4]);
            }
            return filter(AdminCommands.rewardSettingKeys(), args[4]);
        }
        if (args.length == 6) {
            if (args[4].equalsIgnoreCase("loot-table")) {
                return filter(new ArrayList<>(dungeon.getLootTables().keySet()), args[5]);
            }
            return filter(valueHints(args[4]), args[5]);
        }
        return List.of();
    }

    private List<String> setCompletion(String[] args) {
        if (args.length == 2) {
            return dungeonIds(args[1]);
        }
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[1]);
        if (args.length == 3 && dungeon != null) {
            return filter(new ArrayList<>(dungeon.getSettings().keys()), args[2]);
        }
        if (args.length == 4) {
            return filter(valueHints(args[2]), args[3]);
        }
        return List.of();
    }

    private List<String> valueHints(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("equipment.")) {
            return List.of("hand", "clear");
        }
        if (normalized.contains("enabled") || normalized.equals("unique")
                || normalized.equals("glowing") || normalized.equals("baby")
                || normalized.equals("ai") || normalized.equals("persistent")
                || normalized.equals("require-ground")
                || normalized.equals("one-time") || normalized.equals("direct-give")
                || normalized.equals("announce") || normalized.equals("announce-spawn")
                || normalized.equals("announce-death") || normalized.equals("clear-vanilla-drops")
                || normalized.equals("exit-on-leave-region") || normalized.equals("leave-on-death")
                || normalized.equals("reset-on-empty") || normalized.equals("auto-enter")
                || normalized.equals("auto-start") || normalized.equals("allow-rejoin")
                || normalized.equals("protection") || normalized.equals("allow-chest-break")
                || normalized.equals("bosses-after-waves")
                || normalized.equals("burn-in-daylight")) {
            return List.of("true", "false");
        }
        if (normalized.equals("complete-on")) {
            return List.of("BOSSES", "KILLS", "BOSSES_AND_KILLS", "MANUAL");
        }
        if (normalized.equals("type")) {
            return List.of("VANILLA", "MYTHIC");
        }
        if (normalized.equals("entity")) {
            return entityHints("");
        }
        if (normalized.equals("mythic")) {
            List<String> ids = new ArrayList<>();
            if (plugin.getMythicMobsHook() != null) {
                ids.addAll(plugin.getMythicMobsHook().listMobIds());
            }
            return filter(ids, "");
        }
        if (normalized.endsWith("bossbar.color") || normalized.equals("color")) {
            return List.of("PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE");
        }
        if (normalized.endsWith("bossbar.style") || normalized.equals("style")) {
            return List.of("PROGRESS", "NOTCHED_6", "NOTCHED_10", "NOTCHED_12", "NOTCHED_20", "SOLID");
        }
        if (normalized.equals("location")) {
            return onlinePlayers();
        }
        return List.of();
    }

    private List<String> entityHints(String prefix) {
        List<String> values = new ArrayList<>();
        for (EntityType type : Registry.ENTITY_TYPE) {
            if (type.isSpawnable() && type.getEntityClass() != null
                    && org.bukkit.entity.LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
                values.add(type.name());
            }
        }
        if (plugin.getMythicMobsHook() != null) {
            for (String id : plugin.getMythicMobsHook().listMobIds()) {
                values.add("mythic:" + id);
            }
        }
        return filter(values, prefix);
    }

    private List<String> onlinePlayers() {
        List<String> names = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> dungeonIds(String prefix) {
        List<String> ids = new ArrayList<>();
        for (Dungeon dungeon : plugin.getDungeonManager().getDungeons()) {
            ids.add(dungeon.getId());
        }
        return filter(ids, prefix);
    }

    private List<String> filter(List<String> values, String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(needle)) {
                result.add(value);
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }
}
