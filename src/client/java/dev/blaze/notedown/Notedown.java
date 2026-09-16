package dev.blaze.notedown;

import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.gui.EditScreen;
import dev.blaze.notedown.markdown.LayoutCache;
import dev.blaze.notedown.scope.CurrentScope;
import dev.blaze.notedown.store.NoteStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
        ClientTickEvents.END_CLIENT_TICK.register(Notedown::tick);
    }

    private static void tick(Minecraft mc) {
        while (NotedownKeys.NEW_NOTE.consumeClick()) {
            if (mc.level != null) {
                EditScreen.open(null, null, CurrentScope.dirs(store(), mc).getFirst());
            }
        }
    }
}
