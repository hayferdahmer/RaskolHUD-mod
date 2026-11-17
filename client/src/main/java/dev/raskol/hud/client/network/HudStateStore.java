package dev.raskol.hud.client.network;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.raskol.hud.protocol.RaskolHudProtocol;
import dev.raskol.hud.protocol.RaskolHudSnapshot;

public final class HudStateStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("RaskolHUD");
    private static final long STALE_AFTER_MILLIS = 5_000L;

    private static volatile RaskolHudSnapshot current;
    private static volatile long lastReceivedAtMillis;
    private static volatile long lastSampledAtEpochMillis;
    private static volatile boolean warnedInvalidPacket;

    private HudStateStore() {}

    public static void accept(byte[] packet) {
        try {
            RaskolHudSnapshot decoded = RaskolHudProtocol.decode(packet);
            if (decoded.sampledAtEpochMillis() <= lastSampledAtEpochMillis) {
                return;
            }
            current = decoded;
            lastSampledAtEpochMillis = decoded.sampledAtEpochMillis();
            lastReceivedAtMillis = System.currentTimeMillis();
            warnedInvalidPacket = false;
        } catch (IOException | IllegalArgumentException exception) {
            if (!warnedInvalidPacket) {
                LOGGER.warn("Rejected invalid RaskolHUD server snapshot: {}", exception.getMessage());
                warnedInvalidPacket = true;
            }
        }
    }

    public static RaskolHudSnapshot current() {
        RaskolHudSnapshot snapshot = current;
        if (snapshot == null || System.currentTimeMillis() - lastReceivedAtMillis > STALE_AFTER_MILLIS) {
            return null;
        }
        return snapshot;
    }

    public static void clear() {
        current = null;
        lastReceivedAtMillis = 0L;
        lastSampledAtEpochMillis = 0L;
        warnedInvalidPacket = false;
    }
}
