package dev.raskol.hud.client.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Экран персонализации интерфейса РАСКОЛ. */
public final class HudConfigScreen extends Screen {
    private static final String[] MOVABLE_PANELS = {"identity", "status", "effects", "connection", "combatStats"};
    private static final String[] MOVABLE_LABELS = {"Персонаж", "Здоровье / ресурс", "Эффекты", "Связь", "Характеристики"};
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 8;
    private static final int COLUMN_GAP = 12;

    private final Screen parent;
    private int selectedPanel;
    private Button opacityButton;

    public HudConfigScreen(Screen parent) {
        super(Component.literal("Настройки RaskolHUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.max(120, Math.min(190, (this.width - 32 - COLUMN_GAP) / 2));
        int totalWidth = buttonWidth * 2 + COLUMN_GAP;
        int left = (this.width - totalWidth) / 2;
        int right = left + buttonWidth + COLUMN_GAP;
        int top = Math.max(48, this.height / 2 - 104);
        int rowHeight = BUTTON_HEIGHT + GAP;
        int row = 0;

        addToggle(left, top + row * rowHeight, buttonWidth, "Персонаж", HudConfig::showIdentity, HudConfig::toggleIdentity);
        addToggle(right, top + row * rowHeight, buttonWidth, "Здоровье / ресурс", HudConfig::showStatus, HudConfig::toggleStatus);
        row++;
        addToggle(left, top + row * rowHeight, buttonWidth, "Эффекты / DoT", HudConfig::showEffects, HudConfig::toggleEffects);
        addToggle(right, top + row * rowHeight, buttonWidth, "Связь с сервером", HudConfig::showConnection, HudConfig::toggleConnection);
        row++;
        addToggle(left, top + row * rowHeight, buttonWidth, "Фракция / табло", HudConfig::showScoreboard, HudConfig::toggleScoreboard);
        addToggle(right, top + row * rowHeight, buttonWidth, "Хотбар и таймеры КД", HudConfig::showHotbarFrame, HudConfig::toggleHotbarFrame);
        row++;
        addToggle(left, top + row * rowHeight, buttonWidth, "Классовый прицел", HudConfig::showClassCrosshair, HudConfig::toggleClassCrosshair);
        addRenderableWidget(Button.builder(scaleLabel(), button -> {
            HudConfig.cycleScale();
            button.setMessage(scaleLabel());
        }).bounds(right, top + row * rowHeight, buttonWidth, BUTTON_HEIGHT).build());
        row++;
        addToggle(left, top + row * rowHeight, buttonWidth, "Боевые характеристики", HudConfig::showCombatStats, HudConfig::toggleCombatStats);
        row++;

        int editorY = top + row * rowHeight;
        addRenderableWidget(Button.builder(panelLabel(), button -> {
            selectedPanel = (selectedPanel + 1) % MOVABLE_PANELS.length;
            button.setMessage(panelLabel());
            refreshOpacityButton();
        }).bounds(left, editorY, buttonWidth, BUTTON_HEIGHT).build());
        opacityButton = Button.builder(opacityLabel(), button -> {
            HudConfig.cycleOpacity(selectedKey());
            button.setMessage(opacityLabel());
        }).bounds(right, editorY, buttonWidth, BUTTON_HEIGHT).build();
        addRenderableWidget(opacityButton);
        row++;

        int moveY = top + row * rowHeight;
        int moveWidth = (buttonWidth - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("←"), button -> nudge(-4, 0))
                .bounds(left, moveY, moveWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("→"), button -> nudge(4, 0))
                .bounds(left + moveWidth + 6, moveY, moveWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("↑"), button -> nudge(0, -4))
                .bounds(right, moveY, moveWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("↓"), button -> nudge(0, 4))
                .bounds(right + moveWidth + 6, moveY, moveWidth, BUTTON_HEIGHT).build());
        row++;

        int footerY = Math.min(this.height - 28, top + row * rowHeight + 4);
        int thirdGap = 6;
        int thirdWidth = (totalWidth - thirdGap * 2) / 3;
        addRenderableWidget(Button.builder(Component.literal("Сброс позиции"),
                button -> HudConfig.resetPosition(selectedKey()))
                .bounds(left, footerY, thirdWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Сбросить всё"), button -> {
            HudConfig.reset();
            selectedPanel = 0;
            clearWidgets();
            init();
        }).bounds(left + thirdWidth + thirdGap, footerY, thirdWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Готово"), button -> onClose())
                .bounds(left + (thirdWidth + thirdGap) * 2, footerY, thirdWidth, BUTTON_HEIGHT).build());
    }

    private void refreshOpacityButton() {
        if (opacityButton != null) {
            opacityButton.setMessage(opacityLabel());
        }
    }

    private String selectedKey() {
        return MOVABLE_PANELS[selectedPanel];
    }

    private Component panelLabel() {
        return Component.literal("Блок: " + MOVABLE_LABELS[selectedPanel]);
    }

    private Component opacityLabel() {
        return Component.literal("Прозрачность: " + HudConfig.opacity(selectedKey()) + "%");
    }

    private static Component scaleLabel() {
        return Component.literal("Масштаб: " + Math.round(HudConfig.scale() * 100.0D) + "%");
    }

    private void nudge(int dx, int dy) {
        HudConfig.move(selectedKey(), dx, dy);
    }

    private void addToggle(
            int x,
            int y,
            int width,
            String label,
            java.util.function.BooleanSupplier value,
            Runnable toggle) {
        addRenderableWidget(Button.builder(toggleLabel(label, value.getAsBoolean()), button -> {
            toggle.run();
            button.setMessage(toggleLabel(label, value.getAsBoolean()));
        }).bounds(x, y, width, BUTTON_HEIGHT).build());
    }

    private static Component toggleLabel(String label, boolean enabled) {
        return Component.literal(label + ": " + (enabled ? "ВКЛ" : "ВЫКЛ"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, "РАСКОЛ", this.width / 2, 22, 0xFFE1B66F);
        graphics.centeredText(this.font, "ПЕРСОНАЛИЗАЦИЯ ИНТЕРФЕЙСА", this.width / 2, 34, 0xFFD0C2B8);
        graphics.centeredText(this.font, "Выбери блок, затем перемещай стрелками и меняй прозрачность",
                this.width / 2, this.height - 42, 0xFFB5A69B);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
