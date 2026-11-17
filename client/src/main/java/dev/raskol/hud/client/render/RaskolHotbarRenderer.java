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

        // Многослойная стальная рамка обрамляет ванильные иконки, не закрывая их.
        int frameTop = top - 7;
        int frameBottom = top + 25;
        graphics.fill(left - 9, frameTop + 2, right + 9, frameBottom + 2, 0x9A000000);
        graphics.fill(left - 8, frameTop, right + 8, frameBottom, 0xFF08090D);
        graphics.fill(left - 7, frameTop + 1, right + 7, frameBottom - 1, 0xFF80675D);
        graphics.fill(left - 6, frameTop + 2, right + 6, frameBottom - 2, 0xFF171218);
        graphics.fill(left - 4, frameTop + 4, right + 4, frameBottom - 4, 0xD50D0B10);
        graphics.fill(left + 4, frameTop + 2, right - 4, frameTop + 3, withAlpha(theme.highlight(), 210));
        graphics.fill(left + 4, frameBottom - 3, right - 4, frameBottom - 2, 0xFF59423C);
        graphics.fill(left - 8, frameTop + 7, left - 7, frameBottom - 7, withAlpha(theme.accent(), 230));
        graphics.fill(right + 7, frameTop + 7, right + 8, frameBottom - 7, withAlpha(theme.accent(), 230));

        // Девять отдельных ячеек с тёмной внутренней фаской и стальными разделителями.
        for (int slot = 0; slot < 9; slot++) {
            int slotX = left + slot * 20;
            graphics.fill(slotX, top - 2, slotX + 1, top + 20, 0xFF9A7B6A);
            graphics.fill(slotX + 1, top - 1, slotX + 19, top, 0xA9D0B49A);
            graphics.fill(slotX + 1, top + 19, slotX + 19, top + 20, 0xA9000000);
            graphics.fill(slotX + 19, top - 2, slotX + 20, top + 20, 0xFF08070A);
        }
        graphics.fill(left - 8, frameTop + 2, left - 3, frameTop + 7, withAlpha(theme.accent(), 240));
        graphics.fill(right + 3, frameTop + 2, right + 8, frameTop + 7, withAlpha(theme.accent(), 240));
        graphics.fill(centerX - 2, frameTop - 2, centerX + 3, frameTop + 3, withAlpha(theme.highlight(), 240));
        graphics.fill(centerX - 1, frameTop - 3, centerX + 2, frameTop + 4, withAlpha(theme.accent(), 220));

        int selectedSlot = client.player.getInventory().getSelectedSlot();
        int selectedX = left + selectedSlot * 20;
        graphics.fill(selectedX + 1, top - 1, selectedX + 19, top, withAlpha(theme.highlight(), 210));
        graphics.fill(selectedX + 1, top + 19, selectedX + 19, top + 20, withAlpha(theme.accent(), 235));
        graphics.fill(selectedX, top, selectedX + 1, top + 19, withAlpha(theme.highlight(), 210));
        graphics.fill(selectedX + 19, top, selectedX + 20, top + 19, withAlpha(theme.accent(), 210));

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
