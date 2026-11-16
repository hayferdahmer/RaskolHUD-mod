package dev.raskol.hud.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

final class CrosshairSignalRulesTest {

    @Test
    void activeDotDoesNotPulseAgainWithoutStateChange() {
        Map<String, CrosshairSignalRules.DotState> state = Map.of(
                "wither", new CrosshairSignalRules.DotState(1, 10_000L));

        assertFalse(CrosshairSignalRules.hasNewOrRefreshedDot(state, state, null));
    }

    @Test
    void newAndRefreshedDotsProduceAPulse() {
        Map<String, CrosshairSignalRules.DotState> absent = Map.of();
        Map<String, CrosshairSignalRules.DotState> first = Map.of(
                "wither", new CrosshairSignalRules.DotState(1, 10_000L));
        Map<String, CrosshairSignalRules.DotState> refreshed = Map.of(
                "wither", new CrosshairSignalRules.DotState(2, 20_000L));
        Map<String, CrosshairSignalRules.DotState> refreshedAtStackCap = Map.of(
                "wither", new CrosshairSignalRules.DotState(2, 30_000L));

        assertTrue(CrosshairSignalRules.hasNewOrRefreshedDot(absent, first, null));
        assertTrue(CrosshairSignalRules.hasNewOrRefreshedDot(first, refreshed, null));
        assertTrue(CrosshairSignalRules.hasNewOrRefreshedDot(refreshed, refreshedAtStackCap, null));
    }

    @Test
    void dotFilterRejectsUnrelatedEffects() {
        Map<String, CrosshairSignalRules.DotState> before = Map.of(
                "burning", new CrosshairSignalRules.DotState(1, 10_000L));
        Map<String, CrosshairSignalRules.DotState> after = Map.of(
                "burning", new CrosshairSignalRules.DotState(2, 20_000L));

        assertFalse(CrosshairSignalRules.hasNewOrRefreshedDot(
                before, after, Set.of("chilled")));
        assertFalse(CrosshairSignalRules.hasNewOrRefreshedDot(
                before, after, Set.of()));
    }

    @Test
    void wardRefreshMustExtendTheExpiryRatherThanJustTickDown() {
        Map<String, Long> before = Map.of("potion:resistance:1", 10_000L);

        assertFalse(CrosshairSignalRules.hasNewOrExtendedState(
                before, Map.of("potion:resistance:1", 9_950L), 25L));
        assertTrue(CrosshairSignalRules.hasNewOrExtendedState(
                before, Map.of("potion:resistance:1", 15_000L), 25L));
        assertTrue(CrosshairSignalRules.hasNewOrExtendedState(
                before, Map.of(
                        "potion:absorption:0", 15_000L,
                        "potion:resistance:1", 9_950L), 25L));
    }

    @Test
    void backstabUsesTheConfiguredBoundaryInclusively() {
        assertTrue(CrosshairSignalRules.isWithinBackstabArc(135.0, 135.0));
        assertTrue(CrosshairSignalRules.isWithinBackstabArc(170.0, 135.0));
        assertFalse(CrosshairSignalRules.isWithinBackstabArc(134.999, 135.0));
        assertFalse(CrosshairSignalRules.isWithinBackstabArc(180.1, 135.0));
        assertFalse(CrosshairSignalRules.isWithinBackstabArc(Double.NaN, 135.0));
        assertFalse(CrosshairSignalRules.isWithinBackstabArc(150.0, Double.NaN));
    }
}
