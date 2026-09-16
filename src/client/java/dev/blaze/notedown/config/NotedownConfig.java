package dev.blaze.notedown.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.blaze.notedown.markdown.CheckedTaskStyle;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public final class NotedownConfig {

    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.0f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public float pinnedBackgroundOpacity = 0.5f;
    public float pinnedTextScale = 1.0f;
    public boolean showPinnedInContainers = true;
    public boolean showPinnedWithChat = true;
    public CheckedTaskStyle checkedTaskStyle = CheckedTaskStyle.STRIKE_MUTED;
    public boolean showInsertButtons = true;
    public boolean pinnedHidden = false;

    public static NotedownConfig load(Path file, Consumer<String> onProblem) {
        if (!Files.exists(file)) {
            return new NotedownConfig().clamp();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            NotedownConfig config = GSON.fromJson(reader, NotedownConfig.class);
            return (config != null ? config : new NotedownConfig()).clamp();
        } catch (IOException | JsonParseException e) {
            onProblem.accept("Failed to load config from " + file.getFileName() + ": " + e.getMessage());
            return new NotedownConfig().clamp();
        }
    }

    public void save(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public NotedownConfig clamp() {
        pinnedBackgroundOpacity = Math.max(0f, Math.min(1f, pinnedBackgroundOpacity));
        pinnedTextScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, pinnedTextScale));
        if (checkedTaskStyle == null) {
            checkedTaskStyle = CheckedTaskStyle.STRIKE_MUTED;
        }
        return this;
    }
}
