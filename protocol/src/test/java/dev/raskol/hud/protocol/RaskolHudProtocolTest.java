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
                "Рассвет",
                "Заступник",
                58,
                7_450,
                8_200,
                68,
                120,
                List.of(new RaskolHudEffect("shield_wall", "Стальная кожа", "🛡", 4, 5, 1, false)),
                List.of(new RaskolHudEffect("bleed", "Кровотечение", "⚔", 3, 4, 2, true)),
                List.of(new RaskolHudEffect("STUN", "Оцепенение", "✣", 2, 3, 1, true)),
                List.of(new RaskolHudCooldown(2, "shield_bash", 1500, 8000)),
                new RaskolHudCharacterStats(true, 12.5, 8.0, 6.5, 4.0, 18.5, 22.0, 2, 8, 10),
                new RaskolHudTarget(true, true, "Игрок", "Игрок", "Воин", 42, 750, 1000));

        assertEquals(expected, RaskolHudProtocol.decode(RaskolHudProtocol.encode(expected)));
    }

    @Test
    void characterStatsAreClampedAndRoundTripWithoutPrecisionLoss() throws Exception {
        RaskolHudCharacterStats stats = new RaskolHudCharacterStats(
                true, 12.5, 8.0, 6.5, 4.0, 18.5, 22.0, 2, 8, 10);
        RaskolHudSnapshot value = new RaskolHudSnapshot(
                1L, "mage", "Маг", "Лёд", "Мана", "✦",
                "", "", 40, 500, 600, 40, 100, List.of(), List.of(), List.of(), List.of(), stats);

        assertEquals(stats, RaskolHudProtocol.decode(RaskolHudProtocol.encode(value)).characterStats());
        assertEquals(60.0, new RaskolHudCharacterStats(true, 90, 90, 150, -1, 200, 200, -1, 2, 3)
                .physicalResistancePct());
    }

    @Test
    void targetSnapshotRoundTripsAndRejectsInvalidHealth() throws Exception {
        RaskolHudTarget target = new RaskolHudTarget(true, false, "Скелет", "SKELETON", "", 18, 12.5, 20);
        RaskolHudSnapshot value = new RaskolHudSnapshot(
                1L, "hunter", "Охотник", "Следопыт", "Концентрация", "➶",
                "", "", 40, 500, 600, 40, 100, List.of(), List.of(), List.of(), List.of(),
                RaskolHudCharacterStats.UNAVAILABLE, target);

        assertEquals(target, RaskolHudProtocol.decode(RaskolHudProtocol.encode(value)).target());
        assertEquals(RaskolHudTarget.UNAVAILABLE,
                RaskolHudProtocol.decode(RaskolHudProtocol.encode(snapshot())).target());
        assertEquals(20.0, new RaskolHudTarget(true, false, "Скелет", "SKELETON", "", 18, 30, 20)
                .health());
        assertEquals(0.0, new RaskolHudTarget(true, false, "Скелет", "SKELETON", "", 18, 10, 0)
                .health());
        assertFalse(new RaskolHudTarget(true, false, "", "SKELETON", "", 18, 10, 20).available());
    }

    @Test
    void protocolTextIsLimitedByUtf8BytesWithoutSplittingUnicode() {
        String limited = RaskolHudText.limit("я".repeat(100));
        assertEquals(128, limited.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertEquals(64, limited.codePointCount(0, limited.length()));
        String emojiLimited = RaskolHudText.limit("😀".repeat(100));
        assertEquals(128, emojiLimited.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertEquals(32, emojiLimited.codePointCount(0, emojiLimited.length()));
    }

    @Test
    void crosshairSignalIdsAreStableAndUnknownIdsAreRejected() {
        assertEquals(1, RaskolHudProtocol.CrosshairSignal.WARLOCK_CURSE.wireId());
        assertEquals(RaskolHudProtocol.CrosshairSignal.MAGE_FROST,
                RaskolHudProtocol.CrosshairSignal.fromWireId(6));
        assertThrows(IllegalArgumentException.class,
                () -> RaskolHudProtocol.CrosshairSignal.fromWireId(99));
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
                "", "", -2, 999, 100, 999, 120, List.of(), List.of(), List.of(), List.of());

        assertEquals(0, snapshot.characterLevel());
        assertEquals(100, snapshot.health());
        assertEquals(120, snapshot.resource());
    }

    @Test
    void constructorPreservesResourceWhenServerMaximumIsUnknown() {
        RaskolHudSnapshot snapshot = new RaskolHudSnapshot(
                1L, "warrior", "Воин", "", "Ярость", "⚔",
                "", "", 20, 100, 100, 68, 0, List.of(), List.of(), List.of(), List.of());

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
                "", "", 30, 100, 100, 20, 100, tooMany, tooMany, tooMany, List.of());

        assertEquals(RaskolHudProtocol.MAX_EFFECTS, snapshot.activeEffects().size());
        assertEquals(RaskolHudProtocol.MAX_EFFECTS, snapshot.damageOverTime().size());
        assertEquals(RaskolHudProtocol.MAX_EFFECTS, snapshot.crowdControl().size());
    }

    @Test
    void cooldownSnapshotKeepsTheOriginalHotbarSlotAndTimers() throws Exception {
        RaskolHudSnapshot value = new RaskolHudSnapshot(
                1L, "rogue", "Разбойник", "", "Энергия", "☠",
                "", "", 30, 100, 100, 20, 100, List.of(), List.of(), List.of(),
                List.of(new RaskolHudCooldown(7, "kick", 1250, 5000)));
        assertEquals(value, RaskolHudProtocol.decode(RaskolHudProtocol.encode(value)));
    }

    @Test
    void decoderRejectsTrailingBytesAndOversizedPackets() throws Exception {
        byte[] valid = RaskolHudProtocol.encode(snapshot());
        byte[] withTrailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IOException.class, () -> RaskolHudProtocol.decode(withTrailing));
        assertThrows(IOException.class, () -> RaskolHudProtocol.decode(new byte[4097]));
    }

    @Test
    void modelStringsAreBoundedBeforeEncoding() throws Exception {
        String longName = "я".repeat(100);
        RaskolHudSnapshot value = new RaskolHudSnapshot(
                1L, "mage", longName, "Лёд", "Мана", "✦",
                "", "", 40, 500, 600, 40, 100, List.of(), List.of(), List.of(), List.of());
        RaskolHudSnapshot decoded = RaskolHudProtocol.decode(RaskolHudProtocol.encode(value));
        assertEquals(128, decoded.className().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    @Test
    void decoderRejectsMalformedUtf8() throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {
            out.writeInt(0x52534844);
            out.writeByte(RaskolHudProtocol.PROTOCOL_VERSION);
            out.writeLong(1L);
            out.writeByte(1);
            out.writeByte(0xFF);
            for (int index = 0; index < 6; index++) out.writeByte(0);
            for (int index = 0; index < 5; index++) out.writeInt(0);
            for (int index = 0; index < 4; index++) out.writeByte(0);
            out.writeBoolean(false);
            for (int index = 0; index < 6; index++) out.writeDouble(0.0D);
            for (int index = 0; index < 3; index++) out.writeInt(0);
            out.writeBoolean(false);
        }
        assertThrows(IOException.class, () -> RaskolHudProtocol.decode(bytes.toByteArray()));
    }

    @Test
    void protocolModelsClampProgressionAndCombatTimerBounds() {
        RaskolHudSnapshot snapshot = new RaskolHudSnapshot(
                1L, "mage", "Маг", "", "Мана", "✦",
                "", "", 100, 100, 100, 100, 100, List.of(), List.of(), List.of(), List.of());
        assertEquals(60, snapshot.characterLevel());
        assertEquals(86_400, new RaskolHudEffect("long", "Долгий", "✦", 100_000, 100_000, 1, false)
                .remainingSeconds());
        assertEquals(1_000_000D,
                new RaskolHudTarget(true, false, "Босс", "ZOMBIE", "", 60, 2_000_000D, 2_000_000D)
                        .healthMax());
    }

    @Test
    void decoderRejectsTruncatedPacket() {
        assertThrows(IOException.class, () -> RaskolHudProtocol.decode(new byte[] {1, 2, 3}));
    }

    private static RaskolHudSnapshot snapshot() {
        return new RaskolHudSnapshot(
                1L, "mage", "Маг", "Лёд", "Мана", "✦",
                "", "", 40, 500, 600, 40, 100, List.of(), List.of(), List.of(), List.of());
    }
}
