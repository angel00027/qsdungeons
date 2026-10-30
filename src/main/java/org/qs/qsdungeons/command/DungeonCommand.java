package org.qs.qsdungeons.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.DungeonState;
import org.qs.qsdungeons.dungeon.instance.DungeonInstance;
import org.qs.qsdungeons.storage.LocationSerializer;

import java.util.Locale;
import java.util.Map;

/**
 * Punto de entrada de /dungeon. Deriva los subcomandos de configuracion a
 * {@link AdminCommands} y los de import/export a {@link TransferCommands}.
 */
public final class DungeonCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "qsdungeons.admin";

    private final QsDungeons plugin;
    private final AdminCommands admin;
    private final TransferCommands transfer;

    public DungeonCommand(QsDungeons plugin) {
        this.plugin = plugin;
        SelectionManager selections = new SelectionManager();
        this.admin = new AdminCommands(plugin, selections);
        this.transfer = new TransferCommands(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help", "?" -> help(sender);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "start" -> start(sender, args);
            case "stop" -> stop(sender, args);
            case "reset" -> reset(sender, args);
            case "reload" -> reload(sender);
            case "editor" -> adminGui(sender, args);
            case "menu" -> playerMenu(sender);
            case "cooldown-location" -> cooldownLocation(sender, args);
            case "cooldown-protection" -> cooldownProtection(sender, args);
            case "protection" -> protection(sender, args);
            case "tp", "teleport", "ir" -> teleport(sender, args);
            case "top", "ranking" -> top(sender, args);
            case "stats", "estadisticas" -> stats(sender, args);
            case "setwarp" -> setWarp(sender, args);
            case "door", "puerta" -> door(sender, args);
            case "markers", "marcadores" -> markers(sender, args);
            default -> {
                if (isAdminSubcommand(sub)) {
                    if (!requireAdmin(sender)) {
                        return true;
                    }
                    admin.handle(sender, args);
                    return true;
                }
                if (isTransferSubcommand(sub)) {
                    if (!requireAdmin(sender)) {
                        return true;
                    }
                    transfer.handle(sender, args);
                    return true;
                }
                plugin.getMessages().send(sender, "general.unknown-subcommand", Map.of("arg", sub));
            }
        }
        return true;
    }

    private void help(CommandSender sender) {
        plugin.getMessages().send(sender, "command.help.header");
        plugin.getMessages().send(sender, "command.help.title");
        plugin.getMessages().send(sender, "command.help.player");
        plugin.getMessages().send(sender, "command.help.player-extra");
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            plugin.getMessages().send(sender, "command.help.admin");
            plugin.getMessages().send(sender, "command.help.transfer");
            plugin.getMessages().send(sender, "command.help.extra");
        }
        plugin.getMessages().send(sender, "command.help.header");
    }

    private void list(CommandSender sender) {
        if (plugin.getDungeonManager().getDungeons().isEmpty()) {
            plugin.getMessages().send(sender, "command.list-empty");
            return;
        }
        plugin.getMessages().send(sender, "command.list-header", Map.of(
                "amount", String.valueOf(plugin.getDungeonManager().getDungeons().size())));
        for (Dungeon dungeon : plugin.getDungeonManager().getDungeons()) {
            DungeonInstance instance = plugin.getDungeonManager().getInstance(dungeon.getId());
            DungeonState state = instance == null ? DungeonState.IDLE : instance.getState();
            int players = instance == null ? 0 : instance.getPlayerCount();
            plugin.getMessages().send(sender, "command.list-entry", Map.of(
                    "dungeon", dungeon.getId(),
                    "dungeon_name", dungeon.getDisplayName(),
                    "state", stateLabel(state),
                    "players", String.valueOf(players),
                    "max_players", String.valueOf(dungeon.getSettings().getMaxPlayers()),
                    "enabled", dungeon.isEnabled() ? "si" : "no",
                    "ready", dungeon.isReady() ? "si" : "no"));
        }
    }

    private void info(CommandSender sender, String[] args) {
        Dungeon dungeon;
        if (args.length >= 2) {
            dungeon = plugin.getDungeonManager().getDungeon(args[1]);
            if (dungeon == null) {
                plugin.getMessages().send(sender, "general.unknown-dungeon", Map.of("dungeon", args[1]));
                return;
            }
        } else if (sender instanceof Player player) {
            DungeonInstance instance = plugin.getDungeonManager().getPlayerInstance(player);
            if (instance == null) {
                plugin.getMessages().send(sender, "general.not-in-dungeon");
                return;
            }
            dungeon = instance.getDungeon();
        } else {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/dungeon info <id>"));
            return;
        }

        DungeonInstance instance = plugin.getDungeonManager().getInstance(dungeon.getId());
        DungeonState state = instance == null ? DungeonState.IDLE : instance.getState();
        java.util.Map<String, String> info = new java.util.LinkedHashMap<>();
        info.put("dungeon", dungeon.getId());
        info.put("dungeon_name", dungeon.getDisplayName());
        info.put("state", stateLabel(state));
        info.put("players", String.valueOf(instance == null ? 0 : instance.getPlayerCount()));
        info.put("max_players", String.valueOf(dungeon.getSettings().getMaxPlayers()));
        info.put("region", dungeon.getRegion() == null ? "-" : dungeon.getRegion().describe());
        info.put("entry", dungeon.getEntry() == null ? "-" : describe(dungeon.getEntry()));
        info.put("exit", dungeon.getExit() == null ? "-" : describe(dungeon.getExit()));
        info.put("bosses", String.valueOf(dungeon.getBosses().size()));
        info.put("spawns", String.valueOf(dungeon.getSpawns().size()));
        info.put("chests", String.valueOf(dungeon.getChests().size()));
        info.put("rewards", String.valueOf(dungeon.getRewards().size()));
        info.put("loot", String.valueOf(dungeon.getLootTables().size()));
        info.put("enabled", dungeon.isEnabled() ? "si" : "no");
        info.put("ready", dungeon.isReady() ? "si" : "no");
        plugin.getMessages().send(sender, "command.info", info);
    }

    private void join(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/dungeon join <id>"));
            return;
        }
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[1]);
        if (dungeon == null) {
            plugin.getMessages().send(sender, "general.unknown-dungeon", Map.of("dungeon", args[1]));
            return;
        }
        plugin.getDungeonManager().join(player, dungeon);
    }

    private void leave(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        plugin.getDungeonManager().leave(player, true);
    }

    private void start(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/dungeon start <id>"));
            return;
        }
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[1]);
        if (dungeon == null) {
            plugin.getMessages().send(sender, "general.unknown-dungeon", Map.of("dungeon", args[1]));
            return;
        }
        plugin.getDungeonManager().start(dungeon, sender);
    }

    private void stop(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/dungeon stop <id>"));
            return;
        }
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[1]);
        if (dungeon == null) {
            plugin.getMessages().send(sender, "general.unknown-dungeon", Map.of("dungeon", args[1]));
            return;
        }
        plugin.getDungeonManager().stop(dungeon, sender, false);
    }

    private void reset(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/dungeon reset <id>"));
            return;
        }
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[1]);
        if (dungeon == null) {
            plugin.getMessages().send(sender, "general.unknown-dungeon", Map.of("dungeon", args[1]));
            return;
        }
        plugin.getDungeonManager().reset(dungeon);
        plugin.getMessages().send(sender, "dungeon.reset", Map.of("dungeon", dungeon.getId()));
    }

    private void reload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        plugin.getDungeonManager().reload();
        plugin.getMessages().send(sender, "general.reloaded");
    }

    private void adminGui(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length >= 2) {
            Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[1]);
            if (dungeon == null) {
                plugin.getMessages().send(sender, "general.unknown-dungeon", Map.of("dungeon", args[1]));
                return;
            }
            plugin.getGuiManager().openDungeonEditor(player, dungeon);
            return;
        }
        plugin.getGuiManager().openMain(player);
    }

    private void playerMenu(CommandSender sender) {
        Player player = player(sender);
        if (player != null) {
            plugin.getGuiManager().openPlayerDungeons(player, 0);
        }
    }

    private void cooldownLocation(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
            plugin.getConfig().set("cooldown-protection.location", null);
            plugin.saveConfig();
            plugin.getMessages().sendRaw(sender, "<yellow>Ubicacion de cooldown eliminada.", null);
            return;
        }
        var section = plugin.getConfig().getConfigurationSection("cooldown-protection.location");
        if (section == null) {
            section = plugin.getConfig().createSection("cooldown-protection.location");
        }
        LocationSerializer.write(section, player.getLocation());
        plugin.saveConfig();
        plugin.getMessages().sendRaw(sender, "<green>Ubicacion de expulsion guardada en tu posicion.", null);
    }

    private void cooldownProtection(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        boolean current = plugin.getConfig().getBoolean("cooldown-protection.enabled", false);
        boolean enabled = args.length >= 2 && switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on", "enable", "true" -> true;
            case "off", "disable", "false" -> false;
            default -> !current;
        };
        plugin.getConfig().set("cooldown-protection.enabled", enabled);
        plugin.saveConfig();
        plugin.getMessages().sendRaw(sender, "<green>Expulsion por cooldown: "
                + (enabled ? "<white>activada" : "<white>desactivada"), null);
    }

    private void protection(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        boolean current = plugin.getConfig().getBoolean("protection.admin-bypass", false);
        boolean enabled = args.length >= 2 && switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on", "enable", "true" -> true;
            case "off", "disable", "false" -> false;
            default -> !current;
        };
        plugin.getConfig().set("protection.admin-bypass", enabled);
        plugin.saveConfig();
        plugin.getMessages().sendRaw(sender, "<green>Bypass de proteccion para administradores: "
                + (enabled ? "<white>activado" : "<white>desactivado"), null);
    }

    private void teleport(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/dungeon tp <id> [jugador]"));
            return;
        }
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[1]);
        if (dungeon == null) {
            plugin.getMessages().send(sender, "general.unknown-dungeon", Map.of("dungeon", args[1]));
            return;
        }
        Player target;
        if (args.length >= 3) {
            if (!sender.hasPermission("qsdungeons.tp.others")) {
                plugin.getMessages().send(sender, "general.no-permission");
                return;
            }
            target = org.bukkit.Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.getMessages().send(sender, "general.player-not-found", Map.of("player", args[2]));
                return;
            }
        } else {
            target = player(sender);
            if (target == null) {
                return;
            }
            if (!sender.hasPermission("qsdungeons.tp")) {
                plugin.getMessages().send(sender, "general.no-permission");
                return;
            }
        }
        plugin.getDungeonManager().teleport(target, dungeon);
    }

    private void top(CommandSender sender, String[] args) {
        Dungeon dungeon = args.length >= 2 ? plugin.getDungeonManager().getDungeon(args[1]) : null;
        if (dungeon == null && sender instanceof Player player) {
            DungeonInstance instance = plugin.getDungeonManager().getPlayerInstance(player);
            dungeon = instance == null ? null : instance.getDungeon();
        }
        if (dungeon == null) {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/dungeon top <id>"));
            return;
        }
        sendTop(sender, dungeon);
    }

    /**
     * Muestra el top 10 de mejores tiempos de una dungeon.
     */
    public void sendTop(CommandSender sender, Dungeon dungeon) {
        java.util.List<org.qs.qsdungeons.stats.StatsManager.Entry> top =
                plugin.getStatsManager().top(dungeon.getId(), 10);
        plugin.getMessages().send(sender, "top.header", Map.of("dungeon_name", dungeon.getDisplayName()));
        if (top.isEmpty()) {
            plugin.getMessages().send(sender, "top.empty");
            return;
        }
        int position = 1;
        for (org.qs.qsdungeons.stats.StatsManager.Entry entry : top) {
            plugin.getMessages().send(sender, "top.entry", Map.of(
                    "position", String.valueOf(position++),
                    "player", entry.name(),
                    "time", plugin.getPlaceholderService().formatDuration(entry.seconds()),
                    "completions", String.valueOf(entry.completions())));
        }
    }

    private void stats(CommandSender sender, String[] args) {
        java.util.UUID uuid;
        String name;
        if (args.length >= 2) {
            Player online = org.bukkit.Bukkit.getPlayerExact(args[1]);
            uuid = online != null ? online.getUniqueId() : plugin.getStatsManager().findByName(args[1]);
            name = online != null ? online.getName() : args[1];
            if (uuid == null) {
                plugin.getMessages().send(sender, "general.player-not-found", Map.of("player", args[1]));
                return;
            }
        } else {
            Player player = player(sender);
            if (player == null) {
                return;
            }
            uuid = player.getUniqueId();
            name = player.getName();
        }
        org.qs.qsdungeons.stats.StatsManager stats = plugin.getStatsManager();
        plugin.getMessages().send(sender, "stats.header", Map.of("player", name));
        plugin.getMessages().send(sender, "stats.total", Map.of(
                "completions", String.valueOf(stats.total(uuid, org.qs.qsdungeons.stats.StatsManager.COMPLETIONS)),
                "fails", String.valueOf(stats.total(uuid, org.qs.qsdungeons.stats.StatsManager.FAILS)),
                "deaths", String.valueOf(stats.total(uuid, org.qs.qsdungeons.stats.StatsManager.DEATHS)),
                "kills", String.valueOf(stats.total(uuid, org.qs.qsdungeons.stats.StatsManager.KILLS)),
                "boss_kills", String.valueOf(stats.total(uuid, org.qs.qsdungeons.stats.StatsManager.BOSS_KILLS))));
        for (Dungeon dungeon : plugin.getDungeonManager().getDungeons()) {
            long completions = stats.get(uuid, dungeon.getId(), org.qs.qsdungeons.stats.StatsManager.COMPLETIONS);
            long best = stats.get(uuid, dungeon.getId(), org.qs.qsdungeons.stats.StatsManager.BEST_TIME);
            if (completions <= 0 && best <= 0) {
                continue;
            }
            plugin.getMessages().send(sender, "stats.dungeon", Map.of(
                    "dungeon_name", dungeon.getDisplayName(),
                    "completions", String.valueOf(completions),
                    "best", best <= 0 ? "-" : plugin.getPlaceholderService().formatDuration(best)));
        }
    }

    private void setWarp(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/dungeon setwarp <id> [clear]"));
            return;
        }
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[1]);
        if (dungeon == null) {
            plugin.getMessages().send(sender, "general.unknown-dungeon", Map.of("dungeon", args[1]));
            return;
        }
        boolean clear = args.length >= 3 && args[2].equalsIgnoreCase("clear");
        dungeon.setWarp(clear ? null : player.getLocation().clone());
        plugin.getRepository().save(dungeon);
        plugin.getMessages().send(sender, clear ? "tp.warp-cleared" : "tp.warp-set", Map.of("dungeon", dungeon.getId()));
    }

    private void door(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 4) {
            plugin.getMessages().send(sender, "general.usage",
                    Map.of("usage", "/dungeon door <id> <puerta> <open|close>"));
            return;
        }
        Dungeon dungeon = plugin.getDungeonManager().getDungeon(args[1]);
        if (dungeon == null) {
            plugin.getMessages().send(sender, "general.unknown-dungeon", Map.of("dungeon", args[1]));
            return;
        }
        org.qs.qsdungeons.dungeon.DungeonDoor door = dungeon.getDoors().get(args[2].toLowerCase(Locale.ROOT));
        if (door == null || !door.isConfigured()) {
            plugin.getMessages().send(sender, "door.not-found", Map.of("door", args[2]));
            return;
        }
        boolean open = !args[3].equalsIgnoreCase("close");
        DungeonInstance instance = plugin.getDungeonManager().getInstance(dungeon.getId());
        if (open && instance != null && instance.isRunning()) {
            instance.openDoor(door);
        } else if (open) {
            door.open();
        } else {
            door.close();
        }
        plugin.getMessages().send(sender, open ? "door.opened" : "door.closed", Map.of("door", door.getId()));
    }

    private void markers(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        Dungeon dungeon = args.length >= 2 ? plugin.getDungeonManager().getDungeon(args[1])
                : plugin.getDungeonManager().findDungeonAt(player.getLocation());
        if (dungeon == null) {
            plugin.getMessages().send(sender, "general.usage", Map.of("usage", "/dungeon markers <id>"));
            return;
        }
        boolean shown = plugin.getMarkerService().toggle(player, dungeon);
        plugin.getMessages().send(sender, shown ? "markers.shown" : "markers.hidden",
                Map.of("dungeon", dungeon.getId()));
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.getMessages().send(sender, "general.player-only");
        return null;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        plugin.getMessages().send(sender, "general.no-permission");
        return false;
    }

    private String stateLabel(DungeonState state) {
        String raw = plugin.getMessages().raw("placeholder.state."
                + (state == null ? "idle" : state.name().toLowerCase(Locale.ROOT)));
        return raw == null ? (state == null ? "?" : state.name()) : raw;
    }

    private String describe(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return "-";
        }
        return location.getWorld().getName() + " " + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ();
    }

    private static boolean isAdminSubcommand(String sub) {
        return switch (sub) {
            case "create", "delete", "enable", "disable", "pos1", "pos2", "region", "entry",
                    "exit", "spawn", "boss", "loot", "chest", "reward", "set" -> true;
            default -> false;
        };
    }

    private static boolean isTransferSubcommand(String sub) {
        return switch (sub) {
            case "export", "import", "save", "paste", "wand", "structures" -> true;
            default -> false;
        };
    }

    public AdminCommands getAdmin() {
        return admin;
    }

    public TransferCommands getTransfer() {
        return transfer;
    }
}
