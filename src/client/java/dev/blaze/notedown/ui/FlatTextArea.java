package dev.blaze.notedown.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.network.chat.Component;

public final class FlatTextArea extends MultiLineEditBox {

    public FlatTextArea(Font font, int x, int y, int w, int h, Component placeholder) {
        super(font, x, y, w, h, placeholder, Component.empty(), Theme.TEXT, true, Theme.TEXT, true, true);
    }

    @Override
    protected void extractBackground(GuiGraphicsExtractor g) {
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isFocused() ? Theme.PANEL_DARK : Theme.PANEL);
        extractBorder(g, getX(), getY(), getWidth(), getHeight());
    }

    @Override
    protected void extractBorder(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (isFocused()) {
            Theme.border(g, getX(), getY(), getX() + getWidth(), getY() + getHeight(), Theme.BORDER);
        }
    }

    @Override
    protected void extractScrollbar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (maxScrollAmount() <= 0) {
            return;
        }
        int x = scrollBarX();
        g.fill(x, getY(), x + Theme.SCROLLBAR, getY() + getHeight(), Theme.PANEL_LIGHT);
        g.fill(x, scrollBarY(), x + Theme.SCROLLBAR, scrollBarY() + scrollerHeight(), Theme.TEXT_MUTED);
    }

    public void insertAtCursor(String text) {
        textField.insertText(text);
    }

    public int cursor() {
        return textField.cursor();
    }

    public int selectionStart() {
        return textField.getSelected().beginIndex();
    }

    public int selectionEnd() {
        return textField.getSelected().endIndex();
    }

    public void replaceAll(String text, int cursor) {
        setValue(text);
        textField.seekCursor(Whence.ABSOLUTE, Math.max(0, Math.min(cursor, text.length())));
    }
}
