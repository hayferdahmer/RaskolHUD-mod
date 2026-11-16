package dev.raskol.hud.client.network;

import dev.raskol.hud.protocol.RaskolHudProtocol.CrosshairSignal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Однобайтовый сигнал для короткой классовой анимации прицела. */
public record CrosshairSignalPayload(CrosshairSignal signal) implements CustomPacketPayload {

    public static final Type<CrosshairSignalPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("raskolhud", "signal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrosshairSignalPayload> CODEC =
            StreamCodec.ofMember(
                    (payload, buf) -> buf.writeByte(payload.signal().wireId()),
                    buf -> new CrosshairSignalPayload(CrosshairSignal.fromWireId(buf.readUnsignedByte())));

    private static volatile CrosshairSignal current = CrosshairSignal.NONE;
    private static volatile long receivedAtNanos;

    public CrosshairSignalPayload {
        if (signal == null || signal == CrosshairSignal.NONE) {
            throw new IllegalArgumentException("A visible crosshair signal is required");
        }
    }

    public static void accept(CrosshairSignal signal) {
        if (signal == null || signal == CrosshairSignal.NONE) return;
        current = signal;
        receivedAtNanos = System.nanoTime();
    }

    public static Pulse current() {
        CrosshairSignal signal = current;
        if (signal == CrosshairSignal.NONE) return Pulse.NONE;
        long elapsed = System.nanoTime() - receivedAtNanos;
        long duration = signal.durationMillis() * 1_000_000L;
        if (elapsed < 0L || elapsed >= duration) {
            current = CrosshairSignal.NONE;
            return Pulse.NONE;
        }
        float progress = 1.0F - (float) elapsed / duration;
        return new Pulse(signal, (float) Math.sin(Math.PI * progress));
    }

    public static void clear() {
        current = CrosshairSignal.NONE;
        receivedAtNanos = 0L;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Pulse(CrosshairSignal signal, float strength) {
        private static final Pulse NONE = new Pulse(CrosshairSignal.NONE, 0.0F);
        public boolean active() {
            return signal != CrosshairSignal.NONE && strength > 0.01F;
        }
    }
}
