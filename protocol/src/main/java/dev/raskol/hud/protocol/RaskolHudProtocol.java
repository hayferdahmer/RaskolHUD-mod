package dev.raskol.hud.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Ограниченный версионированный бинарный протокол между Paper-мостом и Fabric-клиентом.
 *
 * Пакет состояния: сигнатура, версия, время снимка, данные персонажа и два
 * ограниченных списка боевых состояний. Hello-клиента содержит только версию.
 */
public final class RaskolHudProtocol {

    public static final String HELLO_CHANNEL = "raskolhud:hello";
    public static final String STATE_CHANNEL = "raskolhud:state";
    public static final int PROTOCOL_VERSION = 3;
    public static final int MAX_EFFECTS = 5;
    public static final int MAX_COOLDOWNS = 9;

    private static final int MAGIC = 0x52534844;
    private static final int MAX_PACKET_BYTES = 4096;
    private static final int MAX_STRING_BYTES = 128;

    private RaskolHudProtocol() {}

    public static byte[] encodeHello() {
        return new byte[] {(byte) PROTOCOL_VERSION};
    }

    public static boolean isValidHello(byte[] payload) {
        return payload != null
                && payload.length == 1
                && Byte.toUnsignedInt(payload[0]) == PROTOCOL_VERSION;
    }

    public static byte[] encode(RaskolHudSnapshot snapshot) throws IOException {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeByte(PROTOCOL_VERSION);
            out.writeLong(snapshot.sampledAtEpochMillis());

            writeString(out, snapshot.classId());
            writeString(out, snapshot.className());
            writeString(out, snapshot.specializationName());
            writeString(out, snapshot.resourceName());
            writeString(out, snapshot.resourceSymbol());

            out.writeInt(snapshot.characterLevel());
            out.writeInt(snapshot.health());
            out.writeInt(snapshot.healthMax());
            out.writeInt(snapshot.resource());
            out.writeInt(snapshot.resourceMax());

            writeEffects(out, snapshot.activeEffects());
            writeEffects(out, snapshot.damageOverTime());
            writeEffects(out, snapshot.crowdControl());
            writeCooldowns(out, snapshot.cooldowns());
        }

