package dev.blaze.notedown.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class PinIndex {

    public static final String FILE = "index.json";

    public record Pin(double x, double y, int w, int h, float scale, int scroll) {
        public Pin withPosition(double nx, double ny) {
            return new Pin(nx, ny, w, h, scale, scroll);
        }

        public Pin withSize(int nw, int nh) {
            return new Pin(x, y, nw, nh, scale, scroll);
        }

        public Pin withScroll(int s) {
            return new Pin(x, y, w, h, scale, s);
        }

        public Pin withScale(float s) {
            return new Pin(x, y, w, h, s, scroll);
        }
    }

    private static final class Data {
        int version = 1;
        LinkedHashMap<String, Pin> pins = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Data data;

    private PinIndex(Path file, Data data) {
        this.file = file;
        this.data = data;
    }

    public static PinIndex load(Path dir, Consumer<String> onProblem) {
        Path file = dir.resolve(FILE);
        Data data = new Data();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Data read = GSON.fromJson(reader, Data.class);
                if (read != null && read.pins != null) {
                    data = read;
                }
            } catch (IOException | JsonParseException e) {
                onProblem.accept("Unreadable " + file + ": " + e.getMessage());
                quarantine(file);
                data = new Data();
            }
        }
        data.pins.keySet().removeIf(name -> !Files.isRegularFile(dir.resolve(name)));
        return new PinIndex(file, data);
    }

    public Map<String, Pin> pins() {
        return Collections.unmodifiableMap(data.pins);
    }

    public Optional<Pin> get(String fileName) {
        return Optional.ofNullable(data.pins.get(fileName));
    }

    public boolean isPinned(String fileName) {
        return data.pins.containsKey(fileName);
    }

    public void put(String fileName, Pin pin) {
        data.pins.put(fileName, pin);
    }

    public void remove(String fileName) {
        data.pins.remove(fileName);
    }

    public void rename(String oldName, String newName) {
        Pin pin = data.pins.remove(oldName);
        if (pin != null) {
            data.pins.put(newName, pin);
        }
    }

    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(FILE + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void quarantine(Path file) {
        try {
            Files.move(file, file.resolveSibling(FILE + ".bad"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // the next save overwrites it anyway
        }
    }
}
