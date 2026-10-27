package org.qs.qsdungeons.util;

/**
 * Lectura de valores numericos simples y rangos tipo "2-5".
 */
public final class ConfigValues {

    private ConfigValues() {
    }

    /**
     * Devuelve [min, max] normalizados. Si el valor no es valido usa los valores por defecto.
     */
    public static int[] range(String raw, int defaultMin, int defaultMax) {
        int min = Math.min(defaultMin, defaultMax);
        int max = Math.max(defaultMin, defaultMax);
        if (raw == null || raw.isBlank()) {
            return new int[]{min, max};
        }
        String value = raw.trim().replace("..", "-").replace(":", "-");
        try {
            if (value.contains("-")) {
                String[] parts = value.split("-", 2);
                int a = Integer.parseInt(parts[0].trim());
                int b = Integer.parseInt(parts[1].trim());
                min = Math.min(a, b);
                max = Math.max(a, b);
            } else {
                int single = Integer.parseInt(value);
                min = single;
                max = single;
            }
        } catch (NumberFormatException ignored) {
            // Se dejan los valores por defecto.
        }
        return new int[]{Math.max(0, min), Math.max(0, max)};
    }

    public static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
