package dev.raskol.hud.protocol;

/**
 * Подтверждённые сервером дополнительные характеристики персонажа.
 * Проценты передаются в процентных пунктах, а не долями.
 */
public record RaskolHudCharacterStats(
        boolean available,
        double physicalResistancePct,
        double magicResistancePct,
        double dodgePct,
        double parryPct,
        double meleeCritPct,
        double spellCritPct,
        int availableTalentPoints,
        int spentTalentPoints,
        int earnedTalentPoints) {

    public static final RaskolHudCharacterStats UNAVAILABLE =
            new RaskolHudCharacterStats(false, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public RaskolHudCharacterStats {
        physicalResistancePct = clamp(physicalResistancePct, 0, 60);
        magicResistancePct = clamp(magicResistancePct, 0, 60);
        dodgePct = clamp(dodgePct, 0, 100);
        parryPct = clamp(parryPct, 0, 100);
        meleeCritPct = clamp(meleeCritPct, 0, 100);
        spellCritPct = clamp(spellCritPct, 0, 100);
        availableTalentPoints = Math.max(0, availableTalentPoints);
        spentTalentPoints = Math.max(0, spentTalentPoints);
        earnedTalentPoints = Math.max(0, earnedTalentPoints);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
