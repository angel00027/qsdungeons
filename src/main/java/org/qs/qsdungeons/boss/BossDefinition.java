package org.qs.qsdungeons.boss;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.qs.qsdungeons.dungeon.BossBarSettings;
import org.qs.qsdungeons.spawn.MobType;
import org.qs.qsdungeons.storage.ItemSerializer;
import org.qs.qsdungeons.storage.LocationSerializer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Definicion de un jefe. Se guarda en dungeons/&lt;id&gt;/bosses/&lt;bossId&gt;.yml
 */
public final class BossDefinition {

    private String id;
    private MobType type = MobType.VANILLA;
    private String entity = "ZOMBIE";
    private String displayName = "";
    private Location location;

    private double health = 100.0D;
    private double damage = 0.0D;
    private double speed = 0.0D;
    private double followRange = 0.0D;

    private boolean glowing = true;
    private boolean baby = false;
    private boolean ai = true;
    private boolean persistent = true;
    private boolean clearVanillaDrops = true;
    private boolean announceSpawn = true;
    private boolean announceDeath = true;

    private String lootTable = "";
    private boolean burnInDaylight = false;
    private BossBarSettings bossBar = new BossBarSettings();
    private final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
    private final List<String> commandsOnSpawn = new ArrayList<>();
    private final List<String> commandsOnDeath = new ArrayList<>();

    public BossDefinition(String id) {
        this.id = id;
        this.bossBar.setTitle("<red><bold>{boss}</bold></red> <dark_gray>| <white>{boss_health}<gray>/<white>{boss_max_health}");
        this.bossBar.setColor("RED");
    }

    public static BossDefinition read(String id, ConfigurationSection section) {
        BossDefinition boss = new BossDefinition(id);
        if (section == null) {
            return boss;
        }
        boss.type = MobType.from(section.getString("type"), MobType.VANILLA);
        boss.entity = section.getString("entity", boss.entity);
        boss.displayName = section.getString("display-name", "");
        boss.location = LocationSerializer.read(section.getConfigurationSection("location"));
        boss.health = section.getDouble("health", boss.health);
        boss.damage = section.getDouble("damage", boss.damage);
        boss.speed = section.getDouble("speed", boss.speed);
        boss.followRange = section.getDouble("follow-range", boss.followRange);
        boss.glowing = section.getBoolean("glowing", boss.glowing);
        boss.baby = section.getBoolean("baby", boss.baby);
        boss.ai = section.getBoolean("ai", boss.ai);
        boss.persistent = section.getBoolean("persistent", boss.persistent);
        boss.clearVanillaDrops = section.getBoolean("clear-vanilla-drops", boss.clearVanillaDrops);
        boss.announceSpawn = section.getBoolean("announce-spawn", boss.announceSpawn);
        boss.announceDeath = section.getBoolean("announce-death", boss.announceDeath);
        boss.lootTable = section.getString("loot-table", "");
        boss.burnInDaylight = section.getBoolean("burn-in-daylight", boss.burnInDaylight);
        boss.bossBar = BossBarSettings.read(section.getConfigurationSection("bossbar"), boss.bossBar);
        boss.commandsOnSpawn.addAll(section.getStringList("commands-on-spawn"));
        boss.commandsOnDeath.addAll(section.getStringList("commands-on-death"));

        ConfigurationSection equipmentSection = section.getConfigurationSection("equipment");
        if (equipmentSection != null) {
            for (String rawSlot : equipmentSection.getKeys(false)) {
                EquipmentSlot slot = parseSlot(rawSlot);
                ItemStack item = ItemSerializer.read(equipmentSection.getConfigurationSection(rawSlot));
                if (slot != null && item != null) {
                    boss.equipment.put(slot, item);
                }
            }
        }
        return boss;
    }

    public void write(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        section.set("id", id);
        section.set("type", type.name());
        section.set("entity", entity);
        if (displayName != null && !displayName.isBlank()) {
            section.set("display-name", displayName);
        }
        if (location != null) {
            LocationSerializer.write(section, "location", location);
        }
        section.set("health", health);
        if (damage > 0) {
            section.set("damage", damage);
        }
        if (speed > 0) {
            section.set("speed", speed);
        }
        if (followRange > 0) {
            section.set("follow-range", followRange);
        }
        section.set("glowing", glowing);
        section.set("baby", baby);
        section.set("ai", ai);
        section.set("persistent", persistent);
        section.set("clear-vanilla-drops", clearVanillaDrops);
        section.set("announce-spawn", announceSpawn);
        section.set("announce-death", announceDeath);
        if (lootTable != null && !lootTable.isBlank()) {
            section.set("loot-table", lootTable);
        }
        section.set("burn-in-daylight", burnInDaylight);
        bossBar.write(section.createSection("bossbar"));
        if (!commandsOnSpawn.isEmpty()) {
            section.set("commands-on-spawn", commandsOnSpawn);
        }
        if (!commandsOnDeath.isEmpty()) {
            section.set("commands-on-death", commandsOnDeath);
        }
        if (!equipment.isEmpty()) {
            ConfigurationSection equipmentSection = section.createSection("equipment");
            for (Map.Entry<EquipmentSlot, ItemStack> entry : equipment.entrySet()) {
                ItemSerializer.write(equipmentSection.createSection(entry.getKey().name()
                        .toLowerCase(Locale.ROOT).replace('_', '-')), entry.getValue());
            }
        }
    }

    private static EquipmentSlot parseSlot(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return EquipmentSlot.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public MobType getType() {
        return type;
    }

    public void setType(MobType type) {
        this.type = type;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public double getHealth() {
        return health;
    }

    public void setHealth(double health) {
        this.health = health;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getFollowRange() {
        return followRange;
    }

    public void setFollowRange(double followRange) {
        this.followRange = followRange;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
    }

    public boolean isBaby() {
        return baby;
    }

    public void setBaby(boolean baby) {
        this.baby = baby;
    }

    public boolean isAi() {
        return ai;
    }

    public void setAi(boolean ai) {
        this.ai = ai;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean isClearVanillaDrops() {
        return clearVanillaDrops;
    }

    public void setClearVanillaDrops(boolean clearVanillaDrops) {
        this.clearVanillaDrops = clearVanillaDrops;
    }

    public boolean isAnnounceSpawn() {
        return announceSpawn;
    }

    public void setAnnounceSpawn(boolean announceSpawn) {
        this.announceSpawn = announceSpawn;
    }

    public boolean isAnnounceDeath() {
        return announceDeath;
    }

    public void setAnnounceDeath(boolean announceDeath) {
        this.announceDeath = announceDeath;
    }

    public String getLootTable() {
        return lootTable;
    }

    public void setLootTable(String lootTable) {
        this.lootTable = lootTable;
    }

    public boolean isBurnInDaylight() {
        return burnInDaylight;
    }

    public void setBurnInDaylight(boolean burnInDaylight) {
        this.burnInDaylight = burnInDaylight;
    }

    public BossBarSettings getBossBar() {
        return bossBar;
    }

    public Map<EquipmentSlot, ItemStack> getEquipment() {
        return equipment;
    }

    public List<String> getCommandsOnSpawn() {
        return commandsOnSpawn;
    }

    public List<String> getCommandsOnDeath() {
        return commandsOnDeath;
    }
}
