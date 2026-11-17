package dev.raskol.hud.client.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Экран персонализации интерфейса РАСКОЛ. */
public final class HudConfigScreen extends Screen {
    private final Screen parent;
    private static final int BUTTON_WIDTH = 190;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 8;

    public HudConfigScreen(Screen parent) {
        super(Component.literal("Настройки RaskolHUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int columnGap = 12;
        int totalWidth = BUTTON_WIDTH * 2 + columnGap;
        int left = (this.width - totalWidth) / 2;
        int right = left + BUTTON_WIDTH + columnGap;
        int top = Math.max(48, this.height / 2 - 92);
        int row = 0;

        addToggle(left, top + row++ * (BUTTON_HEIGHT + GAP), "Карточка персонажа", HudConfig::showIdentity, HudConfig::toggleIdentity);
        addToggle(right, top, "Здоровье и ресурс", HudConfig::showStatus, HudConfig::toggleStatus);
        addToggle(left, top + row * (BUTTON_HEIGHT + GAP), "Эффекты и отрицательные статусы", HudConfig::showEffects, HudConfig::toggleEffects);
        addToggle(right, top + row * (BUTTON_HEIGHT + GAP), "Индикатор связи", HudConfig::showConnection, HudConfig::toggleConnection);
        row++;
        addToggle(left, top + row * (BUTTON_HEIGHT + GAP), "Фракция и scoreboard", HudConfig::showScoreboard, HudConfig::toggleScoreboard);
        addToggle(right, top + row * (BUTTON_HEIGHT + GAP), "Оформление хотбара и КД", HudConfig::showHotbarFrame, HudConfig::toggleHotbarFrame);
        row++;
        addToggle(left, top + row * (BUTTON_HEIGHT + GAP), "Классовый прицел", HudConfig::showClassCrosshair, HudConfig::toggleClassCrosshair);
        addRenderableWidget(Button.builder(scaleLabel(), button -> {
            HudConfig.cycleScale();
            button.setMessage(scaleLabel());
        }).bounds(right, top + row * (BUTTON_HEIGHT + GAP), BUTTON_WIDTH, BUTTON_HEIGHT).build());
        row++;

        int footerY = Math.min(this.height - 34, top + row * (BUTTON_HEIGHT + GAP) + 8);
        addRenderableWidget(Button.builder(Component.literal("Сбросить настройки"), button -> {
            HudConfig.reset();
            clearWidgets();
            init();
        }).bounds(left, footerY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Готово"), button -> onClose())
                .bounds(right, footerY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private void addToggle(int x, int y, String label, java.util.function.BooleanSupplier value, Runnable toggle) {
        addRenderableWidget(Button.builder(toggleLabel(label, value.getAsBoolean()), button -> {
            toggle.run();
            button.setMessage(toggleLabel(label, value.getAsBoolean()));
        }).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private static Component toggleLabel(String label, boolean enabled) {
        return Component.literal(label + ": " + (enabled ? "ВКЛ" : "ВЫКЛ"));
    }

    private static Component scaleLabel() {
        return Component.literal("Масштаб HUD: " + Math.round(HudConfig.scale() * 100.0D) + "%");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, "РАСКОЛ", this.width / 2, 22, 0xFFE1B66F);
        graphics.centeredText(this.font, "ПЕРСОНАЛИЗАЦИЯ ИНТЕРФЕЙСА", this.width / 2, 34, 0xFFD0C2B8);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
