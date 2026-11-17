package dev.raskol.hud.client.render;

import java.util.HashMap;
import java.util.Map;

/** Небольшие визуальные переходы; игровые значения всегда остаются серверными. */
public final class RaskolHudMotion {

    public record BarFrame(double fill, double damageTrail, double damageAlpha, double pulse) {}

    private static final long DAMAGE_TRAIL_MILLIS = 720L;
    private static final long PULSE_MILLIS = 280L;
    private static final long SLOT_PULSE_MILLIS = 260L;
    private static final Map<String, BarState> BARS = new HashMap<>();
    private static int selectedSlot = -1;
    private static long selectedSlotChangedAt;

    private RaskolHudMotion() {}

    public static synchronized BarFrame sampleBar(String key, int current, int maximum) {
        long now = System.currentTimeMillis();
        double target = maximum <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) current / maximum));
        BarState state = BARS.computeIfAbsent(key, ignored -> new BarState(target, now));
        if (target < state.target - 0.0001) {
            state.damageTrail = Math.max(state.displayed, state.target);
            state.damageAt = now;
        } else if (target > state.target + 0.0001) {
            state.pulseUntil = now + PULSE_MILLIS;
        }
        state.target = target;
        long elapsed = Math.max(0L, Math.min(250L, now - state.lastUpdate));
        state.lastUpdate = now;
        double easing = 1.0 - Math.exp(-elapsed / 115.0);
        state.displayed += (target - state.displayed) * easing;
        if (Math.abs(target - state.displayed) < 0.001) {
            state.displayed = target;
        }
        double damageAlpha = state.damageAt == 0L ? 0.0
                : Math.max(0.0, 1.0 - (double) (now - state.damageAt) / DAMAGE_TRAIL_MILLIS);
        double pulse = Math.max(0.0, Math.min(1.0, (double) (state.pulseUntil - now) / PULSE_MILLIS));
        return new BarFrame(state.displayed, state.damageTrail, damageAlpha, pulse);
    }

    public static synchronized void reset() {
        BARS.clear();
        selectedSlot = -1;
        selectedSlotChangedAt = 0L;
    }

    public static synchronized float selectedSlotPulse(int currentSlot) {
        long now = System.currentTimeMillis();
        if (selectedSlot != currentSlot) {
            selectedSlot = currentSlot;
            selectedSlotChangedAt = now;
        }
        return (float) Math.max(0.0, 1.0 - (double) (now - selectedSlotChangedAt) / SLOT_PULSE_MILLIS);
    }

    private static final class BarState {
        private double displayed;
        private double target;
        private double damageTrail;
        private long damageAt;
        private long pulseUntil;
        private long lastUpdate;

        private BarState(double initial, long now) {
            displayed = initial;
            target = initial;
            damageTrail = initial;
            lastUpdate = now;
        }
    }
}
