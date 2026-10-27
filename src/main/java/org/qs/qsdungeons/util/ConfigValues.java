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

    /** Acepta segundos simples o formatos como 1s, 5m, 2h, 1d y 1mes. */
    public static int parseDuration(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
        try {
            if (value.matches("\\d+")) {
                return Math.max(0, Integer.parseInt(value));
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(\\d+)(mes(?:es)?|mo|d|h|m|s)").matcher(value);
            int total = 0;
            int end = 0;
            while (matcher.find()) {
                if (matcher.start() != end) {
                    return fallback;
                }
                long amount = Long.parseLong(matcher.group(1));
                long multiplier = switch (matcher.group(2)) {
                    case "s" -> 1L;
                    case "m" -> 60L;
                    case "h" -> 3600L;
                    case "d" -> 86400L;
                    default -> 2592000L;
                };
                long next = (long) total + amount * multiplier;
                if (next > Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
                total = (int) next;
                end = matcher.end();
            }
            return end == value.length() && end > 0 ? total : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
