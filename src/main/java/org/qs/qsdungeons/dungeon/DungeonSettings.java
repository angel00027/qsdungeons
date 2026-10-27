package org.qs.qsdungeons.dungeon;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ajustes de comportamiento de una dungeon.
 */
public final class DungeonSettings {

    private int maxPlayers = 8;
    private int minPlayers = 1;
    private int timeLimitSeconds = 1800;
    private boolean autoEnter = true;
    private boolean autoStart = true;
    private boolean exitOnLeaveRegion = true;
    private boolean leaveOnDeath = false;
    private boolean resetOnEmpty = true;
    private int resetDelaySeconds = 10;
    private int endDelaySeconds = 5;
    private boolean allowRejoin = true;
    private boolean protection = true;
    private boolean allowChestBreak = false;
    private boolean bossesAfterWaves = false;
    private String entryPermission = "";
    private int cooldownSeconds = 0;
    private CompleteMode completeOn = CompleteMode.BOSSES;
    private int requiredKills = -1;
    private BossBarSettings bossBar = new BossBarSettings();

    private final List<String> commandsOnStart = new ArrayList<>();
    private final List<String> commandsOnComplete = new ArrayList<>();
    private final List<String> commandsOnFail = new ArrayList<>();
    private final List<String> commandsOnReset = new ArrayList<>();

    public DungeonSettings() {
    }

    public DungeonSettings(DungeonSettings other) {
        if (other == null) {
            return;
        }
        this.maxPlayers = other.maxPlayers;
        this.minPlayers = other.minPlayers;
        this.timeLimitSeconds = other.timeLimitSeconds;
        this.autoEnter = other.autoEnter;
        this.autoStart = other.autoStart;
        this.exitOnLeaveRegion = other.exitOnLeaveRegion;
        this.leaveOnDeath = other.leaveOnDeath;
        this.resetOnEmpty = other.resetOnEmpty;
        this.resetDelaySeconds = other.resetDelaySeconds;
        this.endDelaySeconds = other.endDelaySeconds;
        this.allowRejoin = other.allowRejoin;
        this.protection = other.protection;
        this.allowChestBreak = other.allowChestBreak;
        this.bossesAfterWaves = other.bossesAfterWaves;
        this.entryPermission = other.entryPermission;
        this.cooldownSeconds = other.cooldownSeconds;
        this.completeOn = other.completeOn;
        this.requiredKills = other.requiredKills;
        this.bossBar = new BossBarSettings(other.bossBar);
        this.commandsOnStart.addAll(other.commandsOnStart);
        this.commandsOnComplete.addAll(other.commandsOnComplete);
        this.commandsOnFail.addAll(other.commandsOnFail);
        this.commandsOnReset.addAll(other.commandsOnReset);
    }

    public static DungeonSettings read(ConfigurationSection section, DungeonSettings fallback) {
        DungeonSettings result = new DungeonSettings(fallback);
        if (section == null) {
            return result;
        }
        result.maxPlayers = section.getInt("max-players", result.maxPlayers);
        result.minPlayers = section.getInt("min-players", result.minPlayers);
        result.timeLimitSeconds = section.getInt("time-limit-seconds", result.timeLimitSeconds);
        result.autoEnter = section.getBoolean("auto-enter", result.autoEnter);
        result.autoStart = section.getBoolean("auto-start", result.autoStart);
        result.exitOnLeaveRegion = section.getBoolean("exit-on-leave-region", result.exitOnLeaveRegion);
        result.leaveOnDeath = section.getBoolean("leave-on-death", result.leaveOnDeath);
        result.resetOnEmpty = section.getBoolean("reset-on-empty", result.resetOnEmpty);
        result.resetDelaySeconds = section.getInt("reset-delay-seconds", result.resetDelaySeconds);
        result.endDelaySeconds = section.getInt("end-delay-seconds", result.endDelaySeconds);
        result.allowRejoin = section.getBoolean("allow-rejoin", result.allowRejoin);
        result.protection = section.getBoolean("protection", result.protection);
        result.allowChestBreak = section.getBoolean("allow-chest-break", result.allowChestBreak);
        result.bossesAfterWaves = section.getBoolean("bosses-after-waves", result.bossesAfterWaves);
        result.entryPermission = section.getString("entry-permission", result.entryPermission);
        result.cooldownSeconds = section.getInt("cooldown-seconds", result.cooldownSeconds);
        result.completeOn = CompleteMode.from(section.getString("complete-on"), result.completeOn);
        result.requiredKills = section.getInt("required-kills", result.requiredKills);
        result.bossBar = BossBarSettings.read(section.getConfigurationSection("bossbar"), result.bossBar);
        result.commandsOnStart.clear();
        result.commandsOnStart.addAll(section.getStringList("commands-on-start"));
        result.commandsOnComplete.clear();
        result.commandsOnComplete.addAll(section.getStringList("commands-on-complete"));
        result.commandsOnFail.clear();
        result.commandsOnFail.addAll(section.getStringList("commands-on-fail"));
        result.commandsOnReset.clear();
        result.commandsOnReset.addAll(section.getStringList("commands-on-reset"));
        return result;
    }

