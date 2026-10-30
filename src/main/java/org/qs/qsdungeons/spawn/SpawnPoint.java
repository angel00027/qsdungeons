package org.qs.qsdungeons.spawn;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.qs.qsdungeons.storage.ItemSerializer;
import org.qs.qsdungeons.storage.LocationSerializer;
import org.qs.qsdungeons.util.ConfigValues;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Punto de spawn de mobs de una dungeon.
 */
public final class SpawnPoint {

    private String id;
    private MobType type = MobType.VANILLA;
    private String entity = "ZOMBIE";
    private Location location;
    private int amount = 1;
    private int maxAlive = 1;
    private int respawnSeconds = 0;
    private int spawnIntervalSeconds = 0;
    private double radius = 3.0D;
    private int minPlayerDistance = 0;
    private boolean requireGround = true;
    // Oleada a la que pertenece. Las oleadas se lanzan en orden ascendente.
    private int wave = 1;

    private double health = 0.0D;
    private double damage = 0.0D;
    private double speed = 0.0D;
    private double followRange = 0.0D;

    private String name = "";
    private boolean glowing = false;
    private boolean baby = false;
    private boolean ai = true;
    private boolean persistent = true;
    private boolean clearVanillaDrops = true;

    private String lootTable = "";
    private boolean burnInDaylight = false;
    private final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
    private final List<String> commandsOnSpawn = new ArrayList<>();

    public SpawnPoint(String id) {
        this.id = id;
    }

    public static SpawnPoint read(String id, ConfigurationSection section) {
        SpawnPoint spawn = new SpawnPoint(id);
        if (section == null) {
            return spawn;
        }
        spawn.type = MobType.from(section.getString("type"), MobType.VANILLA);
        spawn.entity = section.getString("entity", spawn.entity);
        spawn.location = LocationSerializer.read(section.getConfigurationSection("location"));
        spawn.amount = Math.max(1, section.getInt("amount", spawn.amount));
        spawn.maxAlive = Math.max(1, section.getInt("max-alive", spawn.amount));
        spawn.respawnSeconds = ConfigValues.parseDuration(section.getString("respawn-seconds"), spawn.respawnSeconds);
        spawn.spawnIntervalSeconds = ConfigValues.parseDuration(section.getString("spawn-interval-seconds"), spawn.spawnIntervalSeconds);
        spawn.radius = Math.max(0.0D, section.getDouble("radius", spawn.radius));
        spawn.minPlayerDistance = Math.max(0, section.getInt("min-player-distance", spawn.minPlayerDistance));
        spawn.requireGround = section.getBoolean("require-ground", spawn.requireGround);
        spawn.wave = Math.max(1, section.getInt("wave", spawn.wave));
        spawn.health = section.getDouble("health", spawn.health);
        spawn.damage = section.getDouble("damage", spawn.damage);
        spawn.speed = section.getDouble("speed", spawn.speed);
        spawn.followRange = section.getDouble("follow-range", spawn.followRange);
        spawn.name = section.getString("name", spawn.name);
        spawn.glowing = section.getBoolean("glowing", spawn.glowing);
        spawn.baby = section.getBoolean("baby", spawn.baby);
        spawn.ai = section.getBoolean("ai", spawn.ai);
        spawn.persistent = section.getBoolean("persistent", spawn.persistent);
        spawn.clearVanillaDrops = section.getBoolean("clear-vanilla-drops", spawn.clearVanillaDrops);
        spawn.lootTable = section.getString("loot-table", spawn.lootTable);
        spawn.burnInDaylight = section.getBoolean("burn-in-daylight", spawn.burnInDaylight);
        spawn.commandsOnSpawn.addAll(section.getStringList("commands-on-spawn"));

        ConfigurationSection equipmentSection = section.getConfigurationSection("equipment");
        if (equipmentSection != null) {
            for (String rawSlot : equipmentSection.getKeys(false)) {
                EquipmentSlot slot = parseSlot(rawSlot);
                ItemStack item = ItemSerializer.read(equipmentSection.getConfigurationSection(rawSlot));
                if (slot != null && item != null) {
                    spawn.equipment.put(slot, item);
                }
            }
        }
        return spawn;
    }

    public void write(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        section.set("type", type.name());
        section.set("entity", entity);
        if (location != null) {
            LocationSerializer.write(section, "location", location);
        }
        section.set("amount", amount);
        section.set("max-alive", maxAlive);
        section.set("respawn-seconds", respawnSeconds);
        section.set("spawn-interval-seconds", spawnIntervalSeconds);
        section.set("radius", radius);
        section.set("min-player-distance", minPlayerDistance);
        section.set("require-ground", requireGround);
        section.set("wave", wave);
        if (health > 0) {
            section.set("health", health);
        }
        if (damage > 0) {
            section.set("damage", damage);
        }
        if (speed > 0) {
            section.set("speed", speed);
        }
        if (followRange > 0) {
            section.set("follow-range", followRange);
        }
        if (name != null && !name.isBlank()) {
            section.set("name", name);
        }
        section.set("glowing", glowing);
        section.set("baby", baby);
        section.set("ai", ai);
        section.set("persistent", persistent);
        section.set("clear-vanilla-drops", clearVanillaDrops);
        if (lootTable != null && !lootTable.isBlank()) {
            section.set("loot-table", lootTable);
        }
        section.set("burn-in-daylight", burnInDaylight);
        if (!commandsOnSpawn.isEmpty()) {
            section.set("commands-on-spawn", commandsOnSpawn);
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
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return EquipmentSlot.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public int getWave() {
        return wave;
    }

    public void setWave(int wave) {
        this.wave = Math.max(1, wave);
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

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(1, amount);
    }

    public int getMaxAlive() {
        return maxAlive;
    }

    public void setMaxAlive(int maxAlive) {
        this.maxAlive = Math.max(1, maxAlive);
    }

    public int getRespawnSeconds() {
        return respawnSeconds;
    }

    public void setRespawnSeconds(int respawnSeconds) {
        this.respawnSeconds = Math.max(0, respawnSeconds);
    }

    public int getSpawnIntervalSeconds() {
        return spawnIntervalSeconds;
    }

    public void setSpawnIntervalSeconds(int spawnIntervalSeconds) {
        this.spawnIntervalSeconds = Math.max(0, spawnIntervalSeconds);
    }

    public int getMinPlayerDistance() {
        return minPlayerDistance;
    }

    public void setMinPlayerDistance(int minPlayerDistance) {
        this.minPlayerDistance = Math.max(0, minPlayerDistance);
    }

    public boolean isRequireGround() {
        return requireGround;
    }

    public void setRequireGround(boolean requireGround) {
        this.requireGround = requireGround;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = Math.max(0.0D, radius);
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Map<EquipmentSlot, ItemStack> getEquipment() {
        return equipment;
    }

    public List<String> getCommandsOnSpawn() {
        return commandsOnSpawn;
    }
}
