package dev.raskol.hud.protocol;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Чистые правила для серверных импульсов прицела.
 *
 * Эти функции не зависят от Bukkit и Fabric, поэтому их поведение можно
 * проверять обычными модульными тестами.
 */
public final class CrosshairSignalRules {

    public record DotState(int stacks, long expiresAtEpochMillis) {}

    private CrosshairSignalRules() {}

    /**
     * Возвращает true только при появлении или обновлении DoT.
     * Неподвижный уже активный эффект повторным срабатыванием не считается.
     *
     * @param acceptedIds null означает любой id; пустой набор не принимает ни один id
     */
    public static boolean hasNewOrRefreshedDot(
            Map<String, DotState> before,
            Map<String, DotState> after,
            Set<String> acceptedIds) {
        if (after == null || after.isEmpty() || acceptedIds != null && acceptedIds.isEmpty()) {
            return false;
        }

        Map<String, DotState> previous = before == null ? Map.of() : before;
        for (Map.Entry<String, DotState> entry : after.entrySet()) {
            String id = normalize(entry.getKey());
            if (id.isEmpty() || entry.getValue() == null) {
                continue;
            }
            if (acceptedIds != null && !acceptedIds.contains(id)) {
                continue;
            }
            if (!Objects.equals(previous.get(id), entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Определяет, было ли состояние щита/эффекта создано или продлено.
     * Для Bukkit-эффектов передаётся абсолютное время окончания, вычисленное
     * из оставшейся длительности; обычное течение времени не выглядит продлением.
     */
    public static boolean hasNewOrExtendedState(
            Map<String, Long> before,
            Map<String, Long> after,
            long minimumExtensionMillis) {
        if (after == null || after.isEmpty()) {
            return false;
        }

        Map<String, Long> previous = before == null ? Map.of() : before;
        long threshold = Math.max(0L, minimumExtensionMillis);
        for (Map.Entry<String, Long> entry : after.entrySet()) {
            String key = entry.getKey();
            Long expiresAt = entry.getValue();
            if (key == null || key.isBlank() || expiresAt == null) {
                continue;
            }
            Long oldExpiry = previous.get(key);
            if (oldExpiry == null || expiresAt - oldExpiry > threshold) {
                return true;
            }
        }
        return false;
    }

    /** Проверка на серверном пороге угла, который настраивается в RaskolClasses. */
    public static boolean isWithinBackstabArc(double angleDegrees, double thresholdDegrees) {
        return Double.isFinite(angleDegrees)
                && Double.isFinite(thresholdDegrees)
                && angleDegrees >= 0.0
                && angleDegrees <= 180.0
                && thresholdDegrees >= 0.0
                && thresholdDegrees <= 180.0
                && angleDegrees >= thresholdDegrees;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
