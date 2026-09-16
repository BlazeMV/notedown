package dev.blaze.notedown.gui;

import dev.blaze.notedown.ui.FlatButton;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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
        Component value = key.getTranslatedKeyMessage();
        if (conflicted()) {
            value = value.copy().withStyle(ChatFormatting.RED);
        }
        if (screen.isSelecting(key)) {
            value = Messages.t("option.selecting", value);
        }
        change.setMessage(Component.empty().append(Component.translatable(key.getName())).append(": ").append(value));
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
