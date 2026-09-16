package dev.blaze.notedown.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class Messages {

    private Messages() {}

    public static void chat(Minecraft mc, Component message) {
        mc.gui.chatListener().handleSystemMessage(message, false);
    }

    public static Component t(String key, Object... args) {
        return Component.translatable("notedown." + key, args);
    }
}
