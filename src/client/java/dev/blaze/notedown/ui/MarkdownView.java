package dev.blaze.notedown.ui;

import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.markdown.Layout;
import dev.blaze.notedown.markdown.LayoutCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class MarkdownView extends AbstractWidget {

    private static final int WHEEL_STEP = 20;

    private final LayoutCache cache;
    private final IntConsumer onToggleTask;
    private final Consumer<String> onLink;
    private final Scroller scroller = new Scroller();
    private String body = "";
    private Layout layout = Layout.EMPTY;
    private boolean draggingBar;

    public MarkdownView(int x, int y, int w, int h, LayoutCache cache, IntConsumer onToggleTask, Consumer<String> onLink) {
        super(x, y, w, h, Component.empty());
        this.cache = cache;
        this.onToggleTask = onToggleTask;
        this.onLink = onLink;
    }

    public void setBody(String newBody) {
        body = newBody == null ? "" : newBody;
        relayout();
    }

    public void setBounds(int x, int y, int w, int h) {
        setX(x);
        setY(y);
        setWidth(w);
        setHeight(h);
        relayout();
    }

    public void scrollToTop() {
        scroller.scrollTo(0);
    }

    private void relayout() {
        Font font = Minecraft.getInstance().font;
        int contentW = getWidth() - 2 * Theme.PAD - Theme.SCROLLBAR - 2;
        layout = cache.get(body, contentW, ConfigHolder.get().checkedTaskStyle, new FontMeasure(font));
        scroller.update(layout.height() + 2 * Theme.PAD, getHeight());
    }

    private int barX() {
        return getX() + getWidth() - Theme.SCROLLBAR;
    }

    private boolean overBar(double mx, double my) {
        return scroller.visible() && mx >= barX() && mx < barX() + Theme.SCROLLBAR && my >= getY() && my < getY() + getHeight();
    }

    private Optional<Layout.HitBox> hitAt(double mx, double my) {
        double lx = mx - (getX() + Theme.PAD);
        double ly = my - (getY() + Theme.PAD) + scroller.amount();
        return layout.hitBoxes().stream().filter(h -> h.contains(lx, ly)).findFirst();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + getWidth();
        int y2 = y1 + getHeight();
        g.fill(x1, y1, x2, y2, Theme.PANEL);
        LayoutRenderer.draw(g, Minecraft.getInstance().font, layout, x1 + Theme.PAD, y1 + Theme.PAD - scroller.amount(), 1f,
                x1, y1, x2 - (scroller.visible() ? Theme.SCROLLBAR : 0), y2);
        if (scroller.visible()) {
            Theme.scrollbar(g, barX(), y1, getHeight(), scroller, draggingBar);
        }
    }

    @Override
    public void onClick(MouseButtonEvent e, boolean doubleClick) {
        if (overBar(e.x(), e.y())) {
            draggingBar = true;
            scroller.dragTo(getHeight(), (int) (e.y() - getY() - scroller.thumbHeight(getHeight()) / 2));
            return;
        }
        hitAt(e.x(), e.y()).ifPresent(hit -> {
            if (hit.kind() == Layout.HitKind.TASK) {
                onToggleTask.accept(hit.sourceLine());
            } else {
                onLink.accept(hit.url());
            }
        });
    }

    @Override
    protected void onDrag(MouseButtonEvent e, double dx, double dy) {
        if (draggingBar) {
            scroller.dragTo(getHeight(), (int) (e.y() - getY() - scroller.thumbHeight(getHeight()) / 2));
        }
    }

    @Override
    public void onRelease(MouseButtonEvent e) {
        draggingBar = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scroller.scrollBy((int) Math.round(-vertical * WHEEL_STEP));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (!isFocused()) {
            return false;
        }
        switch (e.key()) {
            case GLFW.GLFW_KEY_PAGE_UP -> scroller.pageBy(-1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroller.pageBy(1);
            case GLFW.GLFW_KEY_HOME -> scroller.scrollTo(0);
            case GLFW.GLFW_KEY_END -> scroller.scrollTo(scroller.max());
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
