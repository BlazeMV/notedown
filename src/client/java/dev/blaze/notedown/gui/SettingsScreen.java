package dev.blaze.notedown.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.NotedownKeys;
import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.config.NotedownConfig;
import dev.blaze.notedown.scope.CurrentScope;
import dev.blaze.notedown.ui.FlatButton;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Slider;
import dev.blaze.notedown.ui.Theme;
import dev.blaze.notedown.ui.Toggle;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SettingsScreen extends Screen {

    private static final int COL_W = 300;
    private static final int GAP = 10;
    private static final int TOP = 12;

    private final Screen parent;
    private final List<KeyBindRow> keyRows = new ArrayList<>();
    @Nullable
    private KeyMapping selecting;
    private FlatButton checkedStyleButton;
    private int leftX;
    private int rightX;

    public SettingsScreen(Screen parent) {
        super(Messages.t("screen.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        NotedownConfig cfg = ConfigHolder.get();
        keyRows.clear();
        selecting = null;
        int totalW = Math.min(width - 2 * Theme.MARGIN, 2 * COL_W + GAP);
        int colW = (totalW - GAP) / 2;
        leftX = (width - totalW) / 2;
        rightX = leftX + colW + GAP;
        int y = TOP + font.lineHeight + 6;

        addRenderableWidget(new Slider(leftX, y, colW, Messages.t("option.pinned_opacity"), 0f, 1f, 0.05f, cfg.pinnedBackgroundOpacity,
                v -> Math.round(v * 100) + "%", v -> {
                    cfg.pinnedBackgroundOpacity = v;
                    ConfigHolder.save();
                }));
        y += Theme.ROW_HEIGHT;
        addRenderableWidget(new Slider(leftX, y, colW, Messages.t("option.pinned_scale"), NotedownConfig.MIN_SCALE, NotedownConfig.MAX_SCALE, 0.1f,
                cfg.pinnedTextScale, v -> String.format(Locale.ROOT, "%.1f×", v), v -> {
                    cfg.pinnedTextScale = v;
                    ConfigHolder.save();
                }));
        y += Theme.ROW_HEIGHT;
        addRenderableWidget(new Toggle(leftX, y, colW, Messages.t("option.show_in_containers"), cfg.showPinnedInContainers, v -> {
            cfg.showPinnedInContainers = v;
            ConfigHolder.save();
        }));
        y += Theme.ROW_HEIGHT;
        addRenderableWidget(new Toggle(leftX, y, colW, Messages.t("option.show_with_chat"), cfg.showPinnedWithChat, v -> {
            cfg.showPinnedWithChat = v;
            ConfigHolder.save();
        }));
        y += Theme.ROW_HEIGHT;
        addRenderableWidget(new Toggle(leftX, y, colW, Messages.t("option.show_insert_buttons"), cfg.showInsertButtons, v -> {
            cfg.showInsertButtons = v;
            ConfigHolder.save();
        }));
        y += Theme.ROW_HEIGHT;
        checkedStyleButton = addRenderableWidget(new FlatButton(leftX, y, colW, Theme.BUTTON_HEIGHT, checkedLabel(cfg), () -> {
            cfg.checkedTaskStyle = cfg.checkedTaskStyle.next();
            ConfigHolder.save();
            Notedown.layouts().clear();
            checkedStyleButton.setMessage(checkedLabel(cfg));
        }).leftAlign(true));

        int ry = TOP + font.lineHeight + 6;
        for (KeyMapping key : NotedownKeys.all()) {
            KeyBindRow row = new KeyBindRow(key, this, rightX, ry, colW);
            addRenderableWidget(row.changeButton());
            addRenderableWidget(row.resetButton());
            keyRows.add(row);
            ry += Theme.ROW_HEIGHT;
        }

        int by = height - Theme.MARGIN - Theme.BUTTON_HEIGHT;
        int bw = (totalW - 3 * Theme.MARGIN) / 4;
        int bx = leftX;
        addRenderableWidget(new FlatButton(bx, by, bw, Theme.BUTTON_HEIGHT, Messages.t("button.open_notebook"),
                () -> minecraft.gui.setScreen(new NotebookScreen(this))));
        bx += bw + Theme.MARGIN;
        addRenderableWidget(new FlatButton(bx, by, bw, Theme.BUTTON_HEIGHT, Messages.t("button.open_folder"),
                () -> Util.getPlatform().openPath(Notedown.store().root())));
        bx += bw + Theme.MARGIN;
        addRenderableWidget(new FlatButton(bx, by, bw, Theme.BUTTON_HEIGHT, Messages.t("button.import"), this::importFiles));
        bx += bw + Theme.MARGIN;
        addRenderableWidget(new FlatButton(bx, by, bw, Theme.BUTTON_HEIGHT, Messages.t("button.done"), this::onClose));
    }

    private static Component checkedLabel(NotedownConfig cfg) {
        return Component.empty().append(Messages.t("option.checked_style")).append(": ")
                .append(Messages.t("option.checked_style." + cfg.checkedTaskStyle.name().toLowerCase(Locale.ROOT)));
    }

    private void importFiles() {
        NoteImport.run(minecraft, CurrentScope.dirs(Notedown.store(), minecraft).getFirst());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        g.text(font, title, leftX, TOP, Theme.TEXT);
        g.text(font, Messages.t("screen.settings.keybinds"), rightX, TOP, Theme.TEXT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (selecting == null) {
            return super.mouseClicked(e, doubleClick);
        }
        selecting.setKey(InputConstants.Type.MOUSE.getOrCreate(e.button()));
        selecting = null;
        keysChanged();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (selecting == null) {
            return super.keyPressed(e);
        }
        selecting.setKey(e.key() == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(e));
        selecting = null;
        keysChanged();
        return true;
    }

    boolean isSelecting(KeyMapping key) {
        return selecting == key;
    }

    void startSelecting(KeyMapping key) {
        selecting = key;
        refreshKeyRows();
    }

    void keysChanged() {
        minecraft.options.save();
        KeyMapping.resetMapping();
        refreshKeyRows();
    }

    private void refreshKeyRows() {
        for (KeyBindRow row : keyRows) {
            row.refresh();
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
