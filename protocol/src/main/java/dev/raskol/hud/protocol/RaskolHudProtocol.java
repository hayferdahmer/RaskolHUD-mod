package dev.raskol.hud.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Versioned, bounded binary protocol shared by the Paper bridge and Fabric client.
 *
 * Packet layout:
 *   int magic, byte version, long server timestamp,
 *   five VarInt-length UTF-8 strings,
 *   five signed 32-bit integers.
 *
 * The client-to-server hello is exactly one byte: the protocol version.
 */
public final class RaskolHudProtocol {

    public static final String HELLO_CHANNEL = "raskolhud:hello";
    public static final String STATE_CHANNEL = "raskolhud:state";
    public static final int PROTOCOL_VERSION = 1;

    private static final int MAGIC = 0x52534844; // "RSHD"
    private static final int MAX_PACKET_BYTES = 1024;
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

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(160);
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
                    resourceMax);
        } catch (EOFException exception) {
            throw new IOException("Truncated RaskolHUD packet", exception);
        }
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
