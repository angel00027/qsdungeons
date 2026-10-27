package org.qs.qsdungeons.spawn;

/**
 * Origen de una entidad de dungeon.
 */
public enum MobType {
    VANILLA,
    MYTHIC;

    public static MobType from(String value, MobType fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