        byte[] packet = bytes.toByteArray();
        if (packet.length > MAX_PACKET_BYTES) {
            throw new IOException("RaskolHUD packet exceeds the configured size limit");
        }
        return packet;
    }

    public static RaskolHudSnapshot decode(byte[] packet) throws IOException {
        if (packet == null || packet.length < 17 || packet.length > MAX_PACKET_BYTES) {
            throw new IOException("Invalid RaskolHUD packet size");
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Invalid RaskolHUD packet signature");
            }

            int version = in.readUnsignedByte();
            if (version != PROTOCOL_VERSION) {
                throw new IOException("Unsupported RaskolHUD protocol version: " + version);
            }

            long sampledAt = in.readLong();
            String classId = readString(in);
            String className = readString(in);
            String specializationName = readString(in);
            String resourceName = readString(in);
            String resourceSymbol = readString(in);

            int level = in.readInt();
            int health = in.readInt();
            int healthMax = in.readInt();
            int resource = in.readInt();
            int resourceMax = in.readInt();

            List<RaskolHudEffect> activeEffects = readEffects(in);
            List<RaskolHudEffect> damageOverTime = readEffects(in);
            List<RaskolHudEffect> crowdControl = readEffects(in);
            List<RaskolHudCooldown> cooldowns = readCooldowns(in);

            if (in.available() != 0) {
                throw new IOException("Unexpected trailing bytes in RaskolHUD packet");
            }

            return new RaskolHudSnapshot(
                    sampledAt,
                    classId,
                    className,
                    specializationName,
                    resourceName,
                    resourceSymbol,
                    level,
                    health,
                    healthMax,
                    resource,
                    resourceMax,
                    activeEffects,
                    damageOverTime,
                    crowdControl,
                    cooldowns);
        } catch (EOFException exception) {
            throw new IOException("Truncated RaskolHUD packet", exception);
        }
    }

    private static void writeEffects(DataOutputStream out, List<RaskolHudEffect> effects) throws IOException {
        int count = effects == null ? 0 : Math.min(MAX_EFFECTS, effects.size());
        out.writeByte(count);
        for (int i = 0; i < count; i++) {
            RaskolHudEffect effect = effects.get(i);
            writeString(out, effect.id());
            writeString(out, effect.displayName());
            writeString(out, effect.symbol());
            out.writeInt(effect.remainingSeconds());
            out.writeInt(effect.durationSeconds());
            out.writeInt(effect.stacks());
            out.writeBoolean(effect.harmful());
        }
    }

    private static List<RaskolHudEffect> readEffects(DataInputStream in) throws IOException {
        int count = in.readUnsignedByte();
        if (count > MAX_EFFECTS) {
            throw new IOException("Too many RaskolHUD combat effects: " + count);
        }

        List<RaskolHudEffect> effects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = readString(in);
            String displayName = readString(in);
            String symbol = readString(in);
            int remainingSeconds = in.readInt();
            int durationSeconds = in.readInt();
            int stacks = in.readInt();
            boolean harmful = in.readBoolean();

            if (remainingSeconds < -1 || durationSeconds < 0 || stacks < 1 || stacks > 99) {
                throw new IOException("Invalid RaskolHUD combat effect values");
            }

            effects.add(new RaskolHudEffect(
                    id, displayName, symbol, remainingSeconds, durationSeconds, stacks, harmful));
        }
        return List.copyOf(effects);
    }

    private static void writeCooldowns(DataOutputStream out, List<RaskolHudCooldown> cooldowns) throws IOException {
        int count = cooldowns == null ? 0 : Math.min(MAX_COOLDOWNS, cooldowns.size());
        out.writeByte(count);
        for (int i = 0; i < count; i++) {
            RaskolHudCooldown cooldown = cooldowns.get(i);
            out.writeByte(cooldown.slot());
            writeString(out, cooldown.abilityId());
            out.writeInt(cooldown.remainingMillis());
            out.writeInt(cooldown.durationMillis());
        }
    }

    private static List<RaskolHudCooldown> readCooldowns(DataInputStream in) throws IOException {
        int count = in.readUnsignedByte();
        if (count > MAX_COOLDOWNS) {
            throw new IOException("Too many RaskolHUD ability cooldowns: " + count);
        }
        boolean[] occupiedSlots = new boolean[9];
        List<RaskolHudCooldown> cooldowns = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int slot = in.readUnsignedByte();
            String abilityId = readString(in);
            int remainingMillis = in.readInt();
            int durationMillis = in.readInt();
            if (slot > 8 || occupiedSlots[slot] || remainingMillis < 0
                    || durationMillis < remainingMillis || durationMillis > 86_400_000) {
                throw new IOException("Invalid RaskolHUD ability cooldown values");
            }
            occupiedSlots[slot] = true;
            cooldowns.add(new RaskolHudCooldown(slot, abilityId, remainingMillis, durationMillis));
        }
        return List.copyOf(cooldowns);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] encoded = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_STRING_BYTES) {
            throw new IOException("RaskolHUD string exceeds the configured size limit");
        }
        writeVarInt(out, encoded.length);
        out.write(encoded);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > MAX_STRING_BYTES || length > in.available()) {
            throw new IOException("Invalid RaskolHUD string length");
        }
        byte[] encoded = in.readNBytes(length);
        if (encoded.length != length) {
            throw new EOFException("Truncated RaskolHUD string");
        }
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        int remaining = value;
        while ((remaining & 0xFFFFFF80) != 0) {
            out.writeByte((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.writeByte(remaining);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;

        while (position < 35) {
            int current = in.read();
            if (current == -1) {
                throw new EOFException("Truncated VarInt");
            }

            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) {
                return value;
            }
            position += 7;
        }

        throw new IOException("VarInt is too large");
    }
}
