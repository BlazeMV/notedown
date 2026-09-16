package dev.blaze.notedown;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Notedown implements ClientModInitializer {
    public static final String MOD_ID = "notedown";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Notedown loaded");
    }
}
