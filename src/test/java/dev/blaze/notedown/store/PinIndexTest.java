package dev.blaze.notedown.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PinIndexTest {

    @TempDir
    Path dir;

    @Test
    void emptyWhenMissing() {
        PinIndex idx = PinIndex.load(dir, msg -> fail(msg));
        assertTrue(idx.pins().isEmpty());
        assertFalse(idx.isPinned("a.md"));
    }

    @Test
    void roundTripsInInsertionOrder() throws Exception {
        Files.writeString(dir.resolve("b.md"), "");
        Files.writeString(dir.resolve("a.md"), "");
        PinIndex idx = PinIndex.load(dir, msg -> fail(msg));
        idx.put("b.md", new PinIndex.Pin(0.5, 0.25, 180, 120, 1.0f, 0));
        idx.put("a.md", new PinIndex.Pin(0.1, 0.1, 60, 30, 1.5f, 12));
        idx.save();
        PinIndex back = PinIndex.load(dir, msg -> fail(msg));
        assertEquals(List.of("b.md", "a.md"), new ArrayList<>(back.pins().keySet()));
        assertEquals(new PinIndex.Pin(0.1, 0.1, 60, 30, 1.5f, 12), back.get("a.md").orElseThrow());
    }

    @Test
    void prunesMissingFilesOnLoad() throws Exception {
        Files.writeString(dir.resolve("keep.md"), "");
        Files.writeString(dir.resolve(PinIndex.FILE),
                "{\"version\":1,\"pins\":{\"keep.md\":{\"x\":0,\"y\":0,\"w\":100,\"h\":50,\"scale\":1,\"scroll\":0},"
                        + "\"gone.md\":{\"x\":0,\"y\":0,\"w\":100,\"h\":50,\"scale\":1,\"scroll\":0}}}");
        PinIndex idx = PinIndex.load(dir, msg -> fail(msg));
        assertEquals(List.of("keep.md"), new ArrayList<>(idx.pins().keySet()));
    }

    @Test
    void quarantinesUnreadableIndex() throws Exception {
        Files.writeString(dir.resolve(PinIndex.FILE), "{ nope");
        List<String> problems = new ArrayList<>();
        PinIndex idx = PinIndex.load(dir, problems::add);
        assertTrue(idx.pins().isEmpty());
        assertEquals(1, problems.size());
        assertTrue(Files.exists(dir.resolve(PinIndex.FILE + ".bad")));
        assertFalse(Files.exists(dir.resolve(PinIndex.FILE)));
    }

    @Test
    void renameKeepsPin() throws Exception {
        Files.writeString(dir.resolve("old.md"), "");
        PinIndex idx = PinIndex.load(dir, msg -> fail(msg));
        idx.put("old.md", new PinIndex.Pin(0.2, 0.2, 100, 50, 1f, 0));
        idx.rename("old.md", "new.md");
        assertFalse(idx.isPinned("old.md"));
        assertTrue(idx.isPinned("new.md"));
        idx.remove("new.md");
        assertTrue(idx.pins().isEmpty());
    }
}
