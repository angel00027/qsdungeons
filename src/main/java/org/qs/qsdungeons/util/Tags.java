package org.qs.qsdungeons.util;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Pequeno envoltorio para leer y escribir PersistentDataContainer.
 */
public final class Tags {

    private Tags() {
    }

    public static void set(Entity entity, org.bukkit.NamespacedKey key, String value) {
        if (entity == null || value == null) {
            return;
        }
        entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
    }

    public static String get(Entity entity, org.bukkit.NamespacedKey key) {
        if (entity == null) {
            return null;
        }
        return entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static boolean has(Entity entity, org.bukkit.NamespacedKey key) {
        return entity != null && entity.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    public static void remove(Entity entity, org.bukkit.NamespacedKey key) {
        if (entity != null) {
            entity.getPersistentDataContainer().remove(key);
        }
    }

    public static boolean hasTag(Entity entity, org.bukkit.NamespacedKey key) {
        PersistentDataContainer container = entity.getPersistentDataContainer();
        return container.has(key, PersistentDataType.STRING);
    }

    public static boolean isDungeonEntity(Entity entity) {
        return hasTag(entity, org.qs.qsdungeons.util.Keys.DUNGEON);
    }

    public static boolean isTestEntity(Entity entity) {
        return hasTag(entity, org.qs.qsdungeons.util.Keys.TEST);
    }

    public static String dungeonOf(Entity entity) {
        return get(entity, org.qs.qsdungeons.util.Keys.DUNGEON);
    }

    public static String spawnOf(Entity entity) {
        return get(entity, org.qs.qsdungeons.util.Keys.SPAWN);
    }

    public static String bossOf(Entity entity) {
        return get(entity, org.qs.qsdungeons.util.Keys.BOSS);
    }

    /**
     * Utilidad para depuracion rapida sin arrastrar dependencias de Bukkit Block.
     */
    public static String describe(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + "," + block.getY() + "," + block.getZ();
    }
}
