package dev.blaze.notedown;

import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.config.NotedownConfig;
import dev.blaze.notedown.gui.EditScreen;
import dev.blaze.notedown.gui.NotebookScreen;
import dev.blaze.notedown.hud.HudInteractScreen;
import dev.blaze.notedown.hud.PinnedHudElement;
import dev.blaze.notedown.hud.PinnedNotes;
import dev.blaze.notedown.hud.ScreenHooks;
import dev.blaze.notedown.markdown.LayoutCache;
import dev.blaze.notedown.scope.CurrentScope;
import dev.blaze.notedown.store.NoteStore;
import dev.blaze.notedown.ui.Messages;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class Notedown implements ClientModInitializer {
    public static final String MOD_ID = "notedown";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final LayoutCache LAYOUTS = new LayoutCache();

    private static NoteStore store;
    private static boolean interactWasDown;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static NoteStore store() {
        if (store == null) {
            Path root = FabricLoader.getInstance().getGameDir().resolve(MOD_ID);
            store = new NoteStore(root, msg -> LOGGER.warn(msg));
        }
        return store;
    }

    public static LayoutCache layouts() {
        return LAYOUTS;
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Notedown loaded");
        ConfigHolder.get();
        NotedownKeys.register();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("pinned"), new PinnedHudElement());
        ScreenHooks.register();
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> PinnedNotes.reload());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> PinnedNotes.clear());
        ClientTickEvents.END_CLIENT_TICK.register(Notedown::tick);
    }

    private static void tick(Minecraft mc) {
        while (NotedownKeys.OPEN.consumeClick()) {
            if (mc.level != null) {
                mc.gui.setScreen(new NotebookScreen(null));
            }
        }
        while (NotedownKeys.NEW_NOTE.consumeClick()) {
            if (mc.level != null) {
                EditScreen.open(null, null, CurrentScope.dirs(store(), mc).getFirst());
            }
        }
        while (NotedownKeys.TOGGLE_PINNED.consumeClick()) {
            NotedownConfig cfg = ConfigHolder.get();
            cfg.pinnedHidden = !cfg.pinnedHidden;
            ConfigHolder.save();
            Messages.chat(mc, Messages.t(cfg.pinnedHidden ? "message.pinned_hidden" : "message.pinned_shown"));
        }
        boolean interactDown = NotedownKeys.INTERACT.isDown();
        if (interactDown && !interactWasDown && mc.gui.screen() == null && PinnedHudElement.pinsVisible(mc)) {
            mc.gui.setScreen(new HudInteractScreen());
        }
        interactWasDown = interactDown;
        while (NotedownKeys.QUICK_EDIT.consumeClick()) {
            if (mc.level != null) {
                PinnedNotes.first().ifPresentOrElse(
                        e -> EditScreen.open(null, e.note, e.scope),
                        () -> Messages.chat(mc, Messages.t("message.no_pinned")));
            }
        }
    }
}
