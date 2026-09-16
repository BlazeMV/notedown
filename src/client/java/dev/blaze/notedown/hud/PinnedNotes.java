package dev.blaze.notedown.hud;

import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.scope.CurrentScope;
import dev.blaze.notedown.store.Note;
import dev.blaze.notedown.store.NoteFiles;
import dev.blaze.notedown.store.NoteStore;
import dev.blaze.notedown.store.PinIndex;
import dev.blaze.notedown.store.ScopeDir;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PinnedNotes {

    public static final class Entry {
        public final ScopeDir scope;
        public final Note note;
        public PinIndex.Pin pin;
        public int scroll;

        Entry(ScopeDir scope, Note note, PinIndex.Pin pin) {
            this.scope = scope;
            this.note = note;
            this.pin = pin;
            this.scroll = pin.scroll();
        }
    }

    private static List<Entry> entries = new ArrayList<>();

    private PinnedNotes() {}

    public static List<Entry> entries() {
        return entries;
    }

    /** Re-reads pins and note bodies for the current scope; keeps in-memory scroll positions of surviving pins. */
    public static void reload() {
        Minecraft mc = Minecraft.getInstance();
        NoteStore store = Notedown.store();
        Map<Path, Integer> scrolls = new HashMap<>();
        for (Entry e : entries) {
            scrolls.put(e.note.file().toAbsolutePath().normalize(), e.scroll);
        }
        List<Entry> out = new ArrayList<>();
        if (mc.level != null) {
            for (ScopeDir scope : CurrentScope.dirs(store, mc)) {
                PinIndex idx = store.index(scope);
                for (Map.Entry<String, PinIndex.Pin> pin : idx.pins().entrySet()) {
                    String title = NoteFiles.titleOf(scope.dir().resolve(pin.getKey()));
                    store.read(scope, title).ifPresent(note -> {
                        Entry entry = new Entry(scope, note, pin.getValue());
                        Integer kept = scrolls.get(note.file().toAbsolutePath().normalize());
                        if (kept != null) {
                            entry.scroll = kept;
                        }
                        out.add(entry);
                    });
                }
            }
        }
        entries = out;
    }

    public static void clear() {
        entries = new ArrayList<>();
    }

    public static Optional<Entry> first() {
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.getFirst());
    }

    public static boolean isPinned(Note note) {
        return Notedown.store().index(note.scope()).isPinned(note.fileName());
    }

    public static void toggle(Note note, int screenW, int screenH) throws IOException {
        PinIndex idx = Notedown.store().index(note.scope());
        if (idx.isPinned(note.fileName())) {
            idx.remove(note.fileName());
        } else {
            idx.put(note.fileName(), PinGeometry.defaultPin(entries.size(), screenW, screenH, ConfigHolder.get().pinnedTextScale));
        }
        idx.save();
        reload();
    }

    public static void unpin(Entry entry) throws IOException {
        PinIndex idx = Notedown.store().index(entry.scope);
        idx.remove(entry.note.fileName());
        idx.save();
        reload();
    }

    /** Writes every entry's position, size and scroll back to its index.json. */
    public static void saveGeometry() {
        Map<Path, PinIndex> indexes = new LinkedHashMap<>();
        for (Entry e : entries) {
            PinIndex idx = indexes.computeIfAbsent(e.scope.dir(), d -> Notedown.store().index(e.scope));
            idx.put(e.note.fileName(), e.pin.withScroll(e.scroll));
        }
        for (PinIndex idx : indexes.values()) {
            try {
                idx.save();
            } catch (IOException ex) {
                Notedown.LOGGER.error("Failed to save pinned note layout", ex);
            }
        }
    }
}
