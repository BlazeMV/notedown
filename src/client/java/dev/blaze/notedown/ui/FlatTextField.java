package dev.blaze.notedown.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class FlatTextField extends EditBox {

    private final int panelX;
    private final int panelY;
    private final int panelW;
    private final int panelH;

    /** Panel spans the given box; the vanilla (unbordered) text box sits inside it, vertically centred. */
    public FlatTextField(Font font, int x, int y, int w, int h, Component hint) {
        super(font, x + Theme.PAD, y + (h - 8) / 2, w - 2 * Theme.PAD, h - (h - 8) / 2, hint);
        panelX = x;
        panelY = y;
        panelW = w;
        panelH = h;
        setBordered(false);
        setHint(hint);
        setMaxLength(512);
        setTextColor(Theme.TEXT);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, isFocused() ? Theme.PANEL_DARK : Theme.PANEL);
        if (isFocused()) {
            Theme.border(g, panelX, panelY, panelX + panelW, panelY + panelH, Theme.BORDER);
        }
        super.extractWidgetRenderState(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return active && visible && mouseX >= panelX && mouseX < panelX + panelW && mouseY >= panelY && mouseY < panelY + panelH;
    }
}
