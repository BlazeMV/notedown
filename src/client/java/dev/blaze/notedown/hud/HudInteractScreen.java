package dev.blaze.notedown.hud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.NotedownKeys;
import dev.blaze.notedown.gui.EditScreen;
import dev.blaze.notedown.ui.ConfirmDialog;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.Optional;

public final class HudInteractScreen extends Screen {

    private PinRenderer.Hit dragging;
    private double lastX;
    private double lastY;

    public HudInteractScreen() {
        super(Messages.t("screen.interact.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        PinRenderer.draw(g, minecraft, true, mouseX, mouseY);
        Component hint = Messages.t("screen.interact.hint", NotedownKeys.INTERACT.getTranslatedKeyMessage());
        g.centeredText(font, hint, width / 2, height - 30, Theme.TEXT_MUTED);
    }

    @Override
    public void tick() {
        if (!NotedownKeys.isPhysicallyDown(minecraft, NotedownKeys.INTERACT)) {
            onClose();
        }
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        if (NotedownKeys.INTERACT.matches(e)) {
            onClose();
            return true;
        }
        return super.keyReleased(e);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        Optional<PinRenderer.Hit> hit = PinRenderer.hitTest(e.x(), e.y(), width, height, true);
        if (hit.isEmpty()) {
            return false;
        }
        PinRenderer.Hit h = hit.get();
        switch (h.kind()) {
            case CLOSE -> unpin(h.entry());
            case HEADER -> {
                if (doubleClick) {
                    PinnedNotes.saveGeometry();
                    EditScreen.open(null, h.entry().note, h.entry().scope);
                } else {
                    startDrag(h, e);
                }
            }
            case HANDLE -> startDrag(h, e);
            case TASK -> PinRenderer.toggleTask(h.entry(), h.sourceLine());
            case LINK -> ConfirmDialog.openLink(this, h.url());
            case BODY -> { }
        }
        return true;
    }

    private void startDrag(PinRenderer.Hit h, MouseButtonEvent e) {
        dragging = h;
        lastX = e.x();
        lastY = e.y();
    }

    private void unpin(PinnedNotes.Entry entry) {
        try {
            PinnedNotes.unpin(entry);
        } catch (IOException ex) {
            Notedown.LOGGER.error("Failed to unpin note", ex);
            Messages.chat(minecraft, Messages.t("message.save_failed"));
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging == null) {
            return false;
        }
        int mdx = (int) Math.round(e.x() - lastX);
        int mdy = (int) Math.round(e.y() - lastY);
        if (mdx == 0 && mdy == 0) {
            return true;
        }
        PinnedNotes.Entry entry = dragging.entry();
        PinGeometry.Rect r = PinGeometry.rect(entry.pin, width, height);
        entry.pin = dragging.kind() == PinRenderer.HitKind.HEADER
                ? PinGeometry.moved(entry.pin, r, mdx, mdy, width, height)
                : PinGeometry.resized(entry.pin, r, mdx, mdy, width, height);
        lastX = e.x();
        lastY = e.y();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging == null) {
            return false;
        }
        dragging = null;
        PinnedNotes.saveGeometry();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        Optional<PinRenderer.Hit> hit = PinRenderer.hitTest(mouseX, mouseY, width, height, true);
        if (hit.isEmpty()) {
            return false;
        }
        PinRenderer.scroll(hit.get().entry(), vertical, width, height, true);
        return true;
    }

    @Override
    public void onClose() {
        PinnedNotes.saveGeometry();
        minecraft.gui.setScreen(null);
    }
}
