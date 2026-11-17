package dev.raskol.hud.client;

import dev.raskol.hud.client.config.HudConfig;
import dev.raskol.hud.client.config.HudConfigScreen;
import dev.raskol.hud.client.network.CrosshairSignalPayload;
import dev.raskol.hud.client.network.HelloPayload;
import dev.raskol.hud.client.network.HudStateStore;
import dev.raskol.hud.client.network.StatePayload;
import dev.raskol.hud.client.render.RaskolHudRenderer;
import dev.raskol.hud.client.render.RaskolHotbarRenderer;
import dev.raskol.hud.client.render.RaskolCrosshairRenderer;
import dev.raskol.hud.protocol.RaskolHudProtocol;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

public final class RaskolHudClient implements ClientModInitializer {

    private static final KeyMapping OPEN_SETTINGS = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.raskolhud.open_settings", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, KeyMapping.Category.MISC));

    @Override
    public void onInitializeClient() {
        HudConfig.load();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_SETTINGS.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new HudConfigScreen(null));
                }
            }
        });
        PayloadTypeRegistry.serverboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CrosshairSignalPayload.TYPE, CrosshairSignalPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(
                StatePayload.TYPE,
                (payload, context) -> HudStateStore.accept(payload.body()));
        ClientPlayNetworking.registerGlobalReceiver(
                CrosshairSignalPayload.TYPE,
                (payload, context) -> CrosshairSignalPayload.accept(payload.signal()));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            HudStateStore.clear();
            CrosshairSignalPayload.clear();
            ClientPlayNetworking.send(new HelloPayload(RaskolHudProtocol.PROTOCOL_VERSION));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HudStateStore.clear();
            CrosshairSignalPayload.clear();
        });

        // Пока серверный снимок актуален, заменяем дублирующие ванильные слои.
        hideVanillaElementWhenRaskolHudIsActive(VanillaHudElements.HEALTH_BAR, HudConfig::showStatus);
        hideVanillaElementWhenRaskolHudIsActive(VanillaHudElements.ARMOR_BAR, HudConfig::showStatus);
        hideVanillaElementWhenRaskolHudIsActive(VanillaHudElements.FOOD_BAR, HudConfig::showStatus);
        hideVanillaElementWhenRaskolHudIsActive(VanillaHudElements.SCOREBOARD, HudConfig::showScoreboard);
        hideVanillaElementWhenRaskolHudIsActive(VanillaHudElements.OVERLAY_MESSAGE, () -> true);

        HudElementRegistry.replaceElement(
                VanillaHudElements.CROSSHAIR,
                vanillaElement -> (graphics, deltaTracker) -> {
                    var state = HudStateStore.current();
                    if (state == null || !HudConfig.showClassCrosshair()) {
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
                    if (state != null && HudConfig.showHotbarFrame()) {
                        RaskolHotbarRenderer.render(graphics, state);
                    }
                });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath("raskolhud", "main"),
                RaskolHudRenderer::render);
    }

    private static void hideVanillaElementWhenRaskolHudIsActive(
            Identifier elementId,
            java.util.function.BooleanSupplier replacementEnabled) {
        HudElementRegistry.replaceElement(
                elementId,
                vanillaElement -> (graphics, deltaTracker) -> {
                    if (HudStateStore.current() == null || !replacementEnabled.getAsBoolean()) {
                        vanillaElement.extractRenderState(graphics, deltaTracker);
                    }
                });
    }
}
