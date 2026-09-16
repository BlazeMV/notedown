package dev.blaze.notedown;

import dev.blaze.notedown.store.NoteStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class Notedown implements ClientModInitializer {
    public static final String MOD_ID = "notedown";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

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

    @Override
    public void onInitializeClient() {
        LOGGER.info("Notedown loaded");
    }
}
