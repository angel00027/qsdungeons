package org.qs.qsdungeons.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.Dungeon;
import org.qs.qsdungeons.dungeon.DungeonState;
import org.qs.qsdungeons.dungeon.instance.DungeonInstance;

import java.util.Locale;

/**
 * Resuelve los placeholders internos de QSDungeons y delega en PlaceholderAPI.
 *
 * <p>Placeholders internos: {prefix} {player} {dungeon} {dungeon_id} {dungeon_name}
 * {state} {players} {max_players} {time} {time_left} {kills} {total} {progress}
 * {progress_bar} {boss} {boss_health} {boss_max_health} {boss_percent} {world}</p>
 */
public final class PlaceholderService {

    private final QsDungeons plugin;

    public PlaceholderService(QsDungeons plugin) {
        this.plugin = plugin;
    }

    public String apply(String text, Player player, DungeonInstance instance, LivingEntity boss) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        DungeonInstance context = instance;
        if (context == null && player != null && plugin.getDungeonManager() != null) {
            context = plugin.getDungeonManager().getPlayerInstance(player);
        }
        LivingEntity bossContext = boss;
        if (bossContext == null && context != null) {
            bossContext = context.getPrimaryBoss();
        }

        String result = text;
        result = result.replace("{player}", player == null ? "" : player.getName());
        result = result.replace("{world}", player != null && player.getWorld() != null
                ? player.getWorld().getName()
                : "");

        if (context != null) {
            Dungeon dungeon = context.getDungeon();
            result = result.replace("{dungeon}", dungeon.getId());
            result = result.replace("{dungeon_id}", dungeon.getId());
            result = result.replace("{dungeon_name}", dungeon.getDisplayName());
            result = result.replace("{state}", stateLabel(context.getState()));
            result = result.replace("{players}", String.valueOf(context.getPlayerCount()));
            result = result.replace("{max_players}", String.valueOf(dungeon.getSettings().getMaxPlayers()));
            result = result.replace("{time}", formatTime(context.getElapsedSeconds()));
            result = result.replace("{time_left}", formatTime(context.getRemainingSeconds()));
            result = result.replace("{kills}", String.valueOf(context.getKills()));
            result = result.replace("{total}", String.valueOf(context.getTotalEnemies()));
            result = result.replace("{mobs}", String.valueOf(context.getTotalMobs()));
            result = result.replace("{bosses}", String.valueOf(context.getInitialBossCount()));
            result = result.replace("{kills_remaining}", String.valueOf(Math.max(0, context.getRequiredKills() - context.getKills())));
            result = result.replace("{progress}", String.format(Locale.US, "%.0f", context.getProgress() * 100.0D));
            result = result.replace("{progress_bar}", progressBar(context.getProgress()));
        } else {
            result = result.replace("{dungeon}", "");
            result = result.replace("{dungeon_id}", "");
            result = result.replace("{dungeon_name}", "");
            result = result.replace("{state}", "");
            result = result.replace("{players}", "0");
            result = result.replace("{max_players}", "0");
            result = result.replace("{time}", "00:00");
            result = result.replace("{time_left}", "00:00");
            result = result.replace("{kills}", "0");
            result = result.replace("{total}", "0");
            result = result.replace("{mobs}", "0");
            result = result.replace("{bosses}", "0");
            result = result.replace("{kills_remaining}", "0");
            result = result.replace("{progress}", "0");
            result = result.replace("{progress_bar}", progressBar(0.0D));
        }

        if (bossContext != null && !bossContext.isDead()) {
            String bossName = bossContext.customName() == null
                    ? bossContext.getType().name()
                    : org.qs.qsdungeons.util.Text.plain(bossContext.customName());
            double health = bossContext.getHealth();
            AttributeInstance maxHealthAttribute = bossContext.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maxHealth = maxHealthAttribute == null ? 0.0D : maxHealthAttribute.getValue();
            result = result.replace("{boss}", bossName);
            result = result.replace("{boss_health}", formatNumber(health));
            result = result.replace("{boss_max_health}", formatNumber(maxHealth));
            result = result.replace("{boss_percent}", String.format(Locale.US, "%.0f", maxHealth <= 0 ? 0 : health / maxHealth * 100.0D));
        } else {
            result = result.replace("{boss}", plugin.getMessages().raw("placeholder.boss-none"));
            result = result.replace("{boss_health}", "0");
            result = result.replace("{boss_max_health}", "0");
            result = result.replace("{boss_percent}", "0");
        }

        if (player != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            result = PapiHook.apply(player, result);
        }
        return result;
    }

    public String apply(String text, DungeonInstance instance) {
        return apply(text, null, instance, null);
    }

    public String apply(String text, Player player) {
        return apply(text, player, null, null);
    }

    public String formatTime(long totalSeconds) {
        long seconds = Math.max(0, totalSeconds);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long rest = seconds % 60;
        String format = plugin.getConfigManager().getTimeFormat();
        if (format != null && format.contains("hh")) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, rest);
        }
        return String.format(Locale.US, "%02d:%02d", minutes + hours * 60, rest);
    }

    public String progressBar(double progress) {
        int length = plugin.getConfigManager().getProgressBarLength();
        String symbol = plugin.getConfigManager().getProgressBarSymbol();
        String completedColor = plugin.getConfigManager().getProgressBarCompletedColor();
        String remainingColor = plugin.getConfigManager().getProgressBarRemainingColor();
        int completed = (int) Math.round(Math.max(0.0D, Math.min(1.0D, progress)) * length);
        return completedColor + symbol.repeat(completed)
                + remainingColor + symbol.repeat(Math.max(0, length - completed));
    }

    private String stateLabel(DungeonState state) {
        if (state == null) {
            return "";
        }
        String raw = plugin.getMessages().raw("placeholder.state." + state.name().toLowerCase(Locale.ROOT));
        return raw == null ? state.name() : raw;
    }

    private String formatNumber(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.1f", value);
    }
}
