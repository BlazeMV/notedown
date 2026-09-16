package dev.blaze.notedown.markdown;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UndoStackTest {

    @Test
    void undoAndRedo() {
        UndoStack s = new UndoStack("", 100, 0);
        s.record("a", 0);
        s.record("ab", 5000);
        assertEquals(Optional.of("a"), s.undo());
        assertEquals(Optional.of(""), s.undo());
        assertEquals(Optional.empty(), s.undo());
        assertEquals(Optional.of("a"), s.redo());
        assertEquals(Optional.of("ab"), s.redo());
        assertEquals(Optional.empty(), s.redo());
    }

    @Test
    void recordingClearsRedoAndIgnoresNoChange() {
        UndoStack s = new UndoStack("", 100, 0);
        s.record("a", 0);
        s.undo();
        s.record("b", 5000);
        assertEquals(Optional.empty(), s.redo());
        s.record("b", 6000);
        assertEquals(Optional.of(""), s.undo());
    }

    @Test
    void coalescesQuickSingleCharacterTyping() {
        UndoStack s = new UndoStack("", 100, 700);
        s.record("h", 0);
        s.record("he", 100);
        s.record("hey", 200);
        assertEquals(Optional.of(""), s.undo());
        assertEquals("", s.current());
    }

    @Test
    void doesNotCoalesceAfterPause() {
        UndoStack s = new UndoStack("", 100, 700);
        s.record("h", 0);
        s.record("he", 1000);
        assertEquals(Optional.of("h"), s.undo());
    }

    @Test
    void limitDropsOldest() {
        UndoStack s = new UndoStack("0", 2, 0);
        s.record("1", 0);
        s.record("2", 5000);
        s.record("3", 10000);
        assertEquals(Optional.of("2"), s.undo());
        assertEquals(Optional.of("1"), s.undo());
        assertEquals(Optional.empty(), s.undo());
    }
}
