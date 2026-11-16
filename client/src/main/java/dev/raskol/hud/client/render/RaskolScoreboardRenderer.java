package dev.raskol.hud.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

/**
 * Собственный рендер боковой таблицы сервера.
 *
 * Значения читаются из уже синхронизированного scoreboard Minecraft:
 * мод не создаёт и не пересчитывает серверные очки.
 */
public final class RaskolScoreboardRenderer {

    private static final int PANEL_WIDTH = 196;
    private static final int PANEL_RIGHT_MARGIN = 12;
    private static final int PANEL_TOP = 56;
    private static final int PANEL_PADDING = 9;
    private static final int ROW_HEIGHT = 11;
    private static final int MAX_ROWS = 12;

    private static final int SHADOW = 0x70000000;
    private static final int PANEL = 0xE6110D12;
    private static final int INNER = 0xC91B151A;
    private static final int EDGE = 0xFF8E663F;
    private static final int EDGE_DARK = 0xFF3A2929;
    private static final int TITLE = 0xFFE1B66F;
    private static final int TEXT = 0xFFE8DED3;
    private static final int VALUE = 0xFFD2A76C;
    private static final int MUTED = 0xFF8F817D;
    private static final int DIVIDER = 0xFF493238;

    private RaskolScoreboardRenderer() {}

    public static void render(
            GuiGraphicsExtractor graphics,
            Font font,
            Minecraft client) {

        if (client.level == null || client.player == null) {
            return;
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return;
        }

        List<PlayerScoreEntry> rows = new ArrayList<>(scoreboard.listPlayerScores(objective));
        rows.removeIf(PlayerScoreEntry::isHidden);
        rows.sort(
                Comparator.comparingInt(PlayerScoreEntry::value)
                        .reversed()
                        .thenComparing(PlayerScoreEntry::owner));

        if (rows.size() > MAX_ROWS) {
            rows = new ArrayList<>(rows.subList(0, MAX_ROWS));
        }

        Component title = objective.getDisplayName();
        int longestText = font.width(title);
        int longestValue = 0;

        for (PlayerScoreEntry row : rows) {
            Component label = row.display() != null ? row.display() : row.ownerName();
            longestText = Math.max(longestText, font.width(label));
            longestValue = Math.max(longestValue, font.width(row.formatValue(objective.numberFormat())));
        }

        int contentWidth = Math.min(
                PANEL_WIDTH - PANEL_PADDING * 2 - 8,
                longestText + longestValue + 12);
        int width = Math.max(142, contentWidth + PANEL_PADDING * 2 + 8);
        int height = 25 + rows.size() * ROW_HEIGHT + 7;
        int x = graphics.guiWidth() - PANEL_RIGHT_MARGIN - width;
        int y = PANEL_TOP;

        drawPanel(graphics, x, y, width, height);
        graphics.centeredText(font, title, x + width / 2, y + 7, TITLE);
        graphics.fill(x + PANEL_PADDING, y + 21, x + width - PANEL_PADDING, y + 22, DIVIDER);

        int rowY = y + 27;
        for (PlayerScoreEntry row : rows) {
            Component label = row.display() != null ? row.display() : row.ownerName();
            Component score = row.formatValue(objective.numberFormat());

            graphics.text(font, label, x + PANEL_PADDING, rowY, TEXT);

            int scoreWidth = font.width(score);
            graphics.text(
                    font,
                    score,
                    x + width - PANEL_PADDING - scoreWidth,
                    rowY,
                    VALUE);

            rowY += ROW_HEIGHT;
        }
    }

    private static void drawPanel(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height) {

        graphics.fill(x + 2, y + 3, x + width + 2, y + height + 3, SHADOW);
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, INNER);

        graphics.fill(x + 7, y, x + width - 7, y + 1, EDGE);
        graphics.fill(x, y + 7, x + 1, y + height - 7, EDGE_DARK);
        graphics.fill(x + width - 1, y + 7, x + width, y + height - 7, EDGE_DARK);
        graphics.fill(x + 7, y + height - 1, x + width - 7, y + height, EDGE_DARK);

        graphics.fill(x + 8, y + 3, x + width - 8, y + 4, 0x554D372C);
    }
}
