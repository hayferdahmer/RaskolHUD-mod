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
        String factionName,
        String titleName,
        int characterLevel,
        int health,
        int healthMax,
        int resource,
        int resourceMax,
        List<RaskolHudEffect> activeEffects,
        List<RaskolHudEffect> damageOverTime,
        List<RaskolHudEffect> crowdControl,
        List<RaskolHudCooldown> cooldowns,
        RaskolHudCharacterStats characterStats,
        RaskolHudTarget target) {

    public RaskolHudSnapshot {
        classId = safe(classId);
        className = safe(className);
        specializationName = safe(specializationName);
        resourceName = safe(resourceName);
        resourceSymbol = safe(resourceSymbol);
        factionName = safe(factionName);
        titleName = safe(titleName);

        characterLevel = Math.max(0, Math.min(60, characterLevel));
        healthMax = Math.max(0, healthMax);
        health = clampKnownMaximum(health, healthMax);
        resourceMax = Math.max(0, resourceMax);
        resource = clampKnownMaximum(resource, resourceMax);

        activeEffects = safeEffects(activeEffects);
        damageOverTime = safeEffects(damageOverTime);
        crowdControl = safeEffects(crowdControl);
        cooldowns = safeCooldowns(cooldowns);
        characterStats = characterStats == null ? RaskolHudCharacterStats.UNAVAILABLE : characterStats;
        target = target == null ? RaskolHudTarget.UNAVAILABLE : target;
    }

    public RaskolHudSnapshot(
            long sampledAtEpochMillis,
            String classId,
            String className,
            String specializationName,
            String resourceName,
            String resourceSymbol,
            String factionName,
            String titleName,
            int characterLevel,
            int health,
            int healthMax,
            int resource,
            int resourceMax,
            List<RaskolHudEffect> activeEffects,
            List<RaskolHudEffect> damageOverTime,
            List<RaskolHudEffect> crowdControl,
            List<RaskolHudCooldown> cooldowns) {
        this(sampledAtEpochMillis, classId, className, specializationName, resourceName, resourceSymbol,
                factionName, titleName, characterLevel, health, healthMax, resource, resourceMax,
                activeEffects, damageOverTime, crowdControl, cooldowns,
                RaskolHudCharacterStats.UNAVAILABLE, RaskolHudTarget.UNAVAILABLE);
    }

    public RaskolHudSnapshot(
            long sampledAtEpochMillis,
            String classId,
            String className,
            String specializationName,
            String resourceName,
            String resourceSymbol,
            String factionName,
            String titleName,
            int characterLevel,
            int health,
            int healthMax,
            int resource,
            int resourceMax,
            List<RaskolHudEffect> activeEffects,
            List<RaskolHudEffect> damageOverTime,
            List<RaskolHudEffect> crowdControl,
            List<RaskolHudCooldown> cooldowns,
            RaskolHudCharacterStats characterStats) {
        this(sampledAtEpochMillis, classId, className, specializationName, resourceName, resourceSymbol,
                factionName, titleName, characterLevel, health, healthMax, resource, resourceMax,
                activeEffects, damageOverTime, crowdControl, cooldowns, characterStats, RaskolHudTarget.UNAVAILABLE);
    }

    private static String safe(String value) {
        return RaskolHudText.limit(value);
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

    private static List<RaskolHudCooldown> safeCooldowns(List<RaskolHudCooldown> cooldowns) {
        if (cooldowns == null || cooldowns.isEmpty()) {
            return List.of();
        }
        return cooldowns.stream()
                .filter(Objects::nonNull)
                .limit(RaskolHudProtocol.MAX_COOLDOWNS)
                .toList();
    }

    private static int clampKnownMaximum(int value, int maximum) {
        if (maximum <= 0) {
            return Math.max(0, value);
        }
        return Math.max(0, Math.min(value, maximum));
    }
}
