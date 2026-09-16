package dev.blaze.notedown.hud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.config.NotedownConfig;
import dev.blaze.notedown.ui.ConfirmDialog;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.Optional;

public final class ScreenHooks {

    private ScreenHooks() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            NotedownConfig cfg = ConfigHolder.get();
            boolean container = screen instanceof AbstractContainerScreen<?> && cfg.showPinnedInContainers;
            boolean chat = screen instanceof ChatScreen && cfg.showPinnedWithChat;
            if (!container && !chat) {
                return;
            }
            if (container) {
                ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, tick) -> {
                    if (PinnedHudElement.pinsVisible(client)) {
                        PinRenderer.draw(g, client, false, mouseX, mouseY);
                    }
                });
            }
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !PinnedHudElement.pinsVisible(client)) {
                    return true;
                }
                Optional<PinRenderer.Hit> hit = PinRenderer.hitTest(event.x(), event.y(), s.width, s.height, false);
                if (hit.isEmpty()) {
                    return true;
                }
                PinRenderer.Hit h = hit.get();
                switch (h.kind()) {
                    case TASK -> PinRenderer.toggleTask(h.entry(), h.sourceLine());
                    case LINK -> ConfirmDialog.openLink(s, h.url());
                    default -> {
                        return true;
                    }
                }
                return false;
            });
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> {
                if (!PinnedHudElement.pinsVisible(client)) {
                    return true;
                }
                Optional<PinRenderer.Hit> hit = PinRenderer.hitTest(mouseX, mouseY, s.width, s.height, false);
                if (hit.isEmpty()) {
                    return true;
                }
                PinRenderer.scroll(hit.get().entry(), vertical, s.width, s.height, false);
                return false;
            });
            ScreenEvents.remove(screen).register(s -> PinnedNotes.saveGeometry());
        });
    }
}
