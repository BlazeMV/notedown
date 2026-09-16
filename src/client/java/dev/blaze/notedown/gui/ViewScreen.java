package dev.blaze.notedown.gui;

import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.hud.PinnedNotes;
import dev.blaze.notedown.markdown.TaskToggler;
import dev.blaze.notedown.store.Note;
import dev.blaze.notedown.store.NoteStore;
import dev.blaze.notedown.ui.ConfirmDialog;
import dev.blaze.notedown.ui.FlatButton;
import dev.blaze.notedown.ui.MarkdownView;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ViewScreen extends Screen {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");
    private static final int SIDE_W = 110;
    private static final int TOP = 12;

    private final Screen parent;
    private final NoteStore store = Notedown.store();
    private Note note;
    private MarkdownView view;
    private FlatButton pinButton;

    public ViewScreen(Screen parent, Note note) {
        super(Component.literal(note.title()));
        this.parent = parent;
        this.note = note;
    }

    @Override
    protected void init() {
        int x = Theme.MARGIN;
        int y = TOP;
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.edit"), this::edit));
        y += 25;
        pinButton = addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, pinLabel(), this::togglePin));
        y += 25;
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.duplicate"), this::duplicate));
        y += 25;
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.copy"), this::copyText));
        y += 25;
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.delete"), this::delete));
        addRenderableWidget(new FlatButton(x, height - Theme.MARGIN - Theme.BUTTON_HEIGHT, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.back"), this::onClose));

        int cx = Theme.MARGIN * 2 + SIDE_W;
        int cw = width - cx - Theme.MARGIN;
        int vy = TOP + font.lineHeight + 8;
        view = new MarkdownView(cx, vy, cw, height - vy - Theme.MARGIN, Notedown.layouts(), this::toggleTask, this::openLink);
        view.setBody(note.body());
        addRenderableWidget(view);
        setInitialFocus(view);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int cx = Theme.MARGIN * 2 + SIDE_W;
        int cw = width - cx - Theme.MARGIN;
        String date = Messages.t("list.modified", DATE.format(Instant.ofEpochMilli(note.lastModified()).atZone(ZoneId.systemDefault()))).getString();
        int dateW = font.width(date);
        String badge = "(" + Component.translatable(note.scope().kind().langKey()).getString() + ")";
        String title = Theme.ellipsize(font, note.title(), cw - dateW - font.width(badge) - 12);
        g.text(font, title, cx, TOP, Theme.TEXT);
        g.text(font, badge, cx + font.width(title) + 4, TOP, Theme.TEXT_MUTED);
        g.text(font, date, cx + cw - dateW, TOP, Theme.TEXT_DISABLED);
    }

    private Component pinLabel() {
        return Messages.t(PinnedNotes.isPinned(note) ? "button.unpin" : "button.pin");
    }

    private void edit() {
        EditScreen.open(parent, note, note.scope());
    }

    private void togglePin() {
        try {
            PinnedNotes.toggle(note, width, height);
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to update pins", e);
            Messages.chat(minecraft, Messages.t("message.save_failed"));
        }
        pinButton.setMessage(pinLabel());
    }

    private void duplicate() {
        try {
            store.duplicate(note);
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to duplicate note", e);
            Messages.chat(minecraft, Messages.t("message.save_failed"));
            return;
        }
        minecraft.gui.setScreen(parent);
    }

    private void copyText() {
        minecraft.keyboardHandler.setClipboard(note.body());
        Messages.chat(minecraft, Messages.t("message.copied"));
    }

    private void delete() {
        minecraft.gui.setScreen(new ConfirmDialog(this, Messages.t("dialog.delete_title"), Messages.t("dialog.delete_body", note.title()),
                Messages.t("button.delete"), () -> {
                    try {
                        store.delete(note);
                        PinnedNotes.reload();
                    } catch (IOException e) {
                        Notedown.LOGGER.error("Failed to delete note", e);
                        Messages.chat(minecraft, Messages.t("message.save_failed"));
                    }
                    minecraft.gui.setScreen(parent);
                }));
    }

    private void toggleTask(int line) {
        try {
            note = store.save(note, note.title(), TaskToggler.toggleLine(note.body(), line), note.scope());
            view.setBody(note.body());
            PinnedNotes.reload();
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to save note", e);
            Messages.chat(minecraft, Messages.t("message.save_failed"));
        }
    }

    private void openLink(String url) {
        ConfirmDialog.openLink(this, url);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_E && !e.hasControlDown()) {
            edit();
            return true;
        }
        if (e.key() == GLFW.GLFW_KEY_P && !e.hasControlDown()) {
            togglePin();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
