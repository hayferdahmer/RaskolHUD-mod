package dev.raskol.hud.protocol;

import java.util.Objects;

/**
 * Immutable, server-authored character state displayed by the client HUD.
 *
 * Values are presentation data only. The client must never use this record
 * to calculate combat outcomes or mutate player progression.
 */
public record RaskolHudSnapshot(
        long sampledAtEpochMillis,
        String classId,
        String className,
        String specializationName,
        String resourceName,
        String resourceSymbol,
        int characterLevel,
        int health,
        int healthMax,
        int resource,
        int resourceMax) {

    public RaskolHudSnapshot {
        classId = safe(classId);
        className = safe(className);
        specializationName = safe(specializationName);
        resourceName = safe(resourceName);
        resourceSymbol = safe(resourceSymbol);

        characterLevel = Math.max(0, characterLevel);
        healthMax = Math.max(0, healthMax);
        health = clampKnownMaximum(health, healthMax);
        resourceMax = Math.max(0, resourceMax);
        resource = clampKnownMaximum(resource, resourceMax);
    }

    private static String safe(String value) {
        return Objects.requireNonNullElse(value, "");
    }

    private static int clampKnownMaximum(int value, int maximum) {
        if (maximum <= 0) {
            return Math.max(0, value);
        }
        return Math.max(0, Math.min(value, maximum));
    }
}
