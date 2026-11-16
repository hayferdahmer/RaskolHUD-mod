package dev.raskol.hud.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

final class RaskolHudProtocolTest {

    @Test
    void roundTripPreservesCharacterAndCombatSnapshot() throws Exception {
        RaskolHudSnapshot expected = new RaskolHudSnapshot(
                1_800_000_000_000L,
                "warrior",
                "Воин",
                "Оружие",
                "Ярость",
                "⚔",
                58,
                7_450,
                8_200,
                68,
                120,
                List.of(new RaskolHudEffect("shield_wall", "Стальная кожа", "🛡", 4, 5, 1, false)),
                List.of(new RaskolHudEffect("bleed", "Кровотечение", "⚔", 3, 4, 2, true)));

        assertEquals(expected, RaskolHudProtocol.decode(RaskolHudProtocol.encode(expected)));
    }

    @Test
    void helloContainsOnlySupportedProtocolVersion() {
        byte[] hello = RaskolHudProtocol.encodeHello();

        assertEquals(1, hello.length);
        assertEquals(RaskolHudProtocol.PROTOCOL_VERSION, Byte.toUnsignedInt(hello[0]));
        assertTrue(RaskolHudProtocol.isValidHello(hello));
        assertFalse(RaskolHudProtocol.isValidHello(new byte[] {1}));
        assertFalse(RaskolHudProtocol.isValidHello(new byte[] {2, 0}));
    }

    @Test
    void decoderRejectsBadSignature() throws Exception {
        byte[] packet = RaskolHudProtocol.encode(snapshot());
        packet[0] ^= 0x01;

        assertThrows(IOException.class, () -> RaskolHudProtocol.decode(packet));
    }

    @Test
    void decoderRejectsUnsupportedVersion() throws Exception {
        byte[] packet = RaskolHudProtocol.encode(snapshot());
        packet[4] = 1;

        assertThrows(IOException.class, () -> RaskolHudProtocol.decode(packet));
    }

    @Test
    void constructorClampsValuesToKnownServerMaximums() {
        RaskolHudSnapshot snapshot = new RaskolHudSnapshot(
                1L, "rogue", "Разбойник", "Скрытность", "Энергия", "☠",
                -2, 999, 100, 999, 120, List.of(), List.of());

        assertEquals(0, snapshot.characterLevel());
        assertEquals(100, snapshot.health());
        assertEquals(120, snapshot.resource());
    }

    @Test
    void constructorPreservesResourceWhenServerMaximumIsUnknown() {
        RaskolHudSnapshot snapshot = new RaskolHudSnapshot(
                1L, "warrior", "Воин", "", "Ярость", "⚔",
                20, 100, 100, 68, 0, List.of(), List.of());

        assertEquals(68, snapshot.resource());
        assertEquals(0, snapshot.resourceMax());
    }

    @Test
    void constructorLimitsCombatStatusLists() {
        List<RaskolHudEffect> tooMany = java.util.stream.IntStream.range(0, 9)
                .mapToObj(index -> new RaskolHudEffect(
                        "effect_" + index, "Эффект " + index, "✦", 10, 20, 1, false))
                .toList();

        RaskolHudSnapshot snapshot = new RaskolHudSnapshot(
                1L, "mage", "Маг", "", "Мана", "✦",
                30, 100, 100, 20, 100, tooMany, tooMany);

        assertEquals(RaskolHudProtocol.MAX_EFFECTS, snapshot.activeEffects().size());
        assertEquals(RaskolHudProtocol.MAX_EFFECTS, snapshot.damageOverTime().size());
    }

    @Test
    void decoderRejectsTruncatedPacket() {
        assertThrows(IOException.class, () -> RaskolHudProtocol.decode(new byte[] {1, 2, 3}));
    }

    private static RaskolHudSnapshot snapshot() {
        return new RaskolHudSnapshot(
                1L, "mage", "Маг", "Лёд", "Мана", "✦",
                40, 500, 600, 40, 100, List.of(), List.of());
    }
}
