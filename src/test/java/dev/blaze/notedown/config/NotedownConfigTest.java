package dev.blaze.notedown.config;

import dev.blaze.notedown.markdown.CheckedTaskStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotedownConfigTest {

    @TempDir
    Path dir;

    @Test
    void defaultsWhenMissing() {
        List<String> problems = new ArrayList<>();
        NotedownConfig c = NotedownConfig.load(dir.resolve("notedown.json"), problems::add);
        assertEquals(0.5f, c.pinnedBackgroundOpacity);
        assertEquals(1.0f, c.pinnedTextScale);
        assertTrue(c.showPinnedInContainers);
        assertTrue(c.showPinnedWithChat);
        assertEquals(CheckedTaskStyle.STRIKE_MUTED, c.checkedTaskStyle);
        assertTrue(c.showInsertButtons);
        assertFalse(c.pinnedHidden);
        assertTrue(problems.isEmpty());
    }

    @Test
    void roundTrips() throws Exception {
        Path file = dir.resolve("notedown.json");
        NotedownConfig c = new NotedownConfig();
        c.pinnedBackgroundOpacity = 0.8f;
        c.checkedTaskStyle = CheckedTaskStyle.NONE;
        c.pinnedHidden = true;
        c.save(file);
        NotedownConfig back = NotedownConfig.load(file, msg -> fail(msg));
        assertEquals(0.8f, back.pinnedBackgroundOpacity);
        assertEquals(CheckedTaskStyle.NONE, back.checkedTaskStyle);
        assertTrue(back.pinnedHidden);
        assertFalse(Files.exists(dir.resolve("notedown.json.tmp")));
    }

    @Test
    void clampsOutOfRange() throws Exception {
        Path file = dir.resolve("notedown.json");
        Files.writeString(file, "{\"pinnedBackgroundOpacity\": 7, \"pinnedTextScale\": 0.01, \"checkedTaskStyle\": null}");
        NotedownConfig c = NotedownConfig.load(file, msg -> fail(msg));
        assertEquals(1.0f, c.pinnedBackgroundOpacity);
        assertEquals(0.5f, c.pinnedTextScale);
        assertEquals(CheckedTaskStyle.STRIKE_MUTED, c.checkedTaskStyle);
    }

    @Test
    void reportsUnreadableFile() throws Exception {
        Path file = dir.resolve("notedown.json");
        Files.writeString(file, "{ not json");
        List<String> problems = new ArrayList<>();
        NotedownConfig c = NotedownConfig.load(file, problems::add);
        assertEquals(1, problems.size());
        assertEquals(0.5f, c.pinnedBackgroundOpacity);
    }
}
