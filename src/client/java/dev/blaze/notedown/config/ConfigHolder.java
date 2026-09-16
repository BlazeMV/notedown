package dev.blaze.notedown.config;

import dev.blaze.notedown.Notedown;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigHolder {

    private static NotedownConfig instance;

    private ConfigHolder() {}

    public static NotedownConfig get() {
        if (instance == null) {
            Path file = path();
            boolean existed = Files.exists(file);
            boolean[] hadProblem = {false};
            instance = NotedownConfig.load(file, msg -> {
                hadProblem[0] = true;
                Notedown.LOGGER.warn(msg);
            });
            if (!existed || hadProblem[0]) {
                if (hadProblem[0]) {
                    quarantine(file);
                }
                Notedown.LOGGER.info("Writing default config to {}", file);
                persist(file);
            }
        }
        return instance;
    }

    public static void save() {
        get();
        persist(path());
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("notedown.json");
    }

    private static void persist(Path file) {
        try {
            instance.save(file);
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to save config", e);
        }
    }

    private static void quarantine(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to preserve unreadable config", e);
        }
    }
}
