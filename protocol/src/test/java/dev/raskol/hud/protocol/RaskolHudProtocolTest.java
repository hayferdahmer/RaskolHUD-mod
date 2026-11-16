package dev.raskol.hud.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

final class RaskolHudProtocolTest {

    @Test
    void roundTripPreservesServerSnapshot() throws Exception {
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
                120);

        assertEquals(expected, RaskolHudProtocol.decode(RaskolHudProtocol.encode(expected)));
    }

    @Test
    void helloContainsOnlySupportedProtocolVersion() {
        byte[] hello = RaskolHudProtocol.encodeHello();

        assertEquals(1, hello.length);
        assertTrue(RaskolHudProtocol.isValidHello(hello));
        assertFalse(RaskolHudProtocol.isValidHello(new byte[] {2}));
        assertFalse(RaskolHudProtocol.isValidHello(new byte[] {1, 0}));
    }

    @Test
    void decoderRejectsBadSignature() throws Exception {
        byte[] packet = RaskolHudProtocol.encode(new RaskolHudSnapshot(
                1L, "mage", "Маг", "Лёд", "Мана", "✦", 40, 500, 600, 40, 100));
        packet[0] ^= 0x01;

        assertThrows(IOException.class, () -> RaskolHudProtocol.decode(packet));
    }

    @Test
    void decoderRejectsUnsupportedVersion() throws Exception {
        byte[] packet = RaskolHudProtocol.encode(new RaskolHudSnapshot(
                1L, "mage", "Маг", "Лёд", "Мана", "✦", 40, 500, 600, 40, 100));
        packet[4] = 2;

        assertThrows(IOException.class, () -> RaskolHudProtocol.decode(packet));
    }

    @Test
    void constructorClampsValuesToKnownServerMaximums() {
        RaskolHudSnapshot snapshot = new RaskolHudSnapshot(
                1L, "rogue", "Разбойник", "Скрытность", "Энергия", "☠", -2, 999, 100, 999, 120);

        assertEquals(0, snapshot.characterLevel());
        assertEquals(100, snapshot.health());
        assertEquals(120, snapshot.resource());
    }

    @Test
    void constructorPreservesResourceWhenServerMaximumIsUnknown() {
        RaskolHudSnapshot snapshot = new RaskolHudSnapshot(
                1L, "warrior", "Воин", "", "Ярость", "⚔", 20, 100, 100, 68, 0);

        assertEquals(68, snapshot.resource());
        assertEquals(0, snapshot.resourceMax());
    }

    @Test
    void decoderRejectsTruncatedPacket() {
        assertThrows(IOException.class, () -> RaskolHudProtocol.decode(new byte[] {1, 2, 3}));
    }
}
