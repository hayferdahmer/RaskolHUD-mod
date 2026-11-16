package dev.raskol.hud.client.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record StatePayload(byte[] body) implements CustomPacketPayload {

    private static final int MAX_BODY_BYTES = 1024;

    public static final Type<StatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("raskolhud", "state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC =
            StreamCodec.ofMember(
                    (buf, payload) -> {
                        byte[] body = payload.body();
                        if (body.length > MAX_BODY_BYTES) {
                            throw new IllegalArgumentException("RaskolHUD payload is too large");
                        }
                        buf.writeBytes(body);
                    },
                    buf -> {
                        int length = buf.readableBytes();
                        if (length < 1 || length > MAX_BODY_BYTES) {
                            throw new IllegalArgumentException("Invalid RaskolHUD payload size: " + length);
                        }
                        byte[] body = new byte[length];
                        buf.readBytes(body);
                        return new StatePayload(body);
                    });

    public StatePayload {
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
