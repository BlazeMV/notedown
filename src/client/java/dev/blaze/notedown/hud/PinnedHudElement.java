package dev.blaze.notedown.hud;

import dev.blaze.notedown.config.ConfigHolder;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

public final class PinnedHudElement implements HudElement {

    public static boolean pinsVisible(Minecraft mc) {
        return mc.level != null && !mc.gui.hud.isHidden() && !ConfigHolder.get().pinnedHidden && !PinnedNotes.entries().isEmpty();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!pinsVisible(mc)) {
            return;
        }
        Screen screen = mc.gui.screen();
        if (screen != null && !(screen instanceof ChatScreen && ConfigHolder.get().showPinnedWithChat)) {
            return;
        }
        PinRenderer.draw(g, mc, false, -1, -1);
    }
}
