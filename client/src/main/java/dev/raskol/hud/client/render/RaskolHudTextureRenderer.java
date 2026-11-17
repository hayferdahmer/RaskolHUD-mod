package dev.raskol.hud.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Растровые декоративные элементы, извлечённые из утверждённого фэнтези-концепта РАСКОЛ. */
public final class RaskolHudTextureRenderer {

    private static final Identifier FANTASY_HOTBAR_ORNAMENT =
            Identifier.fromNamespaceAndPath("raskolhud", "textures/gui/fantasy_hotbar_ornament.png");

    private static final Identifier RASKOL_CREST =
            Identifier.fromNamespaceAndPath("raskolhud", "textures/gui/raskol_crest.png");
    private static final Identifier PANEL_FRAME =
            Identifier.fromNamespaceAndPath("raskolhud", "textures/gui/gothic_panel_frame.png");
    private static final Identifier SLOT_FRAME =
            Identifier.fromNamespaceAndPath("raskolhud", "textures/gui/gothic_slot_frame.png");
    private static final Identifier PORTRAIT_FRAME =
            Identifier.fromNamespaceAndPath("raskolhud", "textures/gui/gothic_portrait_frame.png");
    private static final Identifier BAR_FRAME =
            Identifier.fromNamespaceAndPath("raskolhud", "textures/gui/gothic_bar_frame.png");

    private RaskolHudTextureRenderer() {}

    public static void renderPanelFrame(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int opacity) {
        blit(graphics, PANEL_FRAME, x, y, width, height, 256, 128, opacity);
    }

    public static void renderSlotFrame(
            GuiGraphicsExtractor graphics, int x, int y, int size, int opacity) {
        blit(graphics, SLOT_FRAME, x, y, size, size, 96, 96, opacity);
    }

    public static void renderPortraitFrame(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int opacity) {
        blit(graphics, PORTRAIT_FRAME, x, y, width, height, 128, 128, opacity);
    }

    public static void renderBarFrame(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int opacity) {
        blit(graphics, BAR_FRAME, x, y, width, height, 512, 48, opacity);
    }

    private static void blit(
            GuiGraphicsExtractor graphics, Identifier texture, int x, int y,
            int width, int height, int textureWidth, int textureHeight, int opacity) {
        int alpha = Math.max(0, Math.min(100, opacity)) * 255 / 100;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
                width, height, textureWidth, textureHeight, textureWidth, textureHeight,
                (alpha << 24) | 0x00FFFFFF);
    }

    public static void renderRaskolCrest(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int opacity) {
        int alpha = Math.max(0, Math.min(100, opacity)) * 255 / 100;
        graphics.blit(RenderPipelines.GUI_TEXTURED, RASKOL_CREST, x, y, 0.0F, 0.0F,
                width, height, 64, 67, 64, 67, (alpha << 24) | 0x00FFFFFF);
    }

    public static void renderFantasyHotbarOrnament(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int opacity) {
        int alpha = Math.max(0, Math.min(100, opacity)) * 255 / 100;
        int tint = (alpha << 24) | 0x00FFFFFF;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                FANTASY_HOTBAR_ORNAMENT,
                x,
                y,
                0.0F,
                0.0F,
                width,
                height,
                256,
                18,
                256,
                18,
                tint);
    }
}
