package dev.raskol.hud.protocol;

import java.util.List;
import java.util.Objects;

/**
 * Неизменяемый снимок состояния персонажа, подготовленный сервером для HUD.
 *
 * Данные предназначены только для отображения. Клиент не рассчитывает по ним
 * боевые исходы и не изменяет прогрессию или характеристики игрока.
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
        int resourceMax,
        List<RaskolHudEffect> activeEffects,
        List<RaskolHudEffect> damageOverTime) {

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

        activeEffects = safeEffects(activeEffects);
        damageOverTime = safeEffects(damageOverTime);
    }

    private static String safe(String value) {
        return Objects.requireNonNullElse(value, "");
    }

    private static List<RaskolHudEffect> safeEffects(List<RaskolHudEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return List.of();
        }
        return effects.stream()
                .filter(Objects::nonNull)
                .limit(RaskolHudProtocol.MAX_EFFECTS)
                .toList();
    }

    private static int clampKnownMaximum(int value, int maximum) {
        if (maximum <= 0) {
            return Math.max(0, value);
        }
        return Math.max(0, Math.min(value, maximum));
    }
}
