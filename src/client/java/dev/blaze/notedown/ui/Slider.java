package dev.blaze.notedown.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;

public final class Slider extends AbstractWidget {

    private static final int TRACK_W = 90;
    private static final int TRACK_H = 10;
    private static final int THUMB_W = 4;

    private final float min;
    private final float max;
    private final float step;
    private final Function<Float, String> format;
    private final Consumer<Float> onChange;
    private float value;
    private boolean dragging;

    public Slider(int x, int y, int w, Component label, float min, float max, float step, float value,
                  Function<Float, String> format, Consumer<Float> onChange) {
        super(x, y, w, Theme.BUTTON_HEIGHT, label);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = value;
        this.format = format;
        this.onChange = onChange;
    }

    private int trackX() {
        return getX() + getWidth() - Theme.PAD - TRACK_W;
    }

    private void set(float v) {
        float snapped = Math.round(v / step) * step;
        snapped = Math.max(min, Math.min(max, snapped));
        if (snapped != value) {
            value = snapped;
            onChange.accept(value);
        }
    }

    private void setFromMouse(double mx) {
        double f = (mx - trackX() - THUMB_W / 2.0) / (TRACK_W - THUMB_W);
        set(min + (float) Math.max(0, Math.min(1, f)) * (max - min));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x1 = getX();
        int y1 = getY();
        if (isHoveredOrFocused()) {
            g.fill(x1, y1, x1 + getWidth(), y1 + getHeight(), Theme.HIGHLIGHT);
        }
        Font font = Minecraft.getInstance().font;
        int ty = y1 + (getHeight() - 8) / 2;
        g.text(font, getMessage(), x1 + Theme.PAD, ty, active ? Theme.TEXT : Theme.TEXT_DISABLED);
        int tx = trackX();
        int tyTrack = y1 + (getHeight() - TRACK_H) / 2;
        g.fill(tx, tyTrack, tx + TRACK_W, tyTrack + TRACK_H, Theme.PANEL_LIGHT);
        int thumbX = tx + Math.round((value - min) / (max - min) * (TRACK_W - THUMB_W));
        g.fill(thumbX, tyTrack, thumbX + THUMB_W, tyTrack + TRACK_H, dragging || isFocused() ? Theme.ACCENT : Theme.TEXT_MUTED);
        String label = format.apply(value);
        g.text(font, label, tx - 6 - font.width(label), ty, Theme.TEXT_MUTED);
    }

    @Override
    public void onClick(MouseButtonEvent e, boolean doubleClick) {
        if (e.x() < trackX() || e.x() >= trackX() + TRACK_W) {
            return;
        }
        dragging = true;
        setFromMouse(e.x());
    }

    @Override
    protected void onDrag(MouseButtonEvent e, double dx, double dy) {
        if (dragging) {
            setFromMouse(e.x());
        }
    }

    @Override
    public void onRelease(MouseButtonEvent e) {
        dragging = false;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (!isFocused()) {
            return false;
        }
        if (e.isLeft()) {
            set(value - step);
            return true;
        }
        if (e.isRight()) {
            set(value + step);
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
