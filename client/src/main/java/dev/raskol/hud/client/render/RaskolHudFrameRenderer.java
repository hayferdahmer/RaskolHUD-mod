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
        fill(graphics, x + 1, y + 1, x + width - 1, y + height - 1, alpha(STEEL_DARK, a));
        fill(graphics, x + 3, y + 2, x + width - 3, y + height - 2, alpha(STEEL, a));
        fill(graphics, x + 4, y + 3, x + width - 4, y + height - 3, alpha(PANEL, a));
        fill(graphics, x + 7, y + 6, x + width - 7, y + height - 6, alpha(PANEL_INNER, a));

        // Верхняя и нижняя кромки имеют разный свет, чтобы металл не выглядел плоским.
        fill(graphics, x + 9, y + 3, x + width - 9, y + 4, alpha(STEEL_LIGHT, a));
        fill(graphics, x + 9, y + height - 4, x + width - 9, y + height - 3, alpha(BLACK, a));
        fill(graphics, x + 4, y + 9, x + 5, y + height - 9, alpha(STEEL_LIGHT, a));
        fill(graphics, x + width - 5, y + 9, x + width - 4, y + height - 9, alpha(BLACK, a));

        // Внутренний цветной кант и угловые вырезы задают силуэт гербовой пластины.
        fill(graphics, x + 10, y + 5, x + width - 10, y + 6, alpha(accent, a));
        fill(graphics, x + 10, y + height - 6, x + width - 10, y + height - 5, alpha(accent, a / 2));
        corner(graphics, x + 5, y + 5, accent, a, false, false);
        corner(graphics, x + width - 5, y + 5, accent, a, true, false);
        corner(graphics, x + 5, y + height - 5, accent, a, false, true);
        corner(graphics, x + width - 5, y + height - 5, accent, a, true, true);

        // Заклёпки и центральный знак — отдельные пиксельные детали, а не сплошные линии.
        rivet(graphics, x + 8, y + 8, a);
        rivet(graphics, x + width - 8, y + 8, a);
        rivet(graphics, x + 8, y + height - 8, a);
        rivet(graphics, x + width - 8, y + height - 8, a);
        // Текстурная рамка рисуется девятью сегментами: углы не деформируются.
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
        // Общая рамка масштабируется по девяти сегментам, не растягивая угловой металл.
        RaskolHudTextureRenderer.renderPanelFrame(graphics, x, y, width, height, a);
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

    private static void corner(
            GuiGraphicsExtractor g, int cx, int cy, int accent, int opacity, boolean right, boolean bottom) {
        int sx = right ? -1 : 1;
        int sy = bottom ? -1 : 1;
        fill(g, cx, cy, cx + sx * 5, cy + sy, alpha(STEEL_LIGHT, opacity));
        fill(g, cx, cy, cx + sx, cy + sy * 5, alpha(STEEL_LIGHT, opacity));
        fill(g, cx + sx, cy + sy, cx + sx * 4, cy + sy * 2, alpha(accent, opacity));
        fill(g, cx + sx * 2, cy + sy * 2, cx + sx * 3, cy + sy * 3, alpha(BLACK, opacity));
    }

    private static void rivet(GuiGraphicsExtractor g, int x, int y, int opacity) {
        fill(g, x - 2, y - 2, x + 3, y + 3, alpha(BLACK, opacity));
        fill(g, x - 1, y - 1, x + 2, y + 2, alpha(STEEL_LIGHT, opacity));
        fill(g, x, y, x + 1, y + 1, alpha(STEEL_DARK, opacity));
    }

    private static void crest(GuiGraphicsExtractor g, int cx, int y, int accent, int opacity) {
        fill(g, cx - 5, y - 1, cx + 6, y + 2, alpha(BLACK, opacity));
        fill(g, cx - 3, y - 2, cx + 4, y + 3, alpha(accent, opacity));
        fill(g, cx - 1, y - 3, cx + 2, y + 4, alpha(STEEL_LIGHT, opacity));
        fill(g, cx - 1, y - 1, cx + 1, y + 1, alpha(BLACK, opacity));
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
