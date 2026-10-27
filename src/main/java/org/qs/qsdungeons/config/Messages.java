package org.qs.qsdungeons.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.instance.DungeonInstance;
import org.qs.qsdungeons.util.Text;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;

/**
 * Mensajes totalmente configurables en messages.yml usando MiniMessage.
 */
public final class Messages {

    private final QsDungeons plugin;
    private File file;
    private YamlConfiguration yaml;
    private String prefix = "";

    public Messages(QsDungeons plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "messages.yml");
        YamlDefaultsUpdater.update(plugin, "messages.yml");
        yaml = YamlConfiguration.loadConfiguration(file);
        prefix = yaml.getString("prefix", "");
    }

    public void reload() {
        load();
    }

    public String raw(String key) {
        return yaml == null ? null : yaml.getString(key);
    }

    public boolean has(String key) {
        return yaml != null && yaml.contains(key);
    }

    public String prefix() {
        return prefix;
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of(), null);
    }

    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        send(sender, key, replacements, null);
    }

    public void send(CommandSender sender, String key, Map<String, String> replacements,
                     DungeonInstance instance) {
        if (sender == null) {
            return;
        }
        String raw = raw(key);
        if (raw == null) {
            sender.sendMessage(Text.parse("<red>[QSDungeons] Falta el mensaje <white>" + key + "</white> en messages.yml"));
            return;
        }
        Player player = sender instanceof Player p ? p : null;
        String resolved = format(player, raw, replacements, instance, null);
        sender.sendMessage(Text.parse(resolved));
    }

    /**
     * Envia un texto MiniMessage que no es una clave de messages.yml.
     */
    public void sendRaw(CommandSender sender, String miniMessage, DungeonInstance instance) {
        if (sender == null || miniMessage == null || miniMessage.isBlank()) {
            return;
        }
        Player player = sender instanceof Player p ? p : null;
        String resolved = format(player, miniMessage, Map.of(), instance, null);
        sender.sendMessage(Text.parse(resolved));
    }

    public void broadcast(DungeonInstance instance, String key, Map<String, String> replacements) {
        if (instance == null) {
            return;
        }
        for (Player player : instance.getPlayersOnline()) {
            send(player, key, replacements, instance);
        }
    }

    public String format(Player player, String raw, Map<String, String> replacements,
                         DungeonInstance instance, LivingEntity boss) {
        if (raw == null) {
            return "";
        }
        String result = raw.replace("{prefix}", prefix == null ? "" : prefix);
        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        if (plugin.getPlaceholderService() != null) {
            result = plugin.getPlaceholderService().apply(result, player, instance, boss);
        }
        return result;
    }

    public String format(DungeonInstance instance, String raw) {
        return format(null, raw, Map.of(), instance, null);
    }

    public void title(Player player, String key, Map<String, String> replacements, DungeonInstance instance) {
        if (player == null) {
            return;
        }
        String title = raw(key + ".title");
        String subtitle = raw(key + ".subtitle");
        if (title == null && subtitle == null) {
            return;
        }
        Component titleComponent = Text.parse(format(player, title == null ? "" : title, replacements, instance, null));
        Component subtitleComponent = Text.parse(format(player, subtitle == null ? "" : subtitle, replacements, instance, null));
        player.showTitle(Title.title(titleComponent, subtitleComponent));
    }

    public void actionBar(Player player, String key, Map<String, String> replacements, DungeonInstance instance) {
        String raw = raw(key);
        if (player == null || raw == null) {
            return;
        }
        player.sendActionBar(Text.parse(format(player, raw, replacements, instance, null)));
    }

    public void logMissing(String key) {
        if (!has(key)) {
            plugin.getLogger().log(Level.WARNING, "Falta el mensaje '" + key + "' en messages.yml");
        }
    }

    public YamlConfiguration getYaml() {
        return yaml;
    }

    public File getFile() {
        return file;
    }

    public void save() {
        if (yaml == null || file == null) {
            return;
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar messages.yml", exception);
        }
    }
}
