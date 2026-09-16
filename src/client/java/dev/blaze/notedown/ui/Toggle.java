package dev.blaze.notedown.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class Toggle extends AbstractButton {

    private static final int BOX = 10;

    private final Consumer<Boolean> onChange;
    private boolean on;

    public Toggle(int x, int y, int w, Component label, boolean on, Consumer<Boolean> onChange) {
        super(x, y, w, Theme.BUTTON_HEIGHT, label);
        this.on = on;
        this.onChange = onChange;
    }

    public boolean isOn() {
        return on;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        on = !on;
        onChange.accept(on);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x1 = getX();
        int y1 = getY();
        if (isHoveredOrFocused()) {
            g.fill(x1, y1, x1 + getWidth(), y1 + getHeight(), Theme.HIGHLIGHT);
        }
        g.text(Minecraft.getInstance().font, getMessage(), x1 + Theme.PAD, y1 + (getHeight() - 8) / 2, active ? Theme.TEXT : Theme.TEXT_DISABLED);
        int bx = x1 + getWidth() - Theme.PAD - BOX;
        int by = y1 + (getHeight() - BOX) / 2;
        Theme.border(g, bx, by, bx + BOX, by + BOX, on ? Theme.ACCENT : Theme.TEXT_MUTED);
        if (on) {
            g.fill(bx + 2, by + 2, bx + BOX - 2, by + BOX - 2, Theme.ACCENT);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
