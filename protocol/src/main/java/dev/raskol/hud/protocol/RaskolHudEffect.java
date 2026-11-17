package dev.raskol.hud.protocol;

import java.util.Objects;

/**
 * Серверное состояние одного активного эффекта, предназначенное только для отображения.
 */
public record RaskolHudEffect(
        String id,
        String displayName,
        String symbol,
        int remainingSeconds,
        int durationSeconds,
        int stacks,
        boolean harmful) {

    public RaskolHudEffect {
        id = safe(id);
        displayName = safe(displayName);
        symbol = safe(symbol);
        remainingSeconds = Math.max(-1, remainingSeconds);
        durationSeconds = Math.max(0, durationSeconds);
        stacks = Math.max(1, Math.min(99, stacks));
    }

    private static String safe(String value) {
        return RaskolHudText.limit(value);
    }
}
