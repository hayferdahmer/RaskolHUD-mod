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

    private RaskolHudTextureRenderer() {}

    public static void renderRaskolCrest(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int opacity) {
        int alpha = Math.max(0, Math.min(100, opacity)) * 255 / 100;
        graphics.blit(RenderPipelines.GUI_TEXTURED, RASKOL_CREST, x, y, 0.0F, 0.0F,
                width, height, 64, 67, (alpha << 24) | 0x00FFFFFF);
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
                tint);
    }
}
