package dev.raskol.hud.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Растровые декоративные элементы, извлечённые из утверждённого фэнтези-концепта РАСКОЛ. */
public final class RaskolHudTextureRenderer {

    private static final Identifier FANTASY_HOTBAR_ORNAMENT =
            Identifier.fromNamespaceAndPath("raskolhud", "textures/gui/fantasy_hotbar_ornament.png");

    private RaskolHudTextureRenderer() {}

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
