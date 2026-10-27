package org.qs.qsdungeons.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.worldedit.WorldEditHook;

import java.io.File;
import java.util.Locale;
import java.util.Map;

/**
 * Comandos de exportacion/importacion con WorldEdit y archivos .zip.
 */
public final class TransferCommands {

    private final QsDungeons plugin;

    public TransferCommands(QsDungeons plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return false;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "export" -> export(sender, args);
            case "import" -> importArchive(sender, args);
            case "save" -> save(sender, args);
            case "paste" -> paste(sender, args);
            case "wand" -> wand(sender);
            case "structures" -> structures(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void export(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/dungeon export <id> <config|all> [nombre]");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[1]);
        if (dungeon == null) {
            return;
        }
        String mode = args[2].toLowerCase(Locale.ROOT);
        String name = args.length >= 4 ? args[3] : null;
        try {
            File file;
            if (mode.equals("config")) {
                file = plugin.getDungeonArchive().exportConfig(dungeon, name);
            } else if (mode.equals("all")) {
                if (!worldEdit(sender)) {
                    return;
                }
                String structureName = name == null ? dungeon.getId() + "-build" : name + "-build";
                plugin.getWorldEditHook().saveStructure(null, dungeon, structureName,
                        plugin.getConfigManager().includeEntities());
                file = plugin.getDungeonArchive().exportAll(dungeon, name);
            } else {
                usage(sender, "/dungeon export <id> <config|all> [nombre]");
                return;
            }
            send(sender, "command.export-done", Map.of(
                    "dungeon", dungeon.getId(),
                    "file", plugin.getDungeonArchive().displayPath(file)));
        } catch (Exception exception) {
            send(sender, "command.export-failed", Map.of("error", message(exception)));
        }
    }

    private void importArchive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/dungeon import <id> <archivo>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-zA-Z0-9_\\-]{1,32}")) {
            send(sender, "command.invalid-id", Map.of("id", id));
            return;
        }
        File file = plugin.getDungeonArchive().resolveFile(args[2]);
        if (file == null || !file.isFile()) {
            send(sender, "general.file-not-found", Map.of("file", args[2]));
            return;
        }
        String lower = file.getName().toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".zip")) {
                plugin.getDungeonArchive().importConfig(id, file);
                plugin.getDungeonManager().reload();
                send(sender, "command.import-done", Map.of(
                        "dungeon", id,
                        "file", plugin.getDungeonArchive().displayPath(file)));
                return;
            }
            if (lower.endsWith(".schem") || lower.endsWith(".schematic") || lower.endsWith(".nbt")) {
                Dungeon dungeon = dungeon(sender, id);
                if (dungeon == null) {
                    return;
                }
                File target = plugin.getDungeonArchive().importStructure(dungeon, file);
                send(sender, "command.import-done", Map.of(
                        "dungeon", id,
                        "file", plugin.getDungeonArchive().displayPath(target)));
                return;
            }
            send(sender, "command.import-failed", Map.of("error", "formato no soportado"));
        } catch (Exception exception) {
            send(sender, "command.import-failed", Map.of("error", message(exception)));
        }
    }

    private void save(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "/dungeon save <id> <nombre>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[1]);
        if (dungeon == null) {
            return;
        }
        if (!worldEdit(sender)) {
            return;
        }
        try {
            File file = plugin.getWorldEditHook().saveStructure(
                    sender instanceof Player player ? player : null,
                    dungeon, args[2], plugin.getConfigManager().includeEntities());
            send(sender, "command.schematic-saved", Map.of(
                    "file", plugin.getDungeonArchive().displayPath(file)));
        } catch (Exception exception) {
            send(sender, "command.export-failed", Map.of("error", message(exception)));
        }
    }

    private void paste(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            usage(sender, "/dungeon paste <id> <nombre>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[1]);
        if (dungeon == null) {
            return;
        }
        if (!worldEdit(sender)) {
            return;
        }
        try {
            File file = plugin.getWorldEditHook().paste(player, dungeon, args[2],
                    plugin.getConfigManager().ignoreAirOnPaste());
            send(sender, "command.schematic-pasted", Map.of("name", file.getName()));
        } catch (Exception exception) {
            send(sender, "command.import-failed", Map.of("error", message(exception)));
        }
    }

    private void structures(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "/dungeon structures <id>");
            return;
        }
        Dungeon dungeon = dungeon(sender, args[1]);
        if (dungeon == null) {
            return;
        }
        if (!worldEdit(sender)) {
            return;
        }
        java.util.List<String> names = plugin.getWorldEditHook().listStructures(dungeon);
        send(sender, "command.structures", Map.of(
                "structures", names.isEmpty() ? "-" : String.join(", ", names)));
    }

    private void wand(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (!worldEdit(sender)) {
            return;
        }
        plugin.getWorldEditHook().giveWand(player);
        send(sender, "command.wand-given", Map.of());
    }

    private boolean worldEdit(CommandSender sender) {
        WorldEditHook hook = plugin.getWorldEditHook();
        if (hook == null || !hook.isAvailable()) {
            send(sender, "general.worldedit-missing", Map.of());
            return false;
        }
        return true;
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

    private String message(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message;
    }

    private void usage(CommandSender sender, String usage) {
        send(sender, "general.usage", Map.of("usage", usage));
    }

    private void send(CommandSender sender, String key, Map<String, String> replacements) {
        plugin.getMessages().send(sender, key, replacements);
    }
}
