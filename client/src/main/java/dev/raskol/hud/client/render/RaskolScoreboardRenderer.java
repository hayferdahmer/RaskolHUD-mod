package dev.raskol.hud.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.raskol.hud.protocol.RaskolHudSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

/**
 * Компактная панель сведений о персонаже и только уникальных строк scoreboard.
 *
 * Пустые и дублирующие строки не занимают место. Фракция и титул берутся
 * из серверного снимка RaskolClasses, а не вычисляются на клиенте.
 */
public final class RaskolScoreboardRenderer {

    private static final int PANEL_WIDTH = 158;
    private static final int PANEL_RIGHT_MARGIN = 10;
    private static final int PANEL_TOP = 60;
    private static final int PANEL_PADDING = 7;
    private static final int ROW_HEIGHT = 10;
    private static final int MAX_ROWS = 3;

    private static final int SHADOW = 0x36000000;
    private static final int PANEL = 0xA1110D12;
    private static final int INNER = 0x8D1B151A;
    private static final int EDGE = 0xA08E663F;
    private static final int EDGE_DARK = 0x843A2929;
    private static final int TITLE = 0xFFE1B66F;
    private static final int TEXT = 0xE8E8DED3;
    private static final int VALUE = 0xFFD2A76C;
    private static final int DIVIDER = 0x70493238;

    private static final Set<String> DUPLICATE_LABELS = Set.of(
            "здоровье", "health", "hp", "ресурс", "resource", "мана", "mana",
            "ярость", "энергия", "концентрация", "свет", "скверна",
            "класс", "class", "специализация", "spec", "уровень", "level",
            "броня", "armor", "еда", "сытость", "hunger");

    private record Row(String label, String value) {}

    private RaskolScoreboardRenderer() {}

    public static void render(
            GuiGraphicsExtractor graphics,
            Font font,
            Minecraft client,
            RaskolHudSnapshot state,
            int offsetX,
            int offsetY,
            int opacity) {

        if (client.level == null || client.player == null || state == null) {
            return;
        }

        List<Row> rows = new ArrayList<>();
        if (!state.factionName().isBlank()) {
            rows.add(new Row("ФРАКЦИЯ", state.factionName()));
        }
        if (!state.titleName().isBlank()) {
            rows.add(new Row("ТИТУЛ", state.titleName()));
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        List<PlayerScoreEntry> scoreEntries = new ArrayList<>();
        if (objective != null) {
            scoreEntries.addAll(scoreboard.listPlayerScores(objective));
            scoreEntries.removeIf(PlayerScoreEntry::isHidden);
            scoreEntries.sort(
                    Comparator.comparingInt(PlayerScoreEntry::value)
                            .reversed()
                            .thenComparing(PlayerScoreEntry::owner));
        }

        Set<String> seen = new HashSet<>();
        boolean hasContext = !state.factionName().isBlank() || !state.titleName().isBlank();
        int maxRows = MAX_ROWS + (hasContext ? rows.size() : 0);
        for (PlayerScoreEntry entry : scoreEntries) {
            if (rows.size() >= maxRows || objective == null) {
                break;
            }

            String label = (entry.display() != null ? entry.display() : entry.ownerName()).getString().trim();
            String value = entry.formatValue(objective.numberFormat()).getString().trim();
            if (label.isBlank() || value.isBlank()
                    || label.equalsIgnoreCase(client.player.getName().getString())
                    || isDuplicateLabel(label, state)) {
                continue;
            }

            String normalized = label.toLowerCase(Locale.ROOT);
            if (!seen.add(normalized)) {
                continue;
            }
            rows.add(new Row(label, value));
        }

        // Не рисуем рамку, если на сервере пока нет полезных сведений.
        if (rows.isEmpty()) {
            return;
        }

        int width = PANEL_WIDTH;
        int height = 22 + rows.size() * ROW_HEIGHT + 5;
        int x = graphics.guiWidth() - PANEL_RIGHT_MARGIN - width + offsetX;
        int y = PANEL_TOP + offsetY;

        drawPanel(graphics, x, y, width, height, opacity);
        String heading = hasContext
                ? "СВЕДЕНИЯ"
                : objective == null ? "РАСКОЛ" : fit(font, objective.getDisplayName().getString(), width - 16);
        graphics.centeredText(font, heading, x + width / 2, y + 5, applyOpacity(TITLE, opacity));
        graphics.fill(x + PANEL_PADDING, y + 17, x + width - PANEL_PADDING, y + 18, applyOpacity(DIVIDER, opacity));

        int rowY = y + 23;
        for (Row row : rows) {
            graphics.text(font, fit(font, row.label(), 58), x + PANEL_PADDING, rowY, applyOpacity(TEXT, opacity));
            String value = fit(font, row.value(), width - PANEL_PADDING * 2 - 61);
            graphics.text(font, value, x + width - PANEL_PADDING - font.width(value), rowY, applyOpacity(VALUE, opacity));
            rowY += ROW_HEIGHT;
        }
    }

    private static boolean isDuplicateLabel(String label, RaskolHudSnapshot state) {
        String normalized = label.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
        if (normalized.isBlank() || DUPLICATE_LABELS.stream().anyMatch(normalized::contains)) {
            return true;
        }
        return (!state.factionName().isBlank()
                        && (normalized.contains("фракц") || normalized.contains("корон")))
                || (!state.titleName().isBlank() && normalized.contains("титул"));
    }

    private static String fit(Font font, String value, int maxWidth) {
        if (value == null || maxWidth <= 0) {
            return "";
        }
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String candidate = value;
        while (!candidate.isEmpty() && font.width(candidate + "…") > maxWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate.isEmpty() ? "" : candidate + "…";
    }

    private static void drawPanel(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int opacity) {

        graphics.fill(x + 1, y + 2, x + width + 1, y + height + 2, applyOpacity(SHADOW, opacity));
        graphics.fill(x, y, x + width, y + height, applyOpacity(PANEL, opacity));
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, applyOpacity(INNER, opacity));
        graphics.fill(x + 6, y, x + width - 6, y + 1, applyOpacity(EDGE, opacity));
        graphics.fill(x, y + 6, x + 1, y + height - 6, applyOpacity(EDGE_DARK, opacity));
        graphics.fill(x + width - 1, y + 6, x + width, y + height - 6, applyOpacity(EDGE_DARK, opacity));
        graphics.fill(x + 6, y + height - 1, x + width - 6, y + height, applyOpacity(EDGE_DARK, opacity));
    }

    private static int applyOpacity(int color, int opacity) {
        int alpha = (color >>> 24) * Math.max(0, Math.min(100, opacity)) / 100;
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
