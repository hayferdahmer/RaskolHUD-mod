package dev.raskol.hud.protocol;

/** Серверный снимок существа, на которое сейчас смотрит игрок. */
public record RaskolHudTarget(
        boolean available,
        boolean player,
        String name,
        String type,
        String className,
        int level,
        double health,
        double healthMax) {

    public static final RaskolHudTarget UNAVAILABLE =
            new RaskolHudTarget(false, false, "", "", "", 0, 0, 0);

    public RaskolHudTarget {
        name = safe(name);
        type = safe(type);
        className = safe(className);
        level = Math.max(0, Math.min(60, level));
        healthMax = finiteNonNegative(healthMax);
        health = Math.max(0, Math.min(healthMax > 0 ? healthMax : Double.MAX_VALUE,
                finiteNonNegative(health)));
        if (!available) {
            player = false;
            name = "";
            type = "";
            className = "";
            level = 0;
            health = 0;
            healthMax = 0;
        }
    }

    private static String safe(String value) {
        return RaskolHudText.limit(value);
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }
}