    public void write(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        section.set("max-players", maxPlayers);
        section.set("min-players", minPlayers);
        section.set("time-limit-seconds", timeLimitSeconds);
        section.set("auto-enter", autoEnter);
        section.set("auto-start", autoStart);
        section.set("exit-on-leave-region", exitOnLeaveRegion);
        section.set("leave-on-death", leaveOnDeath);
        section.set("reset-on-empty", resetOnEmpty);
        section.set("reset-delay-seconds", resetDelaySeconds);
        section.set("end-delay-seconds", endDelaySeconds);
        section.set("allow-rejoin", allowRejoin);
        section.set("protection", protection);
        section.set("allow-chest-break", allowChestBreak);
        section.set("bosses-after-waves", bossesAfterWaves);
        section.set("entry-permission", entryPermission);
        section.set("cooldown-seconds", cooldownSeconds);
        section.set("complete-on", completeOn.name());
        section.set("required-kills", requiredKills);
        bossBar.write(section.createSection("bossbar"));
        section.set("commands-on-start", commandsOnStart);
        section.set("commands-on-complete", commandsOnComplete);
        section.set("commands-on-fail", commandsOnFail);
        section.set("commands-on-reset", commandsOnReset);
    }

    /**
     * Cambia un ajuste sencillo desde el comando /dungeon set.
     *
     * @return true si la clave existe.
     */
    public boolean set(String key, String value) {
        String normalized = key.toLowerCase(Locale.ROOT);
        try {
            switch (normalized) {
                case "max-players" -> maxPlayers = Integer.parseInt(value);
                case "min-players" -> minPlayers = Integer.parseInt(value);
                case "time-limit-seconds" -> timeLimitSeconds = Integer.parseInt(value);
                case "auto-enter" -> autoEnter = Boolean.parseBoolean(value);
                case "auto-start" -> autoStart = Boolean.parseBoolean(value);
                case "exit-on-leave-region" -> exitOnLeaveRegion = Boolean.parseBoolean(value);
                case "leave-on-death" -> leaveOnDeath = Boolean.parseBoolean(value);
                case "reset-on-empty" -> resetOnEmpty = Boolean.parseBoolean(value);
                case "reset-delay-seconds" -> resetDelaySeconds = Integer.parseInt(value);
                case "end-delay-seconds" -> endDelaySeconds = Integer.parseInt(value);
                case "allow-rejoin" -> allowRejoin = Boolean.parseBoolean(value);
                case "protection" -> protection = Boolean.parseBoolean(value);
                case "allow-chest-break" -> allowChestBreak = Boolean.parseBoolean(value);
                case "bosses-after-waves" -> bossesAfterWaves = Boolean.parseBoolean(value);
                case "entry-permission" -> entryPermission = value;
                case "cooldown-seconds" -> cooldownSeconds = Integer.parseInt(value);
                case "complete-on" -> completeOn = CompleteMode.from(value, completeOn);
                case "required-kills" -> requiredKills = Integer.parseInt(value);
                case "bossbar.enabled" -> bossBar.setEnabled(Boolean.parseBoolean(value));
                case "bossbar.title" -> bossBar.setTitle(value);
                case "bossbar.color" -> bossBar.setColor(value);
                case "bossbar.style" -> bossBar.setStyle(value);
                case "bossbar.radius" -> bossBar.setRadius(Double.parseDouble(value));
                default -> {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public Set<String> keys() {
        return Set.of(
                "max-players", "min-players", "time-limit-seconds", "auto-enter", "auto-start",
                "exit-on-leave-region", "leave-on-death", "reset-on-empty", "reset-delay-seconds",
                "end-delay-seconds", "allow-rejoin", "protection", "allow-chest-break", "bosses-after-waves", "entry-permission",
                "cooldown-seconds", "complete-on", "required-kills", "bossbar.enabled",
                "bossbar.title", "bossbar.color", "bossbar.style", "bossbar.radius"
        );
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public boolean isAutoEnter() {
        return autoEnter;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public boolean isExitOnLeaveRegion() {
        return exitOnLeaveRegion;
    }

    public boolean isLeaveOnDeath() {
        return leaveOnDeath;
    }

    public boolean isResetOnEmpty() {
        return resetOnEmpty;
    }

    public int getResetDelaySeconds() {
        return resetDelaySeconds;
    }

    public int getEndDelaySeconds() {
        return endDelaySeconds;
    }

    public boolean isAllowRejoin() {
        return allowRejoin;
    }

    public boolean isProtection() {
        return protection;
    }

    public boolean isAllowChestBreak() {
        return allowChestBreak;
    }

    public boolean isBossesAfterWaves() {
        return bossesAfterWaves;
    }

    public void setBossesAfterWaves(boolean bossesAfterWaves) {
        this.bossesAfterWaves = bossesAfterWaves;
    }

    public void setAllowChestBreak(boolean allowChestBreak) {
        this.allowChestBreak = allowChestBreak;
    }

    public String getEntryPermission() {
        return entryPermission;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public CompleteMode getCompleteOn() {
        return completeOn;
    }

    public int getRequiredKills() {
        return requiredKills;
    }

    public BossBarSettings getBossBar() {
        return bossBar;
    }

    public List<String> getCommandsOnStart() {
        return commandsOnStart;
    }

    public List<String> getCommandsOnComplete() {
        return commandsOnComplete;
    }

    public List<String> getCommandsOnFail() {
        return commandsOnFail;
    }

    public List<String> getCommandsOnReset() {
        return commandsOnReset;
    }
}
