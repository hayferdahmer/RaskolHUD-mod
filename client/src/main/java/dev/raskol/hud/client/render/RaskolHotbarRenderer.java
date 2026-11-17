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

        // Общая опорная пластина объединяет девять слотов в одну деталь интерфейса.
        RaskolHudFrameRenderer.hotbarPlate(graphics, left - 9, top - 9, right - left + 18, 36,
                theme.accent(), 100);

        // Девять ячеек оформлены тем же готическим металлом, что и панели HUD.
        float selectionPulse = RaskolHudMotion.selectedSlotPulse(client.player.getInventory().getSelectedSlot());
        for (int slot = 0; slot < 9; slot++) {
            int slotX = left + slot * 20;
            boolean selected = slot == client.player.getInventory().getSelectedSlot();
            RaskolHudFrameRenderer.slot(graphics, slotX, top, 20, theme.accent(), selected, 100);
            if (selected && selectionPulse > 0.0F) {
                int pulseAlpha = (int) (90.0F * selectionPulse);
                graphics.fill(slotX + 2, top, slotX + 18, top + 1, withAlpha(theme.highlight(), pulseAlpha));
                graphics.fill(slotX + 2, top + 19, slotX + 18, top + 20, withAlpha(theme.highlight(), pulseAlpha));
            }
        }

        // Золотистый орнамент взят из выбранного фэнтези-концепта №4.
        // Он расположен над предметами и не перекрывает содержимое слотов.
        RaskolHudTextureRenderer.renderFantasyHotbarOrnament(graphics, left - 9, top - 16, 200, 14, 100);

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
