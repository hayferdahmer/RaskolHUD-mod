package dev.raskol.hud.client.render;

import java.text.NumberFormat;
import java.util.Locale;

import dev.raskol.hud.client.network.HudStateStore;
import dev.raskol.hud.protocol.RaskolHudSnapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class RaskolHudRenderer {

    private static final int SHADOW = 0x78000000;
    private static final int FRAME = 0xDD100D12;
    private static final int INNER = 0xC419151A;
    private static final int EDGE = 0xFF76513F;
    private static final int EDGE_DARK = 0xFF38262B;
    private static final int TEXT = 0xFFF0E8DF;
    private static final int SECONDARY = 0xFFC0B1A8;
    private static final int MUTED = 0xFF8F817D;
    private static final int HEALTH = 0xFF873747;
    private static final int HEALTH_HIGHLIGHT = 0xFFB35C68;
    private static final int RESOURCE = 0xFFB58A62;
    private static final int TRACK = 0xFF21191E;
    private static final int CONNECTION = 0xFFB58A62;

    private static final int PANEL_X = 12;
    private static final int PANEL_Y = 12;
    private static final int PANEL_WIDTH = 252;
    private static final int PANEL_HEIGHT = 78;

    private static final NumberFormat NUMBER_FORMAT =
            NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU"));

    private RaskolHudRenderer() {}

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        RaskolHudSnapshot state = HudStateStore.current();
        if (state == null) {
            return;
        }

        Font font = client.font;
        drawCharacterFrame(graphics, font, state);
        drawConnection(graphics, font, graphics.guiWidth());
    }

    private static void drawCharacterFrame(
            GuiGraphicsExtractor graphics,
            Font font,
            RaskolHudSnapshot state) {

        int x = PANEL_X;
        int y = PANEL_Y;
        int right = x + PANEL_WIDTH;

        drawFrame(graphics, x, y, PANEL_WIDTH, PANEL_HEIGHT);

        drawEmblem(graphics, font, state, x + 10, y + 19);

        graphics.text(font, displayOr(state.className(), "Без класса"), x + 48, y + 8, TEXT);
        drawRightAligned(
                graphics,
                font,
                "УР. " + NUMBER_FORMAT.format(state.characterLevel()),
                right - 10,
                y + 9,
                RESOURCE);

        graphics.text(
                font,
                displayOr(state.specializationName(), "Специализация не выбрана"),
                x + 48,
                y + 22,
                SECONDARY);

        String healthValue = NUMBER_FORMAT.format(state.health())
                + " / "
                + NUMBER_FORMAT.format(state.healthMax());
        graphics.text(font, "ЗДОРОВЬЕ", x + 48, y + 39, MUTED);
        drawRightAligned(graphics, font, healthValue, right - 10, y + 39, TEXT);
        drawBar(
                graphics,
                x + 48,
                y + 52,
                PANEL_WIDTH - 58,
                6,
                state.health(),
                state.healthMax(),
                HEALTH,
                HEALTH_HIGHLIGHT);

        String resourceLabel = displayOr(state.resourceName(), "РЕСУРС");
        String resourceValue = state.resourceMax() > 0
                ? NUMBER_FORMAT.format(state.resource()) + " / " + NUMBER_FORMAT.format(state.resourceMax())
                : NUMBER_FORMAT.format(state.resource());

        graphics.text(font, resourceLabel, x + 48, y + 63, MUTED);
        drawRightAligned(graphics, font, resourceValue, right - 10, y + 63, TEXT);

        if (state.resourceMax() > 0) {
            drawBar(
                    graphics,
                    x + 48,
                    y + 72,
                    PANEL_WIDTH - 58,
                    3,
                    state.resource(),
                    state.resourceMax(),
                    RESOURCE,
                    RESOURCE);
        }
    }

    private static void drawFrame(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height) {

        graphics.fill(x + 2, y + 3, x + width + 2, y + height + 3, SHADOW);
        graphics.fill(x, y, x + width, y + height, FRAME);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, INNER);

        graphics.fill(x + 7, y, x + width - 7, y + 1, EDGE);
        graphics.fill(x, y + 7, x + 1, y + height - 7, EDGE_DARK);
        graphics.fill(x + width - 1, y + 7, x + width, y + height - 7, EDGE_DARK);
        graphics.fill(x + 7, y + height - 1, x + width - 7, y + height, EDGE_DARK);

        graphics.fill(x + 8, y + 3, x + width - 8, y + 4, 0x553F2C2C);
    }

    private static void drawEmblem(
            GuiGraphicsExtractor graphics,
            Font font,
            RaskolHudSnapshot state,
            int x,
            int y) {

        graphics.fill(x + 1, y + 2, x + 29, y + 30, SHADOW);
        graphics.fill(x, y, x + 28, y + 28, EDGE_DARK);
        graphics.fill(x + 1, y + 1, x + 27, y + 27, 0xFF151116);
        graphics.fill(x + 3, y + 3, x + 25, y + 4, EDGE);
        graphics.fill(x + 3, y + 24, x + 25, y + 25, EDGE_DARK);

        String symbol = displayOr(state.resourceSymbol(), "✦");
        int symbolX = x + (28 - font.width(symbol)) / 2;
        int symbolY = y + (28 - 9) / 2;
        graphics.text(font, symbol, symbolX, symbolY, RESOURCE);
    }

    private static void drawConnection(GuiGraphicsExtractor graphics, Font font, int screenWidth) {
        String label = "РАСКОЛ";
        int textWidth = font.width(label);
        int x = screenWidth - textWidth - 22;
        int y = 17;

        graphics.fill(x, y + 4, x + 5, y + 9, CONNECTION);
        graphics.fill(x + 1, y + 5, x + 4, y + 8, 0xFFFFD9A0);
        graphics.text(font, label, x + 10, y + 1, SECONDARY);
    }

    private static void drawBar(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int current,
            int maximum,
            int fillColor,
            int highlightColor) {

        graphics.fill(x, y, x + width, y + height, TRACK);
        graphics.fill(x, y, x + width, y + 1, 0xFF34252A);

        if (maximum <= 0 || current <= 0) {
            return;
        }

        double fraction = Math.max(0.0, Math.min(1.0, (double) current / maximum));
        int filledWidth = (int) Math.round(width * fraction);
        if (filledWidth <= 0) {
            return;
        }

        graphics.fill(x, y + 1, x + filledWidth, y + height, fillColor);
        graphics.fill(x, y + 1, x + filledWidth, y + 2, highlightColor);

        for (int segment = 1; segment < 5; segment++) {
            int markerX = x + width * segment / 5;
            if (markerX < x + filledWidth) {
                graphics.fill(markerX, y + 1, markerX + 1, y + height, 0x557A6260);
            }
        }
    }

    private static void drawRightAligned(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int right,
            int y,
            int color) {

        graphics.text(font, text, right - font.width(text), y, color);
    }

    private static String displayOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
