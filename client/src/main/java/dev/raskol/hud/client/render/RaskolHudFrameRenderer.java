package dev.raskol.hud.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Общие декоративные элементы интерфейса РАСКОЛ.
 *
 * Рисуем собственную геометрию поверх GUI-слоя Minecraft: без сторонних
 * библиотек, изменения протокола и вмешательства в игровые данные.
 */
public final class RaskolHudFrameRenderer {

    private static final int SHADOW = 0xA8000000;
    private static final int BLACK = 0xFF050509;
    private static final int STEEL_DARK = 0xFF30272B;
    private static final int STEEL = 0xFF79645A;
    private static final int STEEL_LIGHT = 0xFFC1A58B;
    private static final int PANEL = 0xE90D0B10;
    private static final int PANEL_INNER = 0xD8171218;

    private RaskolHudFrameRenderer() {}

    public static void panel(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int accent, int opacity) {
        if (width < 16 || height < 12) {
            return;
        }
        int a = clampAlpha(opacity);
        fill(graphics, x + 3, y + 4, x + width + 3, y + height + 4, alpha(SHADOW, a));
        fill(graphics, x + 2, y, x + width - 2, y + height, alpha(BLACK, a));
        fill(graphics, x + 5, y + 4, x + width - 5, y + height - 4, alpha(PANEL, a));
        fill(graphics, x + 8, y + 7, x + width - 8, y + height - 7, alpha(PANEL_INNER, a));

        // Прозрачная PNG-оправа рисуется поверх подложки без деформации углов.
        RaskolHudTextureRenderer.renderPanelFrame(graphics, x, y, width, height, a);
    }

    public static void hotbarPlate(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int accent, int opacity) {
        int a = clampAlpha(opacity);
        // Только внешний контур: центральная область остаётся прозрачной для предметов хотбара.
        fill(graphics, x + 3, y + 3, x + width - 3, y + 4, alpha(SHADOW, a));
        fill(graphics, x + 3, y + height - 4, x + width - 3, y + height - 3, alpha(SHADOW, a));
        fill(graphics, x + 2, y + 1, x + width - 2, y + 2, alpha(BLACK, a));
        fill(graphics, x + 2, y + height - 2, x + width - 2, y + height - 1, alpha(BLACK, a));
        fill(graphics, x + 1, y + 4, x + 2, y + height - 4, alpha(STEEL_LIGHT, a));
        fill(graphics, x + width - 2, y + 4, x + width - 1, y + height - 4, alpha(BLACK, a));
        fill(graphics, x + 8, y + 2, x + width - 8, y + 3, alpha(accent, a));
        fill(graphics, x + 8, y + height - 3, x + width - 8, y + height - 2, alpha(accent, a / 2));
        rivet(graphics, x + 5, y + 5, a);
        rivet(graphics, x + width - 5, y + 5, a);
        // Центральная часть пластины остаётся свободной, чтобы не задевать предметы.
    }

    public static void slot(
            GuiGraphicsExtractor graphics, int x, int y, int size, int accent, boolean selected, int opacity) {
        int a = clampAlpha(opacity);
        // Рисуем только фаски по краям, не перекрывая ванильную иконку предмета в центре.
        fill(graphics, x, y, x + size, y + 1, alpha(BLACK, a));
        fill(graphics, x, y + 1, x + 1, y + size - 1, alpha(STEEL_LIGHT, a));
        fill(graphics, x + size - 1, y + 1, x + size, y + size - 1, alpha(BLACK, a));
        fill(graphics, x, y + size - 1, x + size, y + size, alpha(BLACK, a));
        fill(graphics, x + 2, y + 2, x + size - 2, y + 3, alpha(selected ? accent : STEEL, a));
        fill(graphics, x + 2, y + size - 3, x + size - 2, y + size - 2,
                alpha(selected ? accent : STEEL_DARK, a));
        RaskolHudTextureRenderer.renderSlotFrame(graphics, x, y, size, a);
        if (selected) {
            fill(graphics, x + 1, y + 1, x + 2, y + size - 1, alpha(accent, a));
            fill(graphics, x + size - 2, y + 1, x + size - 1, y + size - 1, alpha(accent, a));
            fill(graphics, x + size / 2 - 2, y, x + size / 2 + 2, y + 2, alpha(STEEL_LIGHT, a));
        }
    }

    private static void rivet(GuiGraphicsExtractor g, int x, int y, int opacity) {
        fill(g, x - 2, y - 2, x + 3, y + 3, alpha(BLACK, opacity));
        fill(g, x - 1, y - 1, x + 2, y + 2, alpha(STEEL_LIGHT, opacity));
        fill(g, x, y, x + 1, y + 1, alpha(STEEL_DARK, opacity));
    }

    private static void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int left = Math.min(x1, x2);
        int top = Math.min(y1, y2);
        int right = Math.max(x1, x2);
        int bottom = Math.max(y1, y2);
        if (right > left && bottom > top) {
            g.fill(left, top, right, bottom, color);
        }
    }

    private static int alpha(int color, int opacity) {
        return (color & 0x00FFFFFF) | (((color >>> 24) * clampAlpha(opacity) / 100) << 24);
    }

    private static int clampAlpha(int opacity) {
        return Math.max(0, Math.min(100, opacity));
    }
}
