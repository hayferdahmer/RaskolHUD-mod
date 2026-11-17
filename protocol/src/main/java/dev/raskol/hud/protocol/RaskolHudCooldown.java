package dev.raskol.hud.protocol;

/** Снимок перезарядки способности в конкретном слоте хотбара. */
public record RaskolHudCooldown(int slot, String abilityId, int remainingMillis, int durationMillis) {

    public RaskolHudCooldown {
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException("Hotbar slot must be between 0 and 8");
        }
        abilityId = RaskolHudText.limit(abilityId);
        remainingMillis = Math.max(0, Math.min(86_400_000, remainingMillis));
        durationMillis = Math.max(remainingMillis, Math.max(0, Math.min(86_400_000, durationMillis)));
    }
}
