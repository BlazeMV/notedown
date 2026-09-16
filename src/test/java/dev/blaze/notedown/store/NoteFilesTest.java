package dev.blaze.notedown.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NoteFilesTest {

    @TempDir
    Path dir;

    @Test
    void sanitizeReplacesIllegalCharacters() {
        assertEquals("a_b_c_d_e_f_g_h_i", NoteFiles.sanitize("a/b\\c:d*e?f\"g<h>i"));
        assertEquals("tabs and newlines", NoteFiles.sanitize("tabs\tand\nnewlines"));
        assertEquals("trimmed", NoteFiles.sanitize("  trimmed  "));
        assertEquals("no trailing dots", NoteFiles.sanitize("no trailing dots..."));
    }

    @Test
    void sanitizeFallsBackToUntitled() {
        assertEquals("Untitled", NoteFiles.sanitize(""));
        assertEquals("Untitled", NoteFiles.sanitize("   "));
        assertEquals("Untitled", NoteFiles.sanitize("..."));
        assertEquals("Untitled", NoteFiles.sanitize(null));
    }

    @Test
    void titleOfStripsExtension() {
        assertEquals("Starter House", NoteFiles.titleOf(Path.of("x", "Starter House.md")));
        assertEquals("Odd.name", NoteFiles.titleOf(Path.of("Odd.name.md")));
    }

    @Test
    void isNoteChecksExtensionAndFileType() throws Exception {
        Path note = Files.writeString(dir.resolve("a.MD"), "x");
        Path txt = Files.writeString(dir.resolve("b.txt"), "x");
        Path folder = Files.createDirectory(dir.resolve("c.md"));
        assertTrue(NoteFiles.isNote(note));
        assertFalse(NoteFiles.isNote(txt));
        assertFalse(NoteFiles.isNote(folder));
    }

    @Test
    void uncollidingAppendsCounter() throws Exception {
        Files.writeString(dir.resolve("Base.md"), "");
        Files.writeString(dir.resolve("base 2.md"), "");
        assertEquals(dir.resolve("Base 3.md"), NoteFiles.uncolliding(dir, "Base", null));
        assertEquals(dir.resolve("Fresh.md"), NoteFiles.uncolliding(dir, "Fresh", null));
    }

    @Test
    void uncollidingKeepsOwnFile() throws Exception {
        Path own = Files.writeString(dir.resolve("Base.md"), "");
        assertEquals(own, NoteFiles.uncolliding(dir, "Base", own));
    }
}
