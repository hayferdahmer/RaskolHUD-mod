package dev.raskol.hud.client;

import dev.raskol.hud.client.network.HelloPayload;
import dev.raskol.hud.client.network.HudStateStore;
import dev.raskol.hud.client.network.StatePayload;
import dev.raskol.hud.client.render.RaskolHudRenderer;
import dev.raskol.hud.client.render.RaskolHotbarRenderer;
import dev.raskol.hud.client.render.RaskolCrosshairRenderer;
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

        // Пока серверный снимок актуален, заменяем дублирующие ванильные слои.
        hideVanillaElementWhenRaskolHudIsActive(VanillaHudElements.HEALTH_BAR);
        hideVanillaElementWhenRaskolHudIsActive(VanillaHudElements.ARMOR_BAR);
        hideVanillaElementWhenRaskolHudIsActive(VanillaHudElements.FOOD_BAR);
        hideVanillaElementWhenRaskolHudIsActive(VanillaHudElements.SCOREBOARD);
        hideVanillaElementWhenRaskolHudIsActive(VanillaHudElements.OVERLAY_MESSAGE);

        HudElementRegistry.replaceElement(
                VanillaHudElements.CROSSHAIR,
                vanillaElement -> (graphics, deltaTracker) -> {
                    var state = HudStateStore.current();
                    if (state == null) {
                        vanillaElement.extractRenderState(graphics, deltaTracker);
                    } else {
                        RaskolCrosshairRenderer.render(graphics, state);
                    }
                });

        HudElementRegistry.replaceElement(
                VanillaHudElements.HOTBAR,
                vanillaElement -> (graphics, deltaTracker) -> {
                    vanillaElement.extractRenderState(graphics, deltaTracker);
                    var state = HudStateStore.current();
                    if (state != null) {
                        RaskolHotbarRenderer.render(graphics, state);
                    }
                });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath("raskolhud", "main"),
                RaskolHudRenderer::render);
    }

    private static void hideVanillaElementWhenRaskolHudIsActive(Identifier elementId) {
        HudElementRegistry.replaceElement(
                elementId,
                vanillaElement -> (graphics, deltaTracker) -> {
                    if (HudStateStore.current() == null) {
                        vanillaElement.extractRenderState(graphics, deltaTracker);
                    }
                });
    }
}
