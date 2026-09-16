package dev.blaze.notedown.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class FlatList<T> extends AbstractWidget {

    public interface RowRenderer<T> {
        void render(GuiGraphicsExtractor g, T item, int x, int y, int w, int h, boolean selected, boolean hovered);
    }

    private final int rowHeight;
    private final RowRenderer<T> renderer;
    private final Consumer<T> onSelect;
    private final Consumer<T> onActivate;
    private final Scroller scroller = new Scroller();
    private List<T> items = List.of();
    private int selected = -1;
    private boolean draggingBar;

    public FlatList(int x, int y, int w, int h, int rowHeight, RowRenderer<T> renderer, Consumer<T> onSelect, Consumer<T> onActivate) {
        super(x, y, w, h, Component.empty());
        this.rowHeight = rowHeight;
        this.renderer = renderer;
        this.onSelect = onSelect;
        this.onActivate = onActivate;
    }

    public void setItems(List<T> newItems, T keep) {
        items = List.copyOf(newItems);
        selected = keep == null ? -1 : items.indexOf(keep);
        scroller.update(items.size() * rowHeight, getHeight());
        ensureVisible();
    }

    public Optional<T> selected() {
        return selected >= 0 && selected < items.size() ? Optional.of(items.get(selected)) : Optional.empty();
    }

    public void select(int index) {
        if (items.isEmpty()) {
            selected = -1;
            return;
        }
        selected = Math.max(0, Math.min(items.size() - 1, index));
        ensureVisible();
        onSelect.accept(items.get(selected));
    }

    public void setBounds(int x, int y, int w, int h) {
        setX(x);
        setY(y);
        setWidth(w);
        setHeight(h);
        scroller.update(items.size() * rowHeight, h);
    }

    private int rowWidth() {
        return getWidth() - (scroller.visible() ? Theme.SCROLLBAR + 2 : 0);
    }

    private int barX() {
        return getX() + getWidth() - Theme.SCROLLBAR;
    }

    private boolean overBar(double mx, double my) {
        return scroller.visible() && mx >= barX() && mx < barX() + Theme.SCROLLBAR && my >= getY() && my < getY() + getHeight();
    }

    private int indexAt(double my) {
        int i = (int) ((my - getY() + scroller.amount()) / rowHeight);
        return i >= 0 && i < items.size() ? i : -1;
    }

    private void ensureVisible() {
        if (selected < 0) {
            return;
        }
        int top = selected * rowHeight;
        if (top < scroller.amount()) {
            scroller.scrollTo(top);
        } else if (top + rowHeight > scroller.amount() + getHeight()) {
            scroller.scrollTo(top + rowHeight - getHeight());
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        scroller.update(items.size() * rowHeight, getHeight());
        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + getWidth();
        int y2 = y1 + getHeight();
        g.fill(x1, y1, x2, y2, Theme.PANEL);
        g.enableScissor(x1, y1, x2, y2);
        int rowW = rowWidth();
        boolean mouseInside = isMouseOver(mouseX, mouseY) && !overBar(mouseX, mouseY);
        for (int i = 0; i < items.size(); i++) {
            int ry = y1 + i * rowHeight - scroller.amount();
            if (ry + rowHeight < y1 || ry > y2) {
                continue;
            }
            boolean isSelected = i == selected;
            boolean hovered = mouseInside && mouseY >= ry && mouseY < ry + rowHeight;
            if (isSelected) {
                g.fill(x1, ry, x1 + rowW, ry + rowHeight, Theme.PANEL_HOVER);
                g.fill(x1, ry, x1 + 2, ry + rowHeight, Theme.ACCENT);
            } else if (hovered) {
                g.fill(x1, ry, x1 + rowW, ry + rowHeight, Theme.PANEL_DARK);
            }
            renderer.render(g, items.get(i), x1 + Theme.PAD, ry, rowW - 2 * Theme.PAD, rowHeight, isSelected, hovered);
        }
        g.disableScissor();
        if (scroller.visible()) {
            Theme.scrollbar(g, barX(), y1, getHeight(), scroller, draggingBar);
        }
        if (isFocused()) {
            Theme.border(g, x1, y1, x2, y2, Theme.BORDER);
        }
    }

    @Override
    public void onClick(MouseButtonEvent e, boolean doubleClick) {
        if (overBar(e.x(), e.y())) {
            draggingBar = true;
            scroller.dragTo(getHeight(), (int) (e.y() - getY() - scroller.thumbHeight(getHeight()) / 2));
            return;
        }
        int i = indexAt(e.y());
        if (i < 0) {
            return;
        }
        select(i);
        if (doubleClick) {
            onActivate.accept(items.get(i));
        }
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
        scroller.scrollBy((int) Math.round(-vertical * rowHeight));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (!isFocused() || items.isEmpty()) {
            return false;
        }
        if (e.isUp()) {
            select(selected - 1);
            return true;
        }
        if (e.isDown()) {
            select(selected + 1);
            return true;
        }
        if (e.isConfirmation()) {
            selected().ifPresent(onActivate);
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
