package org.qs.qsdungeons.util;

import org.bukkit.NamespacedKey;

/**
 * Claves persistentes usadas para identificar entidades de una dungeon.
 */
public final class Keys {

    public static final NamespacedKey DUNGEON = new NamespacedKey("qsdungeons", "dungeon");
    public static final NamespacedKey SPAWN = new NamespacedKey("qsdungeons", "spawn");
    public static final NamespacedKey BOSS = new NamespacedKey("qsdungeons", "boss");
    public static final NamespacedKey ENTITY = new NamespacedKey("qsdungeons", "entity");
    public static final NamespacedKey TEST = new NamespacedKey("qsdungeons", "test");

    private Keys() {
    }
}
