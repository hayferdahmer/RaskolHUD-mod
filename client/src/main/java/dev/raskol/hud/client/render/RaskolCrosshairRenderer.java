package dev.raskol.hud.client.render;

import dev.raskol.hud.protocol.RaskolHudSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Тонкий классовый прицел вместо яркого ванильного крестика. */
public final class RaskolCrosshairRenderer {

    private RaskolCrosshairRenderer() {}

    public static void render(GuiGraphicsExtractor graphics, RaskolHudSnapshot state) {
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        int color = RaskolClassTheme.forClass(state.classId()).accent();
        int shadow = 0xB0000000;

        graphics.fill(centerX - 7, centerY - 1, centerX - 3, centerY + 1, shadow);
        graphics.fill(centerX + 3, centerY - 1, centerX + 7, centerY + 1, shadow);
        graphics.fill(centerX - 1, centerY - 7, centerX + 1, centerY - 3, shadow);
        graphics.fill(centerX - 1, centerY + 3, centerX + 1, centerY + 7, shadow);
        graphics.fill(centerX - 6, centerY, centerX - 3, centerY + 1, color);
        graphics.fill(centerX + 3, centerY, centerX + 6, centerY + 1, color);
        graphics.fill(centerX, centerY - 6, centerX + 1, centerY - 3, color);
        graphics.fill(centerX, centerY + 3, centerX + 1, centerY + 6, color);
        graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xCC171216);
        graphics.fill(centerX, centerY, centerX + 1, centerY + 1, 0xFFE3D5C5);
    }
}
