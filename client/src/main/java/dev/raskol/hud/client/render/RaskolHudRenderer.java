package dev.raskol.hud.client.render;

import java.text.NumberFormat;
import java.util.Locale;

import dev.raskol.hud.client.network.HudStateStore;
import dev.raskol.hud.protocol.RaskolHudSnapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

public final class RaskolHudRenderer {

    private static final int SHADOW = 0x78000000;
    private static final int FRAME = 0xDD100D12;
    private static final int INNER = 0xE019151A;
    private static final int EDGE = 0xFF76513F;
    private static final int EDGE_DARK = 0xFF38262B;
    private static final int TEXT = 0xFFF0E8DF;
    private static final int SECONDARY = 0xFFC0B1A8;
    private static final int MUTED = 0xFF8F817D;
    private static final int HEALTH = 0xFF873747;
    private static final int HEALTH_HIGHLIGHT = 0xFFB35C68;
    private static final int RESOURCE = 0xFFB58A62;
    private static final int ARMOR = 0xFF8E9AA8;
    private static final int HUNGER = 0xFFC18A4E;
    private static final int TRACK = 0xFF21191E;
    private static final int CONNECTION = 0xFFB58A62;

    private static final int IDENTITY_X = 12;
    private static final int IDENTITY_Y = 12;
    private static final int IDENTITY_WIDTH = 252;
    private static final int IDENTITY_HEIGHT = 48;

    private static final int STATUS_WIDTH = 168;
    private static final int STATUS_HEIGHT = 44;
    private static final int STATUS_BOTTOM_OFFSET = 20;
    private static final int STATUS_SIDE_OFFSET = 274;

    private static final NumberFormat NUMBER_FORMAT =
            NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU"));

