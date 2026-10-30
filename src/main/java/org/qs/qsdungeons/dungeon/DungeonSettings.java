package org.qs.qsdungeons.dungeon;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.qs.qsdungeons.storage.ItemSerializer;
import org.qs.qsdungeons.util.ConfigValues;

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

    // Sala de espera: cuenta atras cuando se alcanza min-players.
    private int lobbySeconds = 0;
    // Pausa entre oleadas.
    private int waveDelaySeconds = 5;
    // Escalado por cada jugador extra (0.25 = +25%).
    private double scalingHealth = 0.0D;
    private double scalingDamage = 0.0D;
    private double scalingMobs = 0.0D;
    // Recompensa final para cada jugador al completar.
    private String rewardLootTable = "";
    private double rewardMoney = 0.0D;
    private int rewardExp = 0;
    // Requisitos de entrada.
    private int requiredLevel = 0;
    private double entryCost = 0.0D;
    private ItemStack entryItem;
    private final List<String> requiredDungeons = new ArrayList<>();
    // Musica ambiente en bucle.
    private String ambientSound = "";
    private int ambientInterval = 60;
    private double ambientVolume = 1.0D;
    private double ambientPitch = 1.0D;

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
        this.lobbySeconds = other.lobbySeconds;
        this.waveDelaySeconds = other.waveDelaySeconds;
        this.scalingHealth = other.scalingHealth;
        this.scalingDamage = other.scalingDamage;
        this.scalingMobs = other.scalingMobs;
        this.rewardLootTable = other.rewardLootTable;
        this.rewardMoney = other.rewardMoney;
        this.rewardExp = other.rewardExp;
        this.requiredLevel = other.requiredLevel;
        this.entryCost = other.entryCost;
        this.entryItem = other.entryItem == null ? null : other.entryItem.clone();
        this.requiredDungeons.addAll(other.requiredDungeons);
        this.ambientSound = other.ambientSound;
        this.ambientInterval = other.ambientInterval;
        this.ambientVolume = other.ambientVolume;
        this.ambientPitch = other.ambientPitch;
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
        result.timeLimitSeconds = ConfigValues.parseDuration(section.getString("time-limit-seconds"), result.timeLimitSeconds);
        result.autoEnter = section.getBoolean("auto-enter", result.autoEnter);
        result.autoStart = section.getBoolean("auto-start", result.autoStart);
        result.exitOnLeaveRegion = section.getBoolean("exit-on-leave-region", result.exitOnLeaveRegion);
        result.leaveOnDeath = section.getBoolean("leave-on-death", result.leaveOnDeath);
        result.resetOnEmpty = section.getBoolean("reset-on-empty", result.resetOnEmpty);
        result.resetDelaySeconds = ConfigValues.parseDuration(section.getString("reset-delay-seconds"), result.resetDelaySeconds);
        result.endDelaySeconds = ConfigValues.parseDuration(section.getString("end-delay-seconds"), result.endDelaySeconds);
        result.allowRejoin = section.getBoolean("allow-rejoin", result.allowRejoin);
        result.protection = section.getBoolean("protection", result.protection);
        result.allowChestBreak = section.getBoolean("allow-chest-break", result.allowChestBreak);
        result.bossesAfterWaves = section.getBoolean("bosses-after-waves", result.bossesAfterWaves);
        result.entryPermission = section.getString("entry-permission", result.entryPermission);
        result.cooldownSeconds = ConfigValues.parseDuration(section.getString("cooldown-seconds"), result.cooldownSeconds);
        result.completeOn = CompleteMode.from(section.getString("complete-on"), result.completeOn);
        result.requiredKills = section.getInt("required-kills", result.requiredKills);
        result.bossBar = BossBarSettings.read(section.getConfigurationSection("bossbar"), result.bossBar);
        result.lobbySeconds = ConfigValues.parseDuration(section.getString("lobby-seconds"), result.lobbySeconds);
        result.waveDelaySeconds = ConfigValues.parseDuration(section.getString("wave-delay-seconds"), result.waveDelaySeconds);
        result.scalingHealth = section.getDouble("scaling.health-per-player", result.scalingHealth);
        result.scalingDamage = section.getDouble("scaling.damage-per-player", result.scalingDamage);
        result.scalingMobs = section.getDouble("scaling.mobs-per-player", result.scalingMobs);
        result.rewardLootTable = section.getString("reward.loot-table", result.rewardLootTable);
        result.rewardMoney = section.getDouble("reward.money", result.rewardMoney);
        result.rewardExp = section.getInt("reward.exp", result.rewardExp);
        result.requiredLevel = section.getInt("requirements.level", result.requiredLevel);
        result.entryCost = section.getDouble("requirements.money", result.entryCost);
        if (section.isConfigurationSection("requirements.item")) {
            result.entryItem = ItemSerializer.read(section.getConfigurationSection("requirements.item"));
        }
        if (section.isList("requirements.completed")) {
            result.requiredDungeons.clear();
            result.requiredDungeons.addAll(section.getStringList("requirements.completed"));
        }
        result.ambientSound = section.getString("ambient.sound", result.ambientSound);
        result.ambientInterval = ConfigValues.parseDuration(section.getString("ambient.interval-seconds"), result.ambientInterval);
        result.ambientVolume = section.getDouble("ambient.volume", result.ambientVolume);
        result.ambientPitch = section.getDouble("ambient.pitch", result.ambientPitch);
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
        section.set("lobby-seconds", lobbySeconds);
        section.set("wave-delay-seconds", waveDelaySeconds);
        section.set("scaling.health-per-player", scalingHealth);
        section.set("scaling.damage-per-player", scalingDamage);
        section.set("scaling.mobs-per-player", scalingMobs);
        section.set("reward.loot-table", rewardLootTable);
        section.set("reward.money", rewardMoney);
        section.set("reward.exp", rewardExp);
        section.set("requirements.level", requiredLevel);
        section.set("requirements.money", entryCost);
        section.set("requirements.completed", requiredDungeons);
        if (entryItem != null) {
            ItemSerializer.write(section.createSection("requirements.item"), entryItem);
        }
        section.set("ambient.sound", ambientSound);
        section.set("ambient.interval-seconds", ambientInterval);
        section.set("ambient.volume", ambientVolume);
        section.set("ambient.pitch", ambientPitch);
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
                case "time-limit-seconds" -> timeLimitSeconds = ConfigValues.parseDuration(value, timeLimitSeconds);
                case "auto-enter" -> autoEnter = Boolean.parseBoolean(value);
                case "auto-start" -> autoStart = Boolean.parseBoolean(value);
                case "exit-on-leave-region" -> exitOnLeaveRegion = Boolean.parseBoolean(value);
                case "leave-on-death" -> leaveOnDeath = Boolean.parseBoolean(value);
                case "reset-on-empty" -> resetOnEmpty = Boolean.parseBoolean(value);
                case "reset-delay-seconds" -> resetDelaySeconds = ConfigValues.parseDuration(value, resetDelaySeconds);
                case "end-delay-seconds" -> endDelaySeconds = ConfigValues.parseDuration(value, endDelaySeconds);
                case "allow-rejoin" -> allowRejoin = Boolean.parseBoolean(value);
                case "protection" -> protection = Boolean.parseBoolean(value);
                case "allow-chest-break" -> allowChestBreak = Boolean.parseBoolean(value);
                case "bosses-after-waves" -> bossesAfterWaves = Boolean.parseBoolean(value);
                case "entry-permission" -> entryPermission = value;
                case "cooldown-seconds" -> cooldownSeconds = ConfigValues.parseDuration(value, cooldownSeconds);
                case "complete-on" -> completeOn = CompleteMode.from(value, completeOn);
                case "required-kills" -> requiredKills = Integer.parseInt(value);
                case "bossbar.enabled" -> bossBar.setEnabled(Boolean.parseBoolean(value));
                case "bossbar.title" -> bossBar.setTitle(value);
                case "bossbar.color" -> bossBar.setColor(value);
                case "bossbar.style" -> bossBar.setStyle(value);
                case "bossbar.radius" -> bossBar.setRadius(Double.parseDouble(value));
                case "lobby-seconds" -> lobbySeconds = Math.max(0, ConfigValues.parseDuration(value, lobbySeconds));
                case "wave-delay-seconds" -> waveDelaySeconds = Math.max(0, ConfigValues.parseDuration(value, waveDelaySeconds));
                case "scaling.health-per-player" -> scalingHealth = Math.max(0.0D, parsePercent(value));
                case "scaling.damage-per-player" -> scalingDamage = Math.max(0.0D, parsePercent(value));
                case "scaling.mobs-per-player" -> scalingMobs = Math.max(0.0D, parsePercent(value));
                case "reward.loot-table" -> rewardLootTable = value.equalsIgnoreCase("none") ? "" : value;
                case "reward.money" -> rewardMoney = Math.max(0.0D, Double.parseDouble(value.replace(',', '.')));
                case "reward.exp" -> rewardExp = Math.max(0, Integer.parseInt(value));
                case "requirements.level" -> requiredLevel = Math.max(0, Integer.parseInt(value));
                case "requirements.money" -> entryCost = Math.max(0.0D, Double.parseDouble(value.replace(',', '.')));
                case "requirements.completed" -> {
                    requiredDungeons.clear();
                    for (String part : value.split("[,\\s]+")) {
                        if (!part.isBlank() && !part.equalsIgnoreCase("none")) {
                            requiredDungeons.add(part.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                }
                case "ambient.sound" -> ambientSound = value.equalsIgnoreCase("none") ? "" : value.trim();
                case "ambient.interval-seconds" -> ambientInterval = Math.max(1, ConfigValues.parseDuration(value, ambientInterval));
                case "ambient.volume" -> ambientVolume = Math.max(0.0D, Double.parseDouble(value.replace(',', '.')));
                case "ambient.pitch" -> ambientPitch = Math.max(0.5D, Math.min(2.0D, Double.parseDouble(value.replace(',', '.'))));
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
                "bossbar.title", "bossbar.color", "bossbar.style", "bossbar.radius",
                "lobby-seconds", "wave-delay-seconds", "scaling.health-per-player",
                "scaling.damage-per-player", "scaling.mobs-per-player", "reward.loot-table",
                "reward.money", "reward.exp", "requirements.level", "requirements.money",
                "requirements.completed", "ambient.sound", "ambient.interval-seconds",
                "ambient.volume", "ambient.pitch"
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

    /**
     * Acepta "0.25", "25%" o "25" (se interpreta como porcentaje si es > 5).
     */
    private static double parsePercent(String value) {
        String raw = value.trim().replace(',', '.');
        boolean percent = raw.endsWith("%");
        double number = Double.parseDouble(percent ? raw.substring(0, raw.length() - 1) : raw);
        return percent || number > 5.0D ? number / 100.0D : number;
    }

    public int getLobbySeconds() {
        return lobbySeconds;
    }

    public int getWaveDelaySeconds() {
        return waveDelaySeconds;
    }

    public double getScalingHealth() {
        return scalingHealth;
    }

    public double getScalingDamage() {
        return scalingDamage;
    }

    public double getScalingMobs() {
        return scalingMobs;
    }

    public String getRewardLootTable() {
        return rewardLootTable == null ? "" : rewardLootTable;
    }

    public void setRewardLootTable(String rewardLootTable) {
        this.rewardLootTable = rewardLootTable == null ? "" : rewardLootTable;
    }

    public double getRewardMoney() {
        return rewardMoney;
    }

    public int getRewardExp() {
        return rewardExp;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public double getEntryCost() {
        return entryCost;
    }

    public ItemStack getEntryItem() {
        return entryItem;
    }

    public void setEntryItem(ItemStack entryItem) {
        this.entryItem = entryItem == null || entryItem.getType().isAir() ? null : entryItem.clone();
    }

    public List<String> getRequiredDungeons() {
        return requiredDungeons;
    }

    public String getAmbientSound() {
        return ambientSound == null ? "" : ambientSound;
    }

    public int getAmbientInterval() {
        return ambientInterval;
    }

    public double getAmbientVolume() {
        return ambientVolume;
    }

    public double getAmbientPitch() {
        return ambientPitch;
    }
}
