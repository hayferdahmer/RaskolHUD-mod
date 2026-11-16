package dev.raskol.hud.client.render;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.raskol.hud.client.network.HudStateStore;
import dev.raskol.hud.protocol.RaskolHudEffect;
import dev.raskol.hud.protocol.RaskolHudSnapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

public final class RaskolHudRenderer {

    private static final int SHADOW = 0x78000000;
    private static final int FRAME = 0xDD100D12;
    private static final int INNER = 0xE019151A;
    private static final int EDGE_DARK = 0xFF38262B;
    private static final int TEXT = 0xFFF0E8DF;
    private static final int SECONDARY = 0xFFC0B1A8;
    private static final int MUTED = 0xFF8F817D;
    private static final int HEALTH = 0xFF873747;
    private static final int HEALTH_HIGHLIGHT = 0xFFB35C68;
    private static final int ARMOR = 0xFF8E9AA8;
    private static final int HUNGER = 0xFFC18A4E;
    private static final int TRACK = 0xFF21191E;
    private static final int DIVIDER = 0xFF493238;

    private static final int IDENTITY_X = 12;
    private static final int IDENTITY_Y = 12;
    private static final int IDENTITY_WIDTH = 260;
    private static final int IDENTITY_HEIGHT = 50;
    private static final int STATUS_WIDTH = 360;
    private static final int STATUS_HEIGHT = 48;
    private static final int HOTBAR_TOP_OFFSET = 24;

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
        drawIdentity(graphics, font, player, state);
        drawConnection(graphics, font, graphics.guiWidth());
        drawBottomStatus(graphics, font, player, state, graphics.guiWidth(), graphics.guiHeight());
        RaskolScoreboardRenderer.render(graphics, font, client);
    }

    private static void drawIdentity(
            GuiGraphicsExtractor graphics,
            Font font,
            Player player,
            RaskolHudSnapshot state) {

        int x = IDENTITY_X;
        int y = IDENTITY_Y;
        int right = x + IDENTITY_WIDTH;
        RaskolClassTheme theme = RaskolClassTheme.forClass(state.classId());

        drawFrame(graphics, x, y, IDENTITY_WIDTH, IDENTITY_HEIGHT, theme.accent());
        drawPlayerHead(graphics, player, x + 10, y + 10, theme);

        String nickname = fit(font, player.getName().getString(), 150);
        graphics.text(font, nickname, x + 49, y + 9, TEXT);
        drawRightAligned(
                graphics, font, "УР. " + NUMBER_FORMAT.format(state.characterLevel()),
                right - 10, y + 9, theme.highlight());

        String className = displayOr(state.className(), "Без класса");
        String specialization = state.specializationName();
        graphics.text(font, className, x + 49, y + 26, theme.highlight());
        if (!specialization.isBlank()) {
            String specText = " (" + specialization + ")";
            int specX = x + 49 + font.width(className);
            int available = right - 10 - specX;
            graphics.text(font, fit(font, specText, Math.max(0, available)), specX, y + 26, SECONDARY);
        } else {
            graphics.text(font, " (специализация не выбрана)",
                    x + 49 + font.width(className), y + 26, SECONDARY);
        }
    }

    private static void drawPlayerHead(
            GuiGraphicsExtractor graphics,
            Player player,
            int x,
            int y,
            RaskolClassTheme theme) {

        graphics.fill(x - 2, y - 2, x + 30, y + 30, SHADOW);
        graphics.fill(x - 1, y - 1, x + 29, y + 29, theme.muted());
        graphics.fill(x, y, x + 28, y + 28, 0xFF171319);

        if (player instanceof AbstractClientPlayer clientPlayer) {
            var texture = clientPlayer.getSkin().texture();
            // Лицо и верхний слой берутся из актуальной текстуры скина игрока.
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                    x, y, 8.0F, 8.0F, 28, 28, 8, 8, 64, 64);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                    x, y, 40.0F, 8.0F, 28, 28, 8, 8, 64, 64);
        } else {
            graphics.fill(x + 8, y + 8, x + 20, y + 20, theme.accent());
        }

        graphics.fill(x - 1, y - 1, x + 29, y, theme.highlight());
        graphics.fill(x - 1, y + 28, x + 29, y + 29, EDGE_DARK);
    }

    private static void drawBottomStatus(
            GuiGraphicsExtractor graphics,
            Font font,
            Player player,
            RaskolHudSnapshot state,
            int screenWidth,
            int screenHeight) {

        int width = Math.min(STATUS_WIDTH, Math.max(160, screenWidth - 16));
        int x = (screenWidth - width) / 2;
        int y = screenHeight - HOTBAR_TOP_OFFSET - STATUS_HEIGHT;
        int half = width / 2;
        int left = x + 10;
        int right = x + half + 8;
        int rightEdge = x + width - 10;
        RaskolClassTheme theme = RaskolClassTheme.forClass(state.classId());

        drawCrowdControl(graphics, font, state, screenWidth, y - 23);
        drawFrame(graphics, x, y, width, STATUS_HEIGHT, theme.accent());
        graphics.fill(x + half, y + 7, x + half + 1, y + STATUS_HEIGHT - 7, DIVIDER);

        String healthValue = NUMBER_FORMAT.format(state.health()) + " / " + NUMBER_FORMAT.format(state.healthMax());
        graphics.text(font, "ЗДОРОВЬЕ", left, y + 5, MUTED);
        drawRightAligned(graphics, font, healthValue, x + half - 8, y + 5, TEXT);
        drawBar(graphics, left, y + 17, half - 28, 5,
                state.health(), state.healthMax(), HEALTH, HEALTH_HIGHLIGHT);

        String resourceLabel = displayOr(state.resourceName(), "РЕСУРС").toUpperCase(Locale.ROOT);
        String resourceValue = state.resourceMax() > 0
                ? NUMBER_FORMAT.format(state.resource()) + " / " + NUMBER_FORMAT.format(state.resourceMax())
                : NUMBER_FORMAT.format(state.resource());
        String resourceTitle = (state.resourceSymbol().isBlank() ? "" : state.resourceSymbol() + " ")
                + resourceLabel;
        graphics.text(font, resourceTitle, right, y + 5, theme.highlight());
        drawRightAligned(graphics, font, resourceValue, rightEdge, y + 5, TEXT);
        if (state.resourceMax() > 0) {
            drawBar(graphics, right, y + 17, half - 28, 5,
                    state.resource(), state.resourceMax(), theme.accent(), theme.highlight());
        } else {
            drawDecorativeRule(graphics, right, y + 19, half - 28, theme.accent());
        }

        int armor = Math.max(0, player.getArmorValue());
        FoodData food = player.getFoodData();
        int foodLevel = Math.max(0, Math.min(20, food.getFoodLevel()));
        float saturation = Math.max(0.0F, Math.min(20.0F, food.getSaturationLevel()));

        graphics.text(font, "БРОНЯ", left, y + 29, MUTED);
        drawRightAligned(graphics, font, NUMBER_FORMAT.format(armor), x + half - 8, y + 29, ARMOR);
        drawSegmentedMeter(graphics, left, y + 40, half - 28, 4, armor, 20, ARMOR);

        graphics.text(font, "СЫТОСТЬ", right, y + 29, MUTED);
        drawRightAligned(graphics, font, foodLevel + "/20", rightEdge, y + 29, HUNGER);
        drawFoodPips(graphics, right, y + 40, half - 28, foodLevel, saturation);
    }

    private static void drawCrowdControl(
            GuiGraphicsExtractor graphics,
            Font font,
            RaskolHudSnapshot state,
            int screenWidth,
            int y) {

        List<RaskolHudEffect> debuffs = new ArrayList<>();
        debuffs.addAll(state.crowdControl());
        state.activeEffects().stream().filter(RaskolHudEffect::harmful).forEach(debuffs::add);
        debuffs.addAll(state.damageOverTime());
        if (debuffs.isEmpty()) {
            return;
        }

        int visible = Math.min(9, debuffs.size());
        boolean hasOverflow = debuffs.size() > visible;
        int chipWidth = 25;
        int gap = 4;
        int count = visible + (hasOverflow ? 1 : 0);
        int totalWidth = count * chipWidth + Math.max(0, count - 1) * gap;
        int x = (screenWidth - totalWidth) / 2;
        RaskolClassTheme theme = RaskolClassTheme.forClass(state.classId());

        for (int index = 0; index < visible; index++) {
            RaskolHudEffect effect = debuffs.get(index);
            int chipX = x + index * (chipWidth + gap);
            int color = effect.harmful() ? HEALTH : theme.accent();
            graphics.fill(chipX + 1, y + 2, chipX + chipWidth + 1, y + 21, SHADOW);
            graphics.fill(chipX, y, chipX + chipWidth, y + 19, FRAME);
            graphics.fill(chipX, y, chipX + chipWidth, y + 1, color);

            String symbol = effect.symbol().isBlank() ? "!" : effect.symbol();
            graphics.text(font, symbol, chipX + (chipWidth - font.width(symbol)) / 2, y + 2, 0xFFF0E8DF);
            String timer = effect.remainingSeconds() < 0 ? "∞" : effect.remainingSeconds() + "с";
            graphics.text(font, timer, chipX + (chipWidth - font.width(timer)) / 2, y + 11, 0xFFD0C4BA);
        }

        if (hasOverflow) {
            int overflowX = x + visible * (chipWidth + gap);
            String overflow = "+" + (debuffs.size() - visible);
            graphics.fill(overflowX, y, overflowX + chipWidth, y + 19, FRAME);
            graphics.fill(overflowX, y, overflowX + chipWidth, y + 1, theme.muted());
            graphics.text(font, overflow, overflowX + (chipWidth - font.width(overflow)) / 2, y + 5, SECONDARY);
        }
    }

    private static void drawFrame(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int accent) {

        graphics.fill(x + 2, y + 3, x + width + 2, y + height + 3, SHADOW);
        graphics.fill(x, y, x + width, y + height, FRAME);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, INNER);
        graphics.fill(x + 7, y, x + width - 7, y + 1, accent);
        graphics.fill(x, y + 7, x + 1, y + height - 7, EDGE_DARK);
        graphics.fill(x + width - 1, y + 7, x + width, y + height - 7, EDGE_DARK);
        graphics.fill(x + 7, y + height - 1, x + width - 7, y + height, EDGE_DARK);
        graphics.fill(x + 8, y + 3, x + width - 8, y + 4, 0x553F2C2C);
    }

    private static void drawConnection(GuiGraphicsExtractor graphics, Font font, int screenWidth) {
        String label = "РАСКОЛ";
        int x = screenWidth - font.width(label) - 22;
        graphics.fill(x, 21, x + 5, 26, 0xFFB58A62);
        graphics.fill(x + 1, 22, x + 4, 25, 0xFFFFD9A0);
        graphics.text(font, label, x + 10, 18, SECONDARY);
    }

    private static void drawBar(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            int current, int maximum, int fillColor, int highlightColor) {
        if (width <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, TRACK);
        if (maximum <= 0 || current <= 0) {
            return;
        }
        double fraction = Math.max(0.0, Math.min(1.0, (double) current / maximum));
        int filledWidth = (int) Math.round(width * fraction);
        if (filledWidth > 0) {
            graphics.fill(x, y, x + filledWidth, y + height, fillColor);
            graphics.fill(x, y, x + filledWidth, y + 1, highlightColor);
        }
    }

    private static void drawSegmentedMeter(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            int current, int maximum, int color) {
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
            double segmentFill = Math.max(0.0, Math.min(1.0,
                    (clamped - i * maximum / (double) segments) / (maximum / (double) segments)));
            int fillWidth = (int) Math.round(segmentWidth * segmentFill);
            if (fillWidth > 0) {
                graphics.fill(segmentX, y, segmentX + fillWidth, y + height, color);
            }
        }
    }

    private static void drawFoodPips(
            GuiGraphicsExtractor graphics, int x, int y, int width, int foodLevel, float saturation) {
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
        int saturationWidth = (int) Math.round(width * Math.max(0.0F, Math.min(1.0F, saturation / 20.0F)));
        if (saturationWidth > 0) {
            graphics.fill(x, y - 1, x + saturationWidth, y, 0xFFE4C08B);
        }
    }

    private static void drawDecorativeRule(
            GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
        if (width <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + 1, TRACK);
        graphics.fill(x, y, x + Math.min(14, width), y + 1, color);
        graphics.fill(x + width - Math.min(14, width), y, x + width, y + 1, color);
    }

    private static void drawRightAligned(
            GuiGraphicsExtractor graphics, Font font, String text, int right, int y, int color) {
        graphics.text(font, text, right - font.width(text), y, color);
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (text == null || maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String candidate = text;
        while (!candidate.isEmpty() && font.width(candidate + "…") > maxWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate.isEmpty() ? "" : candidate + "…";
    }

    private static String displayOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
