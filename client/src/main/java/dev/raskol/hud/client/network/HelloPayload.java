package dev.raskol.hud.client.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HelloPayload(int protocolVersion) implements CustomPacketPayload {

    public static final Type<HelloPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("raskolhud", "hello"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC =
            StreamCodec.ofMember(
                    (payload, buf) -> buf.writeByte(payload.protocolVersion()),
                    buf -> new HelloPayload(buf.readUnsignedByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
