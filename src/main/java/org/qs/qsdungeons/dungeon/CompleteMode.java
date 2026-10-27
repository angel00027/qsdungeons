package org.qs.qsdungeons.dungeon;

/**
 * Condicion que marca el final de una dungeon.
 */
public enum CompleteMode {
    BOSSES,
    KILLS,
    BOSSES_AND_KILLS,
    MANUAL;

    public static CompleteMode from(String value, CompleteMode fallback) {
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
