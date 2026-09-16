package dev.blaze.notedown.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditorCommandsTest {

    @Test
    void enterContinuesBulletList() {
        EditorCommands.Edit e = EditorCommands.enter("- apples", 8);
        assertEquals("- apples\n- ", e.text());
        assertEquals(11, e.cursor());
    }

    @Test
    void enterContinuesTaskAndNumberedLists() {
        assertEquals("- [x] done\n- [ ] ", EditorCommands.enter("- [x] done", 10).text());
        assertEquals("  2. two\n  3. ", EditorCommands.enter("  2. two", 8).text());
        assertEquals("1) a\n2) ", EditorCommands.enter("1) a", 4).text());
    }

    @Test
    void enterOnEmptyItemRemovesMarker() {
        EditorCommands.Edit e = EditorCommands.enter("- a\n- ", 6);
        assertEquals("- a\n", e.text());
        assertEquals(4, e.cursor());
        assertEquals("- a\n", EditorCommands.enter("- a\n- [ ] ", 10).text());
    }

    @Test
    void enterMidLineSplitsPlainText() {
        EditorCommands.Edit e = EditorCommands.enter("hello world", 5);
        assertEquals("hello\n world", e.text());
        assertEquals(6, e.cursor());
        assertEquals("\n", EditorCommands.enter("", 0).text());
    }

    @Test
    void indentAndOutdent() {
        assertEquals(new EditorCommands.Edit("  - a", 5), EditorCommands.indent("- a", 3, false));
        assertEquals(new EditorCommands.Edit("- a", 3), EditorCommands.indent("  - a", 5, true));
        assertEquals(new EditorCommands.Edit("- a", 0), EditorCommands.indent(" - a", 1, true));
        assertEquals(new EditorCommands.Edit("x\n  y", 5), EditorCommands.indent("x\ny", 3, false));
    }

    @Test
    void wrapSelectionAndUnwrap() {
        assertEquals(new EditorCommands.Edit("a **b** c", 7), EditorCommands.wrap("a b c", 2, 3, "**"));
        assertEquals(new EditorCommands.Edit("a b c", 3), EditorCommands.wrap("a **b** c", 4, 5, "**"));
        assertEquals(new EditorCommands.Edit("a b c", 3), EditorCommands.wrap("a **b** c", 2, 7, "**"));
        assertEquals(new EditorCommands.Edit("**", 1), EditorCommands.wrap("", 0, 0, "*"));
    }

    @Test
    void toggleTaskCyclesStates() {
        assertEquals("- [ ] plain", EditorCommands.toggleTask("plain", 3).text());
        assertEquals("- [ ] item", EditorCommands.toggleTask("- item", 3).text());
        assertEquals("- [x] item", EditorCommands.toggleTask("- [ ] item", 3).text());
        assertEquals("- [ ] item", EditorCommands.toggleTask("- [x] item", 3).text());
        EditorCommands.Edit e = EditorCommands.toggleTask("a\nb", 3);
        assertEquals("a\n- [ ] b", e.text());
        assertEquals(9, e.cursor());
    }

    @Test
    void titleFromBody() {
        assertEquals("Starter House", EditorCommands.titleFromBody("\n\n# Starter House\nx"));
        assertEquals("do it", EditorCommands.titleFromBody("- [ ] **do** _it_"));
        assertEquals("", EditorCommands.titleFromBody("  \n"));
        assertEquals(40, EditorCommands.titleFromBody("x".repeat(60)).length());
    }
}
