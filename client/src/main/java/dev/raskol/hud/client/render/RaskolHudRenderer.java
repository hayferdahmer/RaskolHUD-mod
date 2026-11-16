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

    private static final int PANEL = 0xE8100B0F;
    private static final int BORDER = 0x99783544;
    private static final int TEXT = 0xFFEEE4E1;
    private static final int SECONDARY = 0xFFC0B0B0;
    private static final int MUTED = 0xFF8D7D80;
    private static final int HEALTH = 0xFF783544;
    private static final int RESOURCE = 0xFFB47B86;
    private static final int TRACK = 0xFF21171D;

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
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        drawIdentity(graphics, font, state, 16, 16);
        drawConnection(graphics, font, screenWidth - 178, 16);
        drawVitals(graphics, font, state, 16, screenHeight - 88);
    }

    private static void drawIdentity(
            GuiGraphicsExtractor graphics,
            Font font,
            RaskolHudSnapshot state,
            int x,
            int y) {

        drawPanel(graphics, x, y, 236, 60);
        String symbol = state.resourceSymbol().isBlank() ? "•" : state.resourceSymbol();

        graphics.text(font, symbol, x + 12, y + 11, RESOURCE);
        graphics.text(font, displayOr(state.className(), "Без класса"), x + 38, y + 8, TEXT);
        graphics.text(
                font,
                displayOr(state.specializationName(), "Специализация не выбрана"),
                x + 38,
                y + 27,
                SECONDARY);
        graphics.text(font, "УР. " + NUMBER_FORMAT.format(state.characterLevel()), x + 38, y + 43, MUTED);
    }

    private static void drawConnection(
            GuiGraphicsExtractor graphics,
            Font font,
            int x,
            int y) {

        drawPanel(graphics, x, y, 162, 30);
        graphics.fill(x + 10, y + 11, x + 16, y + 17, RESOURCE);
        graphics.text(font, "RASKOL · СВЯЗЬ АКТИВНА", x + 23, y + 10, TEXT);
    }

    private static void drawVitals(
            GuiGraphicsExtractor graphics,
            Font font,
            RaskolHudSnapshot state,
            int x,
            int y) {

        drawPanel(graphics, x, y, 274, 72);

        String healthValue = NUMBER_FORMAT.format(state.health())
                + " / "
                + NUMBER_FORMAT.format(state.healthMax());
        graphics.text(font, "ЗДОРОВЬЕ", x + 10, y + 7, SECONDARY);
        drawRightAligned(graphics, font, healthValue, x + 264, y + 7, TEXT);
        drawBar(graphics, x + 10, y + 22, 254, 7, state.health(), state.healthMax(), HEALTH);

        String resourceLabel = (state.resourceSymbol().isBlank() ? "" : state.resourceSymbol() + " ")
                + displayOr(state.resourceName(), "РЕСУРС");
        String resourceValue = state.resourceMax() > 0
                ? NUMBER_FORMAT.format(state.resource()) + " / " + NUMBER_FORMAT.format(state.resourceMax())
                : NUMBER_FORMAT.format(state.resource());

        graphics.text(font, resourceLabel, x + 10, y + 39, SECONDARY);
        drawRightAligned(graphics, font, resourceValue, x + 264, y + 39, TEXT);
        if (state.resourceMax() > 0) {
            drawBar(graphics, x + 10, y + 54, 254, 7, state.resource(), state.resourceMax(), RESOURCE);
        }
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width, y + 1, BORDER);
        graphics.fill(x, y, x + 1, y + height, BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
    }

    private static void drawBar(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int current,
            int maximum,
            int color) {

        graphics.fill(x, y, x + width, y + height, TRACK);
        if (maximum <= 0 || current <= 0) {
            return;
        }

        double fraction = Math.max(0.0, Math.min(1.0, (double) current / maximum));
        int filledWidth = (int) Math.round(width * fraction);
        if (filledWidth > 0) {
            graphics.fill(x, y, x + filledWidth, y + height, color);
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
