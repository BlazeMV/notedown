package dev.blaze.notedown.gui;

import dev.blaze.notedown.ui.FlatButton;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class KeyBindRow {

    private static final int RESET_W = 60;

    private final KeyMapping key;
    private final SettingsScreen screen;
    private final FlatButton change;
    private final FlatButton reset;

    KeyBindRow(KeyMapping key, SettingsScreen screen, int x, int y, int w) {
        this.key = key;
        this.screen = screen;
        this.change = new FlatButton(x, y, w - RESET_W - 4, Theme.BUTTON_HEIGHT, Component.empty(), () -> screen.startSelecting(key)).leftAlign(true);
        this.reset = new FlatButton(x + w - RESET_W, y, RESET_W, Theme.BUTTON_HEIGHT, Messages.t("option.reset"), this::resetToDefault);
        refresh();
    }

    FlatButton changeButton() {
        return change;
    }

    FlatButton resetButton() {
        return reset;
    }

    void refresh() {
        Font font = Minecraft.getInstance().font;
        String keyText = key.getTranslatedKeyMessage().getString();
        if (screen.isSelecting(key)) {
            keyText = Messages.t("option.selecting", keyText).getString();
        }
        String suffix = ": " + keyText;
        int avail = change.getWidth() - 2 * Theme.PAD;
        String name = Theme.ellipsize(font, Component.translatable(key.getName()).getString(), Math.max(20, avail - font.width(suffix)));
        MutableComponent label = Component.literal(name + suffix);
        if (conflicted()) {
            label = label.withStyle(ChatFormatting.RED);
        }
        change.setMessage(label);
        reset.active = !key.isDefault();
    }

    private void resetToDefault() {
        key.setKey(key.getDefaultKey());
        screen.keysChanged();
    }

    private boolean conflicted() {
        for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
            if (other != key && !other.isUnbound() && other.same(key)) {
                return true;
            }
        }
        return false;
    }
}
