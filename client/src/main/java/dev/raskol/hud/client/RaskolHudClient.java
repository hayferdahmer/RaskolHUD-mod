package dev.raskol.hud.client;

import dev.raskol.hud.client.network.HelloPayload;
import dev.raskol.hud.client.network.HudStateStore;
import dev.raskol.hud.client.network.StatePayload;
import dev.raskol.hud.client.render.RaskolHudRenderer;
import dev.raskol.hud.protocol.RaskolHudProtocol;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;

public final class RaskolHudClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.serverboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(
                StatePayload.TYPE,
                (payload, context) -> HudStateStore.accept(payload.body()));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            HudStateStore.clear();
            ClientPlayNetworking.send(new HelloPayload(RaskolHudProtocol.PROTOCOL_VERSION));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HudStateStore.clear());

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("raskolhud", "main"),
                RaskolHudRenderer::render);
    }
}
