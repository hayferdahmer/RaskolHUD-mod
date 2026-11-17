package dev.raskol.hud.client.render;

import java.util.HashMap;
import java.util.Map;

import dev.raskol.hud.client.config.HudConfig;
import dev.raskol.hud.protocol.RaskolHudCooldown;
import dev.raskol.hud.protocol.RaskolHudSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Визуальная рамка хотбара и WoW-подобные цифровые таймеры способностей. */
public final class RaskolHotbarRenderer {

    private RaskolHotbarRenderer() {}

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    public static void render(GuiGraphicsExtractor graphics, RaskolHudSnapshot state) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        float scale = (float) HudConfig.scale();
        float centerXFloat = graphics.guiWidth() / 2.0F;
        float centerYFloat = graphics.guiHeight() / 2.0F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerXFloat * (1.0F - scale), centerYFloat * (1.0F - scale));
        graphics.pose().scale(scale, scale);
        int centerX = graphics.guiWidth() / 2;
        int top = graphics.guiHeight() - 22;
        int left = centerX - 91;
        int right = centerX + 91;
        RaskolClassTheme theme = RaskolClassTheme.forClass(state.classId());

        // Ванильные иконки сохраняются; над ними рисуем рамку и классовую подсветку.
        graphics.fill(left - 2, top - 3, right + 2, top - 2, 0x97100D12);
        graphics.fill(left - 2, top - 3, left - 1, top + 22, theme.muted());
        graphics.fill(right + 1, top - 3, right + 2, top + 22, theme.muted());
        graphics.fill(left - 2, top + 21, right + 2, top + 22, 0xA2100D12);
        graphics.fill(left + 5, top - 3, right - 5, top - 2, theme.accent());

        int selectedSlot = client.player.getInventory().getSelectedSlot();
        int selectedX = left + selectedSlot * 20;
        graphics.fill(selectedX, top - 1, selectedX + 20, top, withAlpha(theme.highlight(), 190));
        graphics.fill(selectedX, top + 19, selectedX + 20, top + 20, withAlpha(theme.accent(), 185));
        graphics.fill(selectedX, top - 1, selectedX + 1, top + 20, withAlpha(theme.muted(), 165));
        graphics.fill(selectedX + 19, top - 1, selectedX + 20, top + 20, withAlpha(theme.muted(), 165));

        Font font = client.font;
        Map<Integer, RaskolHudCooldown> cooldowns = new HashMap<>();
        for (RaskolHudCooldown cooldown : state.cooldowns()) {
            cooldowns.put(cooldown.slot(), cooldown);
        }

        for (Map.Entry<Integer, RaskolHudCooldown> entry : cooldowns.entrySet()) {
            int slot = entry.getKey();
            RaskolHudCooldown cooldown = entry.getValue();
            int iconX = left + 3 + slot * 20;
            int iconY = top + 3;

            // Полупрозрачный слой закрывает ванильную полоску прочности/КД.
            graphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0x82000000);
            graphics.fill(iconX, iconY + 15, iconX + 16, iconY + 16, withAlpha(theme.muted(), 190));

            int seconds = (int) Math.ceil(cooldown.remainingMillis() / 1000.0);
            String label = seconds > 99 ? "99+" : Integer.toString(seconds);
            int textX = iconX + (16 - font.width(label)) / 2;
            int textY = iconY + 4;
            graphics.text(font, label, textX, textY, 0xFFF5EDE4);
        }
        graphics.pose().popMatrix();
    }
}
