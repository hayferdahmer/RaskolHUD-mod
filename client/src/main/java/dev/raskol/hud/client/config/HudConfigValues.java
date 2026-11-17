package dev.raskol.hud.client.config;

/** Чистые функции чтения и нормализации пользовательских настроек. */
public final class HudConfigValues {
    private HudConfigValues() {}

    public static boolean readBoolean(String value, boolean fallback) {
        if (value == null) return fallback;
        if ("true".equalsIgnoreCase(value.trim())) return true;
        if ("false".equalsIgnoreCase(value.trim())) return false;
        return fallback;
    }

    public static int readOffset(String value, int fallback) {
        try {
            int parsed = value == null ? fallback : Integer.parseInt(value.trim());
            return Math.max(-500, Math.min(500, parsed));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static int readOpacity(String value) {
        try {
            int parsed = value == null ? 100 : Integer.parseInt(value.trim());
            return Math.max(20, Math.min(100, parsed - parsed % 20));
        } catch (NumberFormatException ignored) {
            return 100;
        }
    }

    public static double readScale(String value, double fallback) {
        double parsed = fallback;
        if (value != null) {
            try {
                parsed = Double.parseDouble(value.trim());
            } catch (NumberFormatException ignored) {
                parsed = fallback;
            }
        }
        if (!Double.isFinite(parsed)) {
            parsed = fallback;
        }
        double bounded = Math.max(0.75D, Math.min(1.50D, parsed));
        return 0.75D + Math.round((bounded - 0.75D) / 0.25D) * 0.25D;
    }

    public static int readNonNegativeInt(String value, int fallback) {
        try {
            return Math.max(0, value == null ? fallback : Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
