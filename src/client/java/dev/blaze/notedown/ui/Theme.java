package dev.blaze.notedown.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Theme {

    public static final int ACCENT = 0xFF94E4D3;
    public static final int ACCENT_DARK = 0xFF7A9E9E;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_MUTED = 0xFFBBBBBB;
    public static final int TEXT_DISABLED = 0xFFAAAAAA;
    public static final int TEXT_CODE = 0xFFDDDDDD;
    public static final int PANEL = 0x90000000;
    public static final int PANEL_DARK = 0xB0000000;
    public static final int PANEL_HOVER = 0xE0000000;
    public static final int PANEL_LIGHT = 0x40000000;
    public static final int HIGHLIGHT = 0x08FFFFFF;
    public static final int BORDER = 0x8000FFEE;

    public static final int BUTTON_HEIGHT = 20;
    public static final int MARGIN = 5;
    public static final int PAD = 8;
    public static final int ROW_HEIGHT = 24;
    public static final int SCROLLBAR = 7;

    private Theme() {}

    public static void border(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    public static void scrollbar(GuiGraphicsExtractor g, int x, int y, int trackHeight, Scroller scroller, boolean active) {
        g.fill(x, y, x + SCROLLBAR, y + trackHeight, PANEL_LIGHT);
        int thumbY = y + scroller.thumbY(trackHeight);
        g.fill(x, thumbY, x + SCROLLBAR, thumbY + scroller.thumbHeight(trackHeight), active ? ACCENT : TEXT_MUTED);
    }

    public static int withAlpha(int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static String ellipsize(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String dots = "…";
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(dots))) + dots;
    }
}
