package dev.blaze.notedown.gui;

import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.hud.PinnedNotes;
import dev.blaze.notedown.scope.CurrentScope;
import dev.blaze.notedown.store.Note;
import dev.blaze.notedown.store.NoteStore;
import dev.blaze.notedown.store.ScopeDir;
import dev.blaze.notedown.ui.ConfirmDialog;
import dev.blaze.notedown.ui.FlatButton;
import dev.blaze.notedown.ui.FlatList;
import dev.blaze.notedown.ui.FlatTextField;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class NotebookScreen extends Screen {

    private static final int SIDE_W = 110;
    private static final int TOP = 12;
    private static final int NOTE_ROW = 36;

    private final Screen parent;
    private final NoteStore store = Notedown.store();
    private List<ScopeDir> scopes = List.of();
    private List<Note> all = List.of();
    private Set<Path> pinned = Set.of();
    private String query = "";
    private Note keep;
    private FlatTextField search;
    private FlatList<Note> list;
    private final List<FlatButton> selectionButtons = new ArrayList<>();
    private FlatButton pinButton;

    public NotebookScreen(Screen parent) {
        super(Messages.t("screen.notebook.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        scopes = CurrentScope.dirs(store, minecraft);
        selectionButtons.clear();
        int x = Theme.MARGIN;
        int y = TOP;
        search = new FlatTextField(font, x, y, width - 2 * Theme.MARGIN, Theme.BUTTON_HEIGHT, Messages.t("screen.notebook.search"));
        search.setValue(query);
        search.setResponder(q -> {
            query = q;
            filter();
        });
        addRenderableWidget(search);
        y += Theme.BUTTON_HEIGHT + 8;
        int listY = y;
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.new"), this::newNote));
        y += 25;
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.import"), this::importFiles));
        y += 25;
        y += 10;
        selectionButtons.add(addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.view"), () -> selected().ifPresent(this::view))));
        y += 25;
        selectionButtons.add(addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.edit"), () -> selected().ifPresent(this::edit))));
        y += 25;
        pinButton = addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.pin"), () -> selected().ifPresent(this::togglePin)));
        selectionButtons.add(pinButton);
        y += 25;
        selectionButtons.add(addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.duplicate"), () -> selected().ifPresent(this::duplicate))));
        y += 25;
        selectionButtons.add(addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.delete"), () -> selected().ifPresent(this::delete))));
        addRenderableWidget(new FlatButton(x, height - Theme.MARGIN - Theme.BUTTON_HEIGHT, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.done"), this::onClose));

        int lx = Theme.MARGIN * 2 + SIDE_W;
        list = new FlatList<>(lx, listY, width - lx - Theme.MARGIN, height - listY - Theme.MARGIN, NOTE_ROW,
                this::renderRow, n -> updateButtons(), this::view);
        addRenderableWidget(list);
        reload();
        setInitialFocus(list);
    }

    private void reload() {
        PinnedNotes.reload();
        List<Note> notes = new ArrayList<>();
        Set<Path> pins = new HashSet<>();
        for (ScopeDir scope : scopes) {
            notes.addAll(store.list(scope));
            for (String name : store.index(scope).pins().keySet()) {
                pins.add(scope.dir().resolve(name).toAbsolutePath().normalize());
            }
        }
        pinned = pins;
        notes.sort(Comparator.comparing((Note n) -> !isPinned(n)).thenComparing(Comparator.comparingLong(Note::lastModified).reversed()));
        all = notes;
        filter();
    }

    private boolean isPinned(Note n) {
        return pinned.contains(n.file().toAbsolutePath().normalize());
    }

    private void filter() {
        List<Note> shown = NoteStore.search(all, query);
        list.setItems(shown, null);
        if (keep != null) {
            for (int i = 0; i < shown.size(); i++) {
                if (shown.get(i).sameFile(keep)) {
                    list.select(i);
                    break;
                }
            }
        }
        updateButtons();
    }

    private Optional<Note> selected() {
        return list.selected();
    }

    private void updateButtons() {
        Optional<Note> sel = selected();
        keep = sel.orElse(keep);
        for (FlatButton b : selectionButtons) {
            b.active = sel.isPresent();
        }
        pinButton.setMessage(Messages.t(sel.map(this::isPinned).orElse(false) ? "button.unpin" : "button.pin"));
    }

    private void renderRow(GuiGraphicsExtractor g, Note n, int x, int y, int w, int h, boolean selected, boolean hovered) {
        String badge = "(" + Component.translatable(n.scope().kind().langKey()).getString() + ")"
                + (isPinned(n) ? " · " + Messages.t("list.pinned").getString() : "");
        String title = Theme.ellipsize(font, n.title(), w - font.width(badge) - 6);
        g.text(font, title, x, y + 4, Theme.TEXT);
        g.text(font, badge, x + font.width(title) + 4, y + 4, Theme.TEXT_MUTED);
        g.text(font, Theme.ellipsize(font, NoteStore.preview(n.body()), w), x, y + 15, Theme.TEXT_MUTED);
        String date = Messages.t("list.modified", ViewScreen.DATE.format(Instant.ofEpochMilli(n.lastModified()).atZone(ZoneId.systemDefault()))).getString();
        g.text(font, date, x, y + 25, Theme.TEXT_DISABLED);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        if (all.isEmpty()) {
            g.centeredText(font, Messages.t("screen.notebook.empty"), list.getX() + list.getWidth() / 2, list.getY() + list.getHeight() / 2 - 4, Theme.TEXT_MUTED);
        }
        if (scopes.size() > 1) {
            ScopeDir current = scopes.getFirst();
            Component label = Messages.t("screen.notebook.scope", Component.translatable(current.kind().langKey()), current.label());
            g.text(font, Theme.ellipsize(font, label.getString(), SIDE_W), Theme.MARGIN, height - Theme.MARGIN - Theme.BUTTON_HEIGHT - 12, Theme.TEXT_DISABLED);
        }
    }

    private void newNote() {
        EditScreen.open(this, null, scopes.getFirst());
    }

    private void view(Note n) {
        keep = n;
        minecraft.gui.setScreen(new ViewScreen(this, n));
    }

    private void edit(Note n) {
        keep = n;
        EditScreen.open(this, n, n.scope());
    }

    private void togglePin(Note n) {
        try {
            PinnedNotes.toggle(n, width, height);
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to update pins", e);
            Messages.chat(minecraft, Messages.t("message.save_failed"));
        }
        keep = n;
        reload();
    }

    private void duplicate(Note n) {
        try {
            keep = store.duplicate(n);
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to duplicate note", e);
            Messages.chat(minecraft, Messages.t("message.save_failed"));
        }
        reload();
    }

    private void delete(Note n) {
        minecraft.gui.setScreen(new ConfirmDialog(this, Messages.t("dialog.delete_title"), Messages.t("dialog.delete_body", n.title()),
                Messages.t("button.delete"), () -> {
                    try {
                        store.delete(n);
                    } catch (IOException e) {
                        Notedown.LOGGER.error("Failed to delete note", e);
                        Messages.chat(minecraft, Messages.t("message.save_failed"));
                    }
                    keep = null;
                }));
    }

    private void importFiles() {
        int count = 0;
        for (Path file : FileDialogs.openNoteFiles()) {
            try {
                keep = store.importFile(scopes.getFirst(), file);
                count++;
            } catch (IOException e) {
                Notedown.LOGGER.error("Failed to import {}", file, e);
                Messages.chat(minecraft, Messages.t("message.import_failed"));
            }
        }
        if (count > 0) {
            Messages.chat(minecraft, Messages.t("message.imported", count));
            reload();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.hasControlDown() && e.key() == GLFW.GLFW_KEY_N) {
            newNote();
            return true;
        }
        if (e.hasControlDown() && e.key() == GLFW.GLFW_KEY_F) {
            setFocused(search);
            return true;
        }
        if (getFocused() == list) {
            if (e.key() == GLFW.GLFW_KEY_E) {
                selected().ifPresent(this::edit);
                return true;
            }
            if (e.key() == GLFW.GLFW_KEY_DELETE) {
                selected().ifPresent(this::delete);
                return true;
            }
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
