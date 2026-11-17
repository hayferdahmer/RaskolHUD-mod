package dev.raskol.hud.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Растровые декоративные элементы интерфейса РАСКОЛ. */
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
        blitNineSlice(graphics, PANEL_FRAME, x, y, width, height, 256, 128,
                30, 30, 24, 24, opacity);
    }

    public static void renderSlotFrame(
            GuiGraphicsExtractor graphics, int x, int y, int size, int opacity) {
        blitNineSlice(graphics, SLOT_FRAME, x, y, size, size, 96, 96,
                18, 18, 18, 18, opacity);
    }

    public static void renderPortraitFrame(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int opacity) {
        blitNineSlice(graphics, PORTRAIT_FRAME, x, y, width, height, 128, 128,
                28, 28, 28, 28, opacity);
    }

    public static void renderBarFrame(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int opacity) {
        blitNineSlice(graphics, BAR_FRAME, x, y, width, height, 512, 48,
                24, 24, 10, 10, opacity);
    }

    /**
     * Рисует рамку девятью независимыми фрагментами.
     * Углы сохраняют исходный размер, стороны растягиваются только по одной оси,
     * центральная область масштабируется независимо от декоративных углов.
     */
    private static void blitNineSlice(
            GuiGraphicsExtractor graphics,
            Identifier texture,
            int x,
            int y,
            int width,
            int height,
            int textureWidth,
            int textureHeight,
            int sourceLeft,
            int sourceRight,
            int sourceTop,
            int sourceBottom,
            int opacity) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int alpha = Math.max(0, Math.min(100, opacity)) * 255 / 100;
        int tint = (alpha << 24) | 0x00FFFFFF;

        int left = Math.min(sourceLeft, width / 2);
        int right = Math.min(sourceRight, width - left);
        int top = Math.min(sourceTop, height / 2);
        int bottom = Math.min(sourceBottom, height - top);

        int centerSourceWidth = textureWidth - sourceLeft - sourceRight;
        int centerSourceHeight = textureHeight - sourceTop - sourceBottom;
        int centerWidth = width - left - right;
        int centerHeight = height - top - bottom;

        // Верхний ряд.
        blitPart(graphics, texture, x, y, left, top,
                0, 0, sourceLeft, sourceTop, textureWidth, textureHeight, tint);
        blitPart(graphics, texture, x + left, y, centerWidth, top,
                sourceLeft, 0, centerSourceWidth, sourceTop, textureWidth, textureHeight, tint);
        blitPart(graphics, texture, x + left + centerWidth, y, right, top,
                textureWidth - sourceRight, 0, sourceRight, sourceTop, textureWidth, textureHeight, tint);

        // Средний ряд.
        blitPart(graphics, texture, x, y + top, left, centerHeight,
                0, sourceTop, sourceLeft, centerSourceHeight, textureWidth, textureHeight, tint);
        blitPart(graphics, texture, x + left, y + top, centerWidth, centerHeight,
                sourceLeft, sourceTop, centerSourceWidth, centerSourceHeight,
                textureWidth, textureHeight, tint);
        blitPart(graphics, texture, x + left + centerWidth, y + top, right, centerHeight,
                textureWidth - sourceRight, sourceTop, sourceRight, centerSourceHeight,
                textureWidth, textureHeight, tint);

        // Нижний ряд.
        blitPart(graphics, texture, x, y + top + centerHeight, left, bottom,
                0, textureHeight - sourceBottom, sourceLeft, sourceBottom,
                textureWidth, textureHeight, tint);
        blitPart(graphics, texture, x + left, y + top + centerHeight, centerWidth, bottom,
                sourceLeft, textureHeight - sourceBottom, centerSourceWidth, sourceBottom,
                textureWidth, textureHeight, tint);
        blitPart(graphics, texture, x + left + centerWidth, y + top + centerHeight, right, bottom,
                textureWidth - sourceRight, textureHeight - sourceBottom, sourceRight, sourceBottom,
                textureWidth, textureHeight, tint);
    }

    private static void blitPart(
            GuiGraphicsExtractor graphics,
            Identifier texture,
            int x,
            int y,
            int width,
            int height,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight,
            int textureWidth,
            int textureHeight,
            int tint) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
                (float) sourceX, (float) sourceY, width, height,
                sourceWidth, sourceHeight, textureWidth, textureHeight, tint);
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
        graphics.blit(RenderPipelines.GUI_TEXTURED, FANTASY_HOTBAR_ORNAMENT, x, y,
                0.0F, 0.0F, width, height, 256, 18, 256, 18,
                (alpha << 24) | 0x00FFFFFF);
    }
}