    private RaskolHudRenderer() {}

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null) {
            return;
        }

        RaskolHudSnapshot state = HudStateStore.current();
        if (state == null) {
            return;
        }

        Font font = client.font;
        drawIdentity(graphics, font, state);
        drawConnection(graphics, font, graphics.guiWidth());
        drawBottomStatus(graphics, font, player, state, graphics.guiWidth(), graphics.guiHeight());
    }

    private static void drawIdentity(
            GuiGraphicsExtractor graphics,
            Font font,
            RaskolHudSnapshot state) {

        int x = IDENTITY_X;
        int y = IDENTITY_Y;
        int right = x + IDENTITY_WIDTH;

        drawFrame(graphics, x, y, IDENTITY_WIDTH, IDENTITY_HEIGHT);
        drawEmblem(graphics, font, state, x + 10, y + 10);

        graphics.text(font, displayOr(state.className(), "Без класса"), x + 48, y + 8, TEXT);
        drawRightAligned(
                graphics,
                font,
                "УР. " + NUMBER_FORMAT.format(state.characterLevel()),
                right - 10,
                y + 8,
                RESOURCE);

        graphics.text(
                font,
                displayOr(state.specializationName(), "Специализация не выбрана"),
                x + 48,
                y + 23,
                SECONDARY);
    }

    private static void drawBottomStatus(
            GuiGraphicsExtractor graphics,
            Font font,
            Player player,
            RaskolHudSnapshot state,
            int screenWidth,
            int screenHeight) {

        int center = screenWidth / 2;
        int leftX = center - STATUS_SIDE_OFFSET;
        int rightX = center + STATUS_SIDE_OFFSET - STATUS_WIDTH;
        int y = screenHeight - STATUS_BOTTOM_OFFSET - STATUS_HEIGHT;

        // На обычном разрешении блоки занимают те же боковые зоны, что и
        // стандартные полосы здоровья/брони и голода, не перекрывая хотбар и XP.
        if (leftX >= 4 && rightX + STATUS_WIDTH <= screenWidth - 4) {
            drawHealthArmorPanel(graphics, font, state, player, leftX, y);
            drawResourceHungerPanel(graphics, font, state, player, rightX, y);
            return;
        }

        // Узкие GUI-масштабы: переносим показатели в одну компактную панель над XP.
        int width = Math.max(180, screenWidth - 12);
        int x = (screenWidth - width) / 2;
        int compactY = screenHeight - STATUS_BOTTOM_OFFSET - STATUS_HEIGHT - 28;
        drawCompactStatusPanel(graphics, font, state, player, x, compactY, width);
    }

    private static void drawHealthArmorPanel(
            GuiGraphicsExtractor graphics,
            Font font,
            RaskolHudSnapshot state,
            Player player,
            int x,
            int y) {

        drawFrame(graphics, x, y, STATUS_WIDTH, STATUS_HEIGHT);

        String healthValue = NUMBER_FORMAT.format(state.health())
                + " / "
                + NUMBER_FORMAT.format(state.healthMax());
        graphics.text(font, "ЗДОРОВЬЕ", x + 8, y + 5, MUTED);
        drawRightAligned(graphics, font, healthValue, x + STATUS_WIDTH - 8, y + 5, TEXT);

        drawBar(
                graphics,
                x + 8,
                y + 16,
                STATUS_WIDTH - 16,
                5,
                state.health(),
                state.healthMax(),
                HEALTH,
                HEALTH_HIGHLIGHT);

        int armor = Math.max(0, player.getArmorValue());
        graphics.text(font, "БРОНЯ", x + 8, y + 27, MUTED);
        drawRightAligned(
                graphics,
                font,
                NUMBER_FORMAT.format(armor),
                x + STATUS_WIDTH - 8,
                y + 27,
                ARMOR);
        drawSegmentedMeter(graphics, x + 8, y + 37, STATUS_WIDTH - 16, 3, armor, 20, ARMOR);
    }

    private static void drawResourceHungerPanel(
            GuiGraphicsExtractor graphics,
            Font font,
            RaskolHudSnapshot state,
            Player player,
            int x,
            int y) {

        drawFrame(graphics, x, y, STATUS_WIDTH, STATUS_HEIGHT);

        String resourceLabel = displayOr(state.resourceName(), "РЕСУРС").toUpperCase(Locale.ROOT);
        String resourceValue = state.resourceMax() > 0
                ? NUMBER_FORMAT.format(state.resource()) + " / " + NUMBER_FORMAT.format(state.resourceMax())
                : NUMBER_FORMAT.format(state.resource());

        String symbol = state.resourceSymbol().isBlank() ? "" : state.resourceSymbol() + " ";
        graphics.text(font, symbol + resourceLabel, x + 8, y + 5, MUTED);
        drawRightAligned(graphics, font, resourceValue, x + STATUS_WIDTH - 8, y + 5, TEXT);

        if (state.resourceMax() > 0) {
            drawBar(
                    graphics,
                    x + 8,
                    y + 16,
                    STATUS_WIDTH - 16,
                    5,
                    state.resource(),
                    state.resourceMax(),
                    RESOURCE,
                    RESOURCE);
        } else {
            // Максимум пока не опубликован API RaskolClasses — не создаём ложную шкалу.
            drawDecorativeRule(graphics, x + 8, y + 18, STATUS_WIDTH - 16, RESOURCE);
        }

        FoodData food = player.getFoodData();
        int foodLevel = Math.max(0, Math.min(20, food.getFoodLevel()));
        float saturation = Math.max(0.0F, Math.min(20.0F, food.getSaturationLevel()));

        graphics.text(font, "СЫТОСТЬ", x + 8, y + 27, MUTED);
        drawRightAligned(
                graphics,
                font,
                foodLevel + "/20",
                x + STATUS_WIDTH - 8,
                y + 27,
                HUNGER);

        drawFoodPips(graphics, x + 8, y + 37, STATUS_WIDTH - 16, foodLevel, saturation);
    }

    private static void drawCompactStatusPanel(
            GuiGraphicsExtractor graphics,
            Font font,
            RaskolHudSnapshot state,
            Player player,
            int x,
            int y,
            int width) {

        int height = 52;
        drawFrame(graphics, x, y, width, height);

        int half = width / 2;
        int left = x + 8;
        int right = x + half + 4;
        int rightEdge = x + width - 8;

        String healthValue = NUMBER_FORMAT.format(state.health())
                + "/"
                + NUMBER_FORMAT.format(state.healthMax());
        graphics.text(font, "ЗДОРОВЬЕ", left, y + 5, MUTED);
        drawRightAligned(graphics, font, healthValue, x + half - 4, y + 5, TEXT);
        drawBar(graphics, left, y + 16, half - 20, 4,
                state.health(), state.healthMax(), HEALTH, HEALTH_HIGHLIGHT);

        String resourceName = displayOr(state.resourceName(), "РЕСУРС").toUpperCase(Locale.ROOT);
        String resourceValue = state.resourceMax() > 0
                ? state.resource() + "/" + state.resourceMax()
                : String.valueOf(state.resource());
        graphics.text(font, resourceName, right, y + 5, MUTED);
        drawRightAligned(graphics, font, resourceValue, rightEdge, y + 5, TEXT);
        if (state.resourceMax() > 0) {
            drawBar(graphics, right, y + 16, half - 20, 4,
                    state.resource(), state.resourceMax(), RESOURCE, RESOURCE);
        } else {
            drawDecorativeRule(graphics, right, y + 18, half - 20, RESOURCE);
        }

        int armor = Math.max(0, player.getArmorValue());
        FoodData food = player.getFoodData();
        int foodLevel = Math.max(0, Math.min(20, food.getFoodLevel()));
        float saturation = Math.max(0.0F, Math.min(20.0F, food.getSaturationLevel()));

        graphics.text(font, "БРОНЯ " + armor, left, y + 29, ARMOR);
        drawSegmentedMeter(graphics, left, y + 41, half - 20, 4, armor, 20, ARMOR);

        graphics.text(font, "СЫТОСТЬ " + foodLevel + "/20", right, y + 29, HUNGER);
        drawFoodPips(graphics, right, y + 41, half - 20, foodLevel, saturation);
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
        if (maximum <= 0 || current <= 0 || width <= 0) {
            return;
        }

        double fraction = Math.max(0.0, Math.min(1.0, (double) current / maximum));
        int filledWidth = (int) Math.round(width * fraction);
        if (filledWidth <= 0) {
            return;
        }

        graphics.fill(x, y, x + filledWidth, y + height, fillColor);
        graphics.fill(x, y, x + filledWidth, Math.min(y + height, y + 1), highlightColor);
    }

    private static void drawSegmentedMeter(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int current,
            int maximum,
            int color) {

        if (width <= 0 || maximum <= 0) {
            return;
        }

        int segments = 10;
        int gap = 2;
        int segmentWidth = Math.max(1, (width - gap * (segments - 1)) / segments);
        int clamped = Math.max(0, Math.min(maximum, current));

        for (int i = 0; i < segments; i++) {
            int segmentX = x + i * (segmentWidth + gap);
            graphics.fill(segmentX, y, segmentX + segmentWidth, y + height, TRACK);

            double segmentFill = Math.max(0.0, Math.min(1.0, (clamped - i * maximum / (double) segments)
                    / (maximum / (double) segments)));
            int fillWidth = (int) Math.round(segmentWidth * segmentFill);
            if (fillWidth > 0) {
                graphics.fill(segmentX, y, segmentX + fillWidth, y + height, color);
            }
        }
    }

    private static void drawFoodPips(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int foodLevel,
            float saturation) {

        if (width <= 0) {
            return;
        }

        int pips = 10;
        int gap = 2;
        int pipWidth = Math.max(1, (width - gap * (pips - 1)) / pips);

        for (int i = 0; i < pips; i++) {
            int pipX = x + i * (pipWidth + gap);
            graphics.fill(pipX, y, pipX + pipWidth, y + 4, TRACK);

            double fill = Math.max(0.0, Math.min(1.0, (foodLevel - i * 2) / 2.0));
            int fillWidth = (int) Math.round(pipWidth * fill);
            if (fillWidth > 0) {
                graphics.fill(pipX, y, pipX + fillWidth, y + 4, HUNGER);
            }
        }

        // Насыщение показываем тонкой верхней линией — это отдельный запас еды.
        int saturationWidth = (int) Math.round(width * Math.max(0.0F, Math.min(1.0F, saturation / 20.0F)));
        if (saturationWidth > 0) {
            graphics.fill(x, y - 1, x + saturationWidth, y, 0xFFE4C08B);
        }
    }

    private static void drawDecorativeRule(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int color) {

        if (width <= 0) {
            return;
        }

        graphics.fill(x, y, x + width, y + 1, TRACK);
        graphics.fill(x, y, x + Math.min(14, width), y + 1, color);
        graphics.fill(x + width - Math.min(14, width), y, x + width, y + 1, color);
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
