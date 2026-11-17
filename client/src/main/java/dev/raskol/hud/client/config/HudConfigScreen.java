package dev.raskol.hud.client.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Экран персонализации интерфейса РАСКОЛ. */
public final class HudConfigScreen extends Screen {
    private static final String[] MOVABLE_PANELS = {"identity", "status", "effects", "connection", "combatStats", "target", "scoreboard"};
    private static final String[] MOVABLE_LABELS = {"raskolhud.config.panel.identity", "raskolhud.config.panel.status", "raskolhud.config.panel.effects", "raskolhud.config.panel.connection", "raskolhud.config.panel.stats", "raskolhud.config.panel.target", "raskolhud.config.panel.scoreboard"};
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 8;
    private static final int COLUMN_GAP = 12;

    private final Screen parent;
    private int selectedPanel;
    private Button opacityButton;

    public HudConfigScreen(Screen parent) {
        super(Component.translatable("raskolhud.config.title"));
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

        addToggle(left, top + row * rowHeight, buttonWidth, "raskolhud.config.toggle.identity", HudConfig::showIdentity, HudConfig::toggleIdentity);
        addToggle(right, top + row * rowHeight, buttonWidth, "raskolhud.config.toggle.status", HudConfig::showStatus, HudConfig::toggleStatus);
        row++;
        addToggle(left, top + row * rowHeight, buttonWidth, "raskolhud.config.toggle.effects", HudConfig::showEffects, HudConfig::toggleEffects);
        addToggle(right, top + row * rowHeight, buttonWidth, "raskolhud.config.toggle.connection", HudConfig::showConnection, HudConfig::toggleConnection);
        row++;
        addToggle(left, top + row * rowHeight, buttonWidth, "raskolhud.config.toggle.scoreboard", HudConfig::showScoreboard, HudConfig::toggleScoreboard);
        addToggle(right, top + row * rowHeight, buttonWidth, "raskolhud.config.toggle.hotbar", HudConfig::showHotbarFrame, HudConfig::toggleHotbarFrame);
        row++;
        addToggle(left, top + row * rowHeight, buttonWidth, "raskolhud.config.toggle.crosshair", HudConfig::showClassCrosshair, HudConfig::toggleClassCrosshair);
        addRenderableWidget(Button.builder(scaleLabel(), button -> {
            HudConfig.cycleScale();
            button.setMessage(scaleLabel());
        }).bounds(right, top + row * rowHeight, buttonWidth, BUTTON_HEIGHT).build());
        row++;
        addToggle(left, top + row * rowHeight, buttonWidth, "raskolhud.config.toggle.stats", HudConfig::showCombatStats, HudConfig::toggleCombatStats);
        addToggle(right, top + row * rowHeight, buttonWidth, "raskolhud.config.toggle.target", HudConfig::showTarget, HudConfig::toggleTarget);
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
        addRenderableWidget(Button.builder(Component.translatable("raskolhud.config.reset_position"),
                button -> HudConfig.resetPosition(selectedKey()))
                .bounds(left, footerY, thirdWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("raskolhud.config.reset_all"), button -> {
            HudConfig.reset();
            selectedPanel = 0;
            clearWidgets();
            init();
        }).bounds(left + thirdWidth + thirdGap, footerY, thirdWidth, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("raskolhud.config.done"), button -> onClose())
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
        return Component.translatable("raskolhud.config.selected_panel", Component.translatable(MOVABLE_LABELS[selectedPanel]));
    }

    private Component opacityLabel() {
        return Component.translatable("raskolhud.config.opacity", HudConfig.opacity(selectedKey()));
    }

    private static Component scaleLabel() {
        return Component.translatable("raskolhud.config.scale", Math.round(HudConfig.scale() * 100.0D));
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
        return Component.translatable("raskolhud.config.toggle_state", Component.translatable(label),
                Component.translatable(enabled ? "raskolhud.config.enabled" : "raskolhud.config.disabled"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, Component.translatable("raskolhud.config.brand"), this.width / 2, 22, 0xFFE1B66F);
        graphics.centeredText(this.font, Component.translatable("raskolhud.config.heading"), this.width / 2, 34, 0xFFD0C2B8);
        graphics.centeredText(this.font, Component.translatable("raskolhud.config.hint"),
                this.width / 2, this.height - 42, 0xFFB5A69B);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
