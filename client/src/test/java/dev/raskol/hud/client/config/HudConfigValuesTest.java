package dev.raskol.hud.client.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class HudConfigValuesTest {
    @Test
    void booleanValuesUseFallbackForMalformedInput() {
        assertTrue(HudConfigValues.readBoolean("true", false));
        assertFalse(HudConfigValues.readBoolean("false", true));
        assertTrue(HudConfigValues.readBoolean("not-a-boolean", true));
        assertFalse(HudConfigValues.readBoolean(null, false));
    }

    @Test
    void offsetsAreBoundedAndMalformedValuesFallBack() {
        assertEquals(500, HudConfigValues.readOffset("900", 0));
        assertEquals(-500, HudConfigValues.readOffset("-900", 0));
        assertEquals(12, HudConfigValues.readOffset("broken", 12));
    }

    @Test
    void opacityUsesSupportedTwentyPercentSteps() {
        assertEquals(100, HudConfigValues.readOpacity(null));
        assertEquals(80, HudConfigValues.readOpacity("99"));
        assertEquals(20, HudConfigValues.readOpacity("0"));
        assertEquals(100, HudConfigValues.readOpacity("invalid"));
    }

    @Test
    void scaleIsBoundedAndSnappedToSupportedSteps() {
        assertEquals(0.75D, HudConfigValues.readScale("0.1", 1.0D));
        assertEquals(1.25D, HudConfigValues.readScale("1.24", 1.0D));
        assertEquals(1.50D, HudConfigValues.readScale("2.0", 1.0D));
        assertEquals(1.0D, HudConfigValues.readScale("NaN", 1.0D));
    }

    @Test
    void integerValuesNeverBecomeNegative() {
        assertEquals(0, HudConfigValues.readNonNegativeInt("-4", 3));
        assertEquals(7, HudConfigValues.readNonNegativeInt("broken", 7));
    }
}
