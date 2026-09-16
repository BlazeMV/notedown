package dev.blaze.notedown.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public class FlatButton extends AbstractButton {

    private final Runnable action;
    private boolean leftAlign;
    private boolean selected;

    public FlatButton(int x, int y, int w, int h, Component label, Runnable action) {
        super(x, y, w, h, label);
        this.action = action;
    }

    public FlatButton leftAlign(boolean value) {
        leftAlign = value;
        return this;
    }

    public FlatButton selected(boolean value) {
        selected = value;
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + getWidth();
        int y2 = y1 + getHeight();
        int bg = !active ? Theme.PANEL_LIGHT : isHoveredOrFocused() ? Theme.PANEL_HOVER : Theme.PANEL;
        g.fill(x1, y1, x2, y2, bg);
        Font font = Minecraft.getInstance().font;
        int color = active ? Theme.TEXT : Theme.TEXT_DISABLED;
        int ty = y1 + (getHeight() - 8) / 2;
        if (leftAlign) {
            g.text(font, getMessage(), x1 + Theme.PAD, ty, color);
        } else {
            g.centeredText(font, getMessage(), x1 + getWidth() / 2, ty, color);
        }
        if (selected) {
            g.fill(x1, y2 - 1, x2, y2, Theme.ACCENT);
        }
        if (isFocused() && active) {
            Theme.border(g, x1, y1, x2, y2, Theme.BORDER);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
