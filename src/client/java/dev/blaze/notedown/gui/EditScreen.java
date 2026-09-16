package dev.blaze.notedown.gui;

import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.hud.PinnedNotes;
import dev.blaze.notedown.markdown.EditorCommands;
import dev.blaze.notedown.markdown.TaskToggler;
import dev.blaze.notedown.markdown.UndoStack;
import dev.blaze.notedown.scope.CurrentScope;
import dev.blaze.notedown.store.Note;
import dev.blaze.notedown.store.NoteFiles;
import dev.blaze.notedown.store.NoteStore;
import dev.blaze.notedown.store.ScopeDir;
import dev.blaze.notedown.ui.ConfirmDialog;
import dev.blaze.notedown.ui.FlatButton;
import dev.blaze.notedown.ui.FlatTextArea;
import dev.blaze.notedown.ui.FlatTextField;
import dev.blaze.notedown.ui.MarkdownView;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public final class EditScreen extends Screen {

    private static final int SIDE_W = 110;
    private static final int TOP = 12;
    private static final int UNDO_LIMIT = 100;
    private static final long COALESCE_MS = 700;

    private final Screen returnTo;
    private final NoteStore store;
    private final ScopeDir currentScope;
    private final ScopeDir globalScope;
    private final UndoStack undo;
    private Note note;
    private ScopeDir scope;
    private String title;
    private String body;
    private int cursor;
    private String savedTitle;
    private String savedBody;
    private ScopeDir savedScope;
    private boolean previewing;
    private boolean applying;
    private FlatTextField titleField;
    private FlatTextArea bodyArea;
    private MarkdownView preview;
    private FlatButton previewButton;
    private FlatButton scopeButton;

    public EditScreen(Screen returnTo, Note note, ScopeDir defaultScope) {
        super(Messages.t(note == null ? "screen.edit.new" : "screen.edit.edit"));
        this.returnTo = returnTo;
        this.store = Notedown.store();
        this.note = note;
        List<ScopeDir> dirs = CurrentScope.dirs(store, Minecraft.getInstance());
        this.globalScope = dirs.getLast();
        this.currentScope = dirs.size() > 1 ? dirs.getFirst() : null;
        this.scope = note != null ? note.scope() : defaultScope;
        this.title = note != null ? note.title() : "";
        this.body = note != null ? note.body() : "";
        this.savedTitle = title;
        this.savedBody = body;
        this.savedScope = scope;
        this.undo = new UndoStack(body, UNDO_LIMIT, COALESCE_MS);
    }

    /** Opens the editor unless the note is too large for the in-game text box. */
    public static void open(Screen returnTo, Note note, ScopeDir scope) {
        Minecraft mc = Minecraft.getInstance();
        if (note != null && note.body().length() > NoteStore.MAX_EDITABLE_BYTES) {
            Messages.chat(mc, Messages.t("message.too_large"));
            return;
        }
        mc.gui.setScreen(new EditScreen(returnTo, note, scope));
    }

    @Override
    protected void init() {
        if (bodyArea != null) {
            cursor = bodyArea.cursor();
        }
        int x = Theme.MARGIN;
        int y = TOP;
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.save"), this::saveAndClose));
        y += 25;
        previewButton = addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, previewLabel(), this::togglePreview));
        y += 25;
        Minecraft mc = minecraft;
        if (ConfigHolder.get().showInsertButtons && GameInfo.inWorld(mc)) {
            y += 5;
            addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.insert_coords"), () -> insert(GameInfo.coords(mc))));
            y += 25;
            addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.insert_chunk"), () -> insert(GameInfo.chunk(mc))));
            y += 25;
            addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.insert_biome"), () -> insert(GameInfo.biome(mc))));
            y += 25;
        }
        y += 5;
        scopeButton = addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, scopeLabel(), this::toggleScope));
        scopeButton.active = currentScope != null;
        addRenderableWidget(new FlatButton(x, height - Theme.MARGIN - Theme.BUTTON_HEIGHT, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.back"), this::onClose));

        int cx = Theme.MARGIN * 2 + SIDE_W;
        int cw = width - cx - Theme.MARGIN;
        titleField = new FlatTextField(font, cx, TOP, cw, Theme.BUTTON_HEIGHT, Messages.t("screen.edit.title_hint"));
        titleField.setValue(title);
        titleField.setResponder(v -> title = v);
        addRenderableWidget(titleField);

        int by = TOP + Theme.BUTTON_HEIGHT + 6;
        int bh = height - by - Theme.MARGIN;
        bodyArea = new FlatTextArea(font, cx, by, cw, bh, Messages.t("screen.edit.body_hint"));
        bodyArea.replaceAll(body, cursor);
        bodyArea.setValueListener(this::onBodyChanged);
        addRenderableWidget(bodyArea);

        preview = new MarkdownView(cx, by, cw, bh, Notedown.layouts(), this::toggleTaskInPreview, this::openLink);
        preview.setBody(body);
        addRenderableWidget(preview);
        applyPreviewState();
        setInitialFocus(previewing ? preview : bodyArea);
    }

    @Override
    public void removed() {
        if (bodyArea != null) {
            cursor = bodyArea.cursor();
        }
    }

    private Component previewLabel() {
        return Messages.t(previewing ? "button.source" : "button.preview");
    }

    private Component scopeLabel() {
        return Messages.t("button.scope", Component.translatable(scope.kind().langKey()));
    }

    private void applyPreviewState() {
        bodyArea.visible = !previewing;
        bodyArea.active = !previewing;
        preview.visible = previewing;
        preview.active = previewing;
        previewButton.setMessage(previewLabel());
    }

    private void togglePreview() {
        previewing = !previewing;
        if (previewing) {
            preview.setBody(body);
        }
        applyPreviewState();
        setFocused(previewing ? preview : bodyArea);
    }

    private void toggleScope() {
        if (currentScope == null) {
            return;
        }
        scope = scope.isGlobal() ? currentScope : globalScope;
        scopeButton.setMessage(scopeLabel());
    }

    private void insert(String text) {
        if (previewing) {
            togglePreview();
        }
        bodyArea.insertAtCursor(text);
        setFocused(bodyArea);
    }

    private void onBodyChanged(String value) {
        body = value;
        if (!applying) {
            undo.record(value, System.currentTimeMillis());
        }
    }

    private void toggleTaskInPreview(int line) {
        String toggled = TaskToggler.toggleLine(body, line);
        bodyArea.replaceAll(toggled, Math.min(bodyArea.cursor(), toggled.length()));
        preview.setBody(body);
    }

    private void openLink(String url) {
        ConfirmDialog.openLink(this, url);
    }

    private boolean dirty() {
        return !title.equals(savedTitle) || !body.equals(savedBody) || !scope.dir().equals(savedScope.dir());
    }

    private boolean save() {
        String effectiveTitle = title.isBlank() ? EditorCommands.titleFromBody(body) : title;
        try {
            note = note == null ? store.create(scope, effectiveTitle, body) : store.save(note, effectiveTitle, body, scope);
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to save note", e);
            Messages.chat(minecraft, Messages.t("message.save_failed"));
            return false;
        }
        title = note.title();
        titleField.setValue(title);
        savedTitle = title;
        savedBody = note.body();
        body = savedBody;
        savedScope = scope;
        PinnedNotes.reload();
        return true;
    }

    private void saveAndClose() {
        if (save()) {
            close();
        }
    }

    private void close() {
        minecraft.gui.setScreen(returnTo);
    }

    @Override
    public void onClose() {
        if (!dirty()) {
            close();
            return;
        }
        String shown = title.isBlank() ? NoteFiles.UNTITLED : title;
        minecraft.gui.setScreen(new ConfirmDialog(this, Messages.t("dialog.discard_title"), Messages.t("dialog.discard_body", shown),
                Messages.t("button.discard"), this::close));
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        boolean ctrl = e.hasControlDown();
        if (ctrl && e.key() == GLFW.GLFW_KEY_S) {
            save();
            return true;
        }
        if (ctrl && e.key() == GLFW.GLFW_KEY_E) {
            togglePreview();
            return true;
        }
        if (!previewing && getFocused() == bodyArea) {
            if (ctrl && e.key() == GLFW.GLFW_KEY_Z && !e.hasShiftDown()) {
                apply(undo.undo());
                return true;
            }
            if (ctrl && (e.key() == GLFW.GLFW_KEY_Y || (e.key() == GLFW.GLFW_KEY_Z && e.hasShiftDown()))) {
                apply(undo.redo());
                return true;
            }
            if (ctrl && e.key() == GLFW.GLFW_KEY_D) {
                command(EditorCommands.wrap(body, bodyArea.selectionStart(), bodyArea.selectionEnd(), "**"));
                return true;
            }
            if (ctrl && e.key() == GLFW.GLFW_KEY_I) {
                command(EditorCommands.wrap(body, bodyArea.selectionStart(), bodyArea.selectionEnd(), "*"));
                return true;
            }
            if (ctrl && e.hasShiftDown() && e.key() == GLFW.GLFW_KEY_C) {
                command(EditorCommands.toggleTask(body, bodyArea.cursor()));
                return true;
            }
            if ((e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) && !ctrl && !e.hasShiftDown()) {
                command(EditorCommands.enter(body, bodyArea.cursor()));
                return true;
            }
            if (e.key() == GLFW.GLFW_KEY_TAB) {
                command(EditorCommands.indent(body, bodyArea.cursor(), e.hasShiftDown()));
                return true;
            }
        }
        return super.keyPressed(e);
    }

    private void command(EditorCommands.Edit edit) {
        bodyArea.replaceAll(edit.text(), edit.cursor());
    }

    private void apply(Optional<String> value) {
        value.ifPresent(v -> {
            applying = true;
            bodyArea.replaceAll(v, Math.min(bodyArea.cursor(), v.length()));
            applying = false;
        });
    }
}
