package dev.raskol.hud.client.render;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.raskol.hud.client.config.HudConfig;
import dev.raskol.hud.client.network.HudStateStore;
import dev.raskol.hud.protocol.RaskolHudEffect;
import dev.raskol.hud.protocol.RaskolHudCharacterStats;
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

    private static int activeOpacity = 100;
    private static final int SHADOW = 0x45000000;
    private static final int FRAME = 0x98100D12;
    private static final int INNER = 0x9D19151A;
    private static final int EDGE_DARK = 0x9B38262B;
    private static final int TEXT = 0xFFF0E8DF;
    private static final int SECONDARY = 0xFFD0C2B8;
    private static final int MUTED = 0xC08F817D;
    private static final int HEALTH = 0xFF873747;
    private static final int HEALTH_HIGHLIGHT = 0xFFB35C68;
    private static final int ARMOR = 0xFF8E9AA8;
    private static final int HUNGER = 0xFFC18A4E;
    private static final int TRACK = 0xB221191E;
    private static final int DIVIDER = 0x70493238;

    private static final int IDENTITY_X = 10;
    private static final int IDENTITY_Y = 10;
    private static final int IDENTITY_WIDTH = 226;
    private static final int IDENTITY_HEIGHT = 42;
    private static final int STATUS_WIDTH = 216;
    private static final int STATS_WIDTH = 154;
    private static final int STATUS_HEIGHT = 38;
    private static final int HOTBAR_GAP = 22;

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
        double scale = HudConfig.scale();
        float centerX = graphics.guiWidth() / 2.0F;
        float centerY = graphics.guiHeight() / 2.0F;
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) (centerX * (1.0D - scale)), (float) (centerY * (1.0D - scale)));
        graphics.pose().scale((float) scale, (float) scale);
        if (HudConfig.showIdentity()) {
            drawAtOffset(graphics, HudConfig.identityX(), HudConfig.identityY(), HudConfig.opacity("identity"),
                    () -> drawIdentity(graphics, font, player, state));
        }
        if (HudConfig.showConnection()) {
            drawAtOffset(graphics, HudConfig.connectionX(), HudConfig.connectionY(), HudConfig.opacity("connection"),
                    () -> drawConnection(graphics, font, graphics.guiWidth()));
        }
        if (HudConfig.showStatus()) {
            drawAtOffset(graphics, HudConfig.statusX(), HudConfig.statusY(), HudConfig.opacity("status"),
                    () -> drawBottomStatus(graphics, font, player, state, graphics.guiWidth(), graphics.guiHeight()));
        }
        if (HudConfig.showEffects()) {
            drawAtOffset(graphics, HudConfig.effectsX(), HudConfig.effectsY(), HudConfig.opacity("effects"),
                    () -> drawCrowdControl(graphics, font, state, graphics.guiWidth(),
                            graphics.guiHeight() - HOTBAR_GAP - STATUS_HEIGHT - 19));
        }
        if (HudConfig.showCombatStats() && state.characterStats().available()) {
            drawAtOffset(graphics, HudConfig.combatStatsX(), HudConfig.combatStatsY(), HudConfig.opacity("combatStats"),
                    () -> drawCombatStats(graphics, font, state.characterStats(), state.classId()));
        }
        if (HudConfig.showScoreboard()) {
            RaskolScoreboardRenderer.render(graphics, font, client, state);
        }
        graphics.pose().popMatrix();
    }

    private static void drawAtOffset(GuiGraphicsExtractor graphics, int x, int y, int opacity, Runnable render) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        int previousOpacity = activeOpacity;
        activeOpacity = opacity;
        render.run();
        activeOpacity = previousOpacity;
        graphics.pose().popMatrix();
    }

    private static int applyOpacity(int color) {
        int alpha = (color >>> 24) * activeOpacity / 100;
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static void fill(
            GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y2, applyOpacity(color));
    }

    private static void text(
            GuiGraphicsExtractor graphics, Font font, String value, int x, int y, int color) {
        graphics.text(font, value, x, y, applyOpacity(color));
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

        drawFrame(graphics, x, y, IDENTITY_WIDTH, IDENTITY_HEIGHT, withAlpha(theme.accent(), 190));
        drawPlayerHead(graphics, player, x + 8, y + 8, theme);

        String nickname = fit(font, player.getName().getString(), 132);
        text(graphics, font, nickname, x + 42, y + 7, TEXT);
        drawRightAligned(
                graphics,
                font,
                "УР. " + NUMBER_FORMAT.format(state.characterLevel()),
                right - 8,
                y + 7,
                theme.highlight());

        String className = displayOr(state.className(), "Без класса");
        String specialization = state.specializationName();
        text(graphics, font, fit(font, className, 92), x + 42, y + 23, theme.highlight());
        if (!specialization.isBlank()) {
            String specText = " (" + specialization + ")";
            int specX = x + 42 + font.width(className);
            int available = right - 8 - specX;
            text(graphics, font, fit(font, specText, Math.max(0, available)), specX, y + 23, SECONDARY);
        }
    }

    private static void drawCombatStats(
            GuiGraphicsExtractor graphics,
            Font font,
            RaskolHudCharacterStats stats,
            String classId) {
        int x = 10;
        int y = 60;
        int width = STATS_WIDTH;
        int height = 94;
        RaskolClassTheme theme = RaskolClassTheme.forClass(classId);
        drawFrame(graphics, x, y, width, height, withAlpha(theme.accent(), 170));
        text(graphics, font, "БОЕВЫЕ ХАРАКТЕРИСТИКИ", x + 7, y + 6, theme.highlight());
        fill(graphics, x + 7, y + 19, x + width - 7, y + 20, DIVIDER);
        drawStatLine(graphics, font, "Физ. сопротивление", stats.physicalResistancePct(), x + 7, y + 25);
        drawStatLine(graphics, font, "Маг. сопротивление", stats.magicResistancePct(), x + 7, y + 36);
        drawStatLine(graphics, font, "Уклонение", stats.dodgePct(), x + 7, y + 47);
        drawStatLine(graphics, font, "Парирование", stats.parryPct(), x + 7, y + 58);
        drawStatLine(graphics, font, "Крит. ближний бой", stats.meleeCritPct(), x + 7, y + 69);
        drawStatLine(graphics, font, "Крит. заклинания", stats.spellCritPct(), x + 7, y + 80);
        if (stats.earnedTalentPoints() > 0 || stats.availableTalentPoints() > 0) {
            String talent = "Таланты: " + stats.spentTalentPoints() + "/" + stats.earnedTalentPoints()
                    + " (+" + stats.availableTalentPoints() + ")";
            text(graphics, font, fit(font, talent, width - 14), x + 7, y + height + 3, SECONDARY);
        }
    }

    private static void drawStatLine(
            GuiGraphicsExtractor graphics, Font font, String label, double value, int x, int y) {
        text(graphics, font, label, x, y, SECONDARY);
        String formatted = String.format(Locale.ROOT, "%.1f%%", value);
        drawRightAligned(graphics, font, formatted, x + STATS_WIDTH - 14, y, TEXT);
    }

    private static void drawPlayerHead(
            GuiGraphicsExtractor graphics,
            Player player,
            int x,
            int y,
            RaskolClassTheme theme) {

        fill(graphics, x - 2, y - 2, x + 26, y + 26, SHADOW);
        fill(graphics, x - 1, y - 1, x + 25, y + 25, withAlpha(theme.muted(), 210));
        fill(graphics, x, y, x + 24, y + 24, 0xFF171319);

        if (player instanceof AbstractClientPlayer clientPlayer) {
            var texture = clientPlayer.getSkin().body().texturePath();
            // Лицо и верхний слой берутся из актуальной текстуры скина игрока.
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                    x, y, 8.0F, 8.0F, 24, 24, 8, 8, 64, 64, applyOpacity(0xFFFFFFFF));
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                    x, y, 40.0F, 8.0F, 24, 24, 8, 8, 64, 64, applyOpacity(0xFFFFFFFF));
        } else {
            fill(graphics, x + 7, y + 7, x + 17, y + 17, theme.accent());
        }

        fill(graphics, x - 1, y - 1, x + 25, y, withAlpha(theme.highlight(), 200));
        fill(graphics, x - 1, y + 24, x + 25, y + 25, EDGE_DARK);
    }

    private static void drawBottomStatus(
            GuiGraphicsExtractor graphics,
            Font font,
            Player player,
            RaskolHudSnapshot state,
            int screenWidth,
            int screenHeight) {

        int width = Math.max(1, Math.min(STATUS_WIDTH, screenWidth - 12));
        int x = (screenWidth - width) / 2;
        int y = screenHeight - HOTBAR_GAP - STATUS_HEIGHT;
        int half = width / 2;
        int left = x + 7;
        int right = x + half + 5;
        int leftEdge = x + half - 7;
        int rightEdge = x + width - 7;
        int columnWidth = half - 14;
        RaskolClassTheme theme = RaskolClassTheme.forClass(state.classId());

        drawFrame(graphics, x, y, width, STATUS_HEIGHT, withAlpha(theme.accent(), 190));
        fill(graphics, x + half, y + 6, x + half + 1, y + STATUS_HEIGHT - 6, DIVIDER);

        String healthValue = NUMBER_FORMAT.format(state.health()) + "/" + NUMBER_FORMAT.format(state.healthMax());
        text(graphics, font, "ЗДОРОВЬЕ", left, y + 4, MUTED);
        drawRightAligned(graphics, font, fit(font, healthValue, columnWidth - font.width("ЗДОРОВЬЕ")),
                leftEdge, y + 4, TEXT);
        drawBar(graphics, left, y + 16, columnWidth, 4,
                state.health(), state.healthMax(), withAlpha(HEALTH, 220), HEALTH_HIGHLIGHT);

        String resourceLabel = displayOr(state.resourceName(), "РЕСУРС").toUpperCase(Locale.ROOT);
        String resourceValue = state.resourceMax() > 0
                ? NUMBER_FORMAT.format(state.resource()) + "/" + NUMBER_FORMAT.format(state.resourceMax())
                : NUMBER_FORMAT.format(state.resource());
        String resourceTitle = (state.resourceSymbol().isBlank() ? "" : state.resourceSymbol() + " ")
                + resourceLabel;
        text(graphics, font, fit(font, resourceTitle, columnWidth - font.width(resourceValue) - 4),
                right, y + 4, theme.highlight());
        drawRightAligned(graphics, font, resourceValue, rightEdge, y + 4, TEXT);
        if (state.resourceMax() > 0) {
            drawBar(graphics, right, y + 16, columnWidth, 4,
                    state.resource(), state.resourceMax(), withAlpha(theme.accent(), 220), theme.highlight());
        } else {
            drawDecorativeRule(graphics, right, y + 18, columnWidth, withAlpha(theme.accent(), 210));
        }

        int armor = Math.max(0, player.getArmorValue());
        FoodData food = player.getFoodData();
        int foodLevel = Math.max(0, Math.min(20, food.getFoodLevel()));

        text(graphics, font, "БРОНЯ " + armor, left, y + 25, ARMOR);
        drawBar(graphics, left, y + 34, columnWidth, 2, armor, 20,
                withAlpha(ARMOR, 200), withAlpha(ARMOR, 200));

        text(graphics, font, "ЕДА " + foodLevel + "/20", right, y + 25, HUNGER);
        drawBar(graphics, right, y + 34, columnWidth, 2, foodLevel, 20,
                withAlpha(HUNGER, 205), withAlpha(HUNGER, 205));
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

        int visible = Math.min(6, debuffs.size());
        boolean hasOverflow = debuffs.size() > visible;
        int chipWidth = 22;
        int gap = 3;
        int count = visible + (hasOverflow ? 1 : 0);
        int totalWidth = count * chipWidth + Math.max(0, count - 1) * gap;
        int x = (screenWidth - totalWidth) / 2;
        RaskolClassTheme theme = RaskolClassTheme.forClass(state.classId());

        for (int index = 0; index < visible; index++) {
            RaskolHudEffect effect = debuffs.get(index);
            int chipX = x + index * (chipWidth + gap);
            int color = effect.harmful() ? HEALTH : theme.accent();

            fill(graphics, chipX + 1, y + 2, chipX + chipWidth + 1, y + 17, SHADOW);
            fill(graphics, chipX, y, chipX + chipWidth, y + 15, 0x90100D12);
            fill(graphics, chipX, y, chipX + chipWidth, y + 1, withAlpha(color, 210));

            String symbol = effect.symbol().isBlank() ? "!" : effect.symbol();
            text(graphics, font, symbol, chipX + (chipWidth - font.width(symbol)) / 2, y + 1, 0xFFF0E8DF);
            String timer = effect.remainingSeconds() < 0 ? "∞" : effect.remainingSeconds() + "с";
            text(graphics, font, timer, chipX + (chipWidth - font.width(timer)) / 2, y + 8, SECONDARY);
        }

        if (hasOverflow) {
            int overflowX = x + visible * (chipWidth + gap);
            String overflow = "+" + (debuffs.size() - visible);
            fill(graphics, overflowX, y, overflowX + chipWidth, y + 15, 0x90100D12);
            fill(graphics, overflowX, y, overflowX + chipWidth, y + 1, withAlpha(theme.muted(), 210));
            text(graphics, font, overflow, overflowX + (chipWidth - font.width(overflow)) / 2,
                    y + 4, SECONDARY);
        }
    }

    private static void drawFrame(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int accent) {

        fill(graphics, x + 2, y + 2, x + width + 2, y + height + 2, SHADOW);
        fill(graphics, x, y, x + width, y + height, FRAME);
        fill(graphics, x + 1, y + 1, x + width - 1, y + height - 1, INNER);
        fill(graphics, x + 7, y, x + width - 7, y + 1, accent);
        fill(graphics, x, y + 7, x + 1, y + height - 7, EDGE_DARK);
        fill(graphics, x + width - 1, y + 7, x + width, y + height - 7, EDGE_DARK);
        fill(graphics, x + 7, y + height - 1, x + width - 7, y + height, EDGE_DARK);
    }

    private static void drawConnection(GuiGraphicsExtractor graphics, Font font, int screenWidth) {
        String label = "РАСКОЛ";
        int x = screenWidth - font.width(label) - 20;
        fill(graphics, x, 20, x + 4, 24, 0xFFB58A62);
        fill(graphics, x + 1, 21, x + 3, 23, 0xFFFFD9A0);
        text(graphics, font, label, x + 8, 17, SECONDARY);
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

        if (width <= 0 || height <= 0) {
            return;
        }
        fill(graphics, x, y, x + width, y + height, TRACK);
        if (maximum <= 0 || current <= 0) {
            return;
        }
        double fraction = Math.max(0.0, Math.min(1.0, (double) current / maximum));
        int filledWidth = (int) Math.round(width * fraction);
        if (filledWidth > 0) {
            fill(graphics, x, y, x + filledWidth, y + height, fillColor);
            fill(graphics, x, y, x + filledWidth, y + 1, highlightColor);
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
        fill(graphics, x, y, x + width, y + 1, TRACK);
        fill(graphics, x, y, x + Math.min(10, width), y + 1, color);
        fill(graphics, x + width - Math.min(10, width), y, x + width, y + 1, color);
    }

    private static void drawRightAligned(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int right,
            int y,
            int color) {

        text(graphics, font, text, right - font.width(text), y, color);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
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
