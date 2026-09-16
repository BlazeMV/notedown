package dev.blaze.notedown.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskTogglerTest {

    @Test
    void togglesOnlyTheGivenLine() {
        String body = "- [ ] a\n- [x] b\n- [ ] c";
        assertEquals("- [x] a\n- [x] b\n- [ ] c", TaskToggler.toggleLine(body, 0));
        assertEquals("- [ ] a\n- [ ] b\n- [ ] c", TaskToggler.toggleLine(body, 1));
    }

    @Test
    void acceptsOtherMarkersAndIndentation() {
        assertEquals("  * [x] a", TaskToggler.toggleLine("  * [ ] a", 0));
        assertEquals("1. [ ] a", TaskToggler.toggleLine("1. [X] a", 0));
    }

    @Test
    void ignoresNonTaskLinesAndBadIndexes() {
        assertEquals("plain\n- [ ] a", TaskToggler.toggleLine("plain\n- [ ] a", 0));
        assertEquals("- [ ] a", TaskToggler.toggleLine("- [ ] a", 5));
        assertEquals("- [ ] a", TaskToggler.toggleLine("- [ ] a", -1));
    }

    @Test
    void preservesTrailingNewline() {
        assertEquals("- [x] a\n", TaskToggler.toggleLine("- [ ] a\n", 0));
    }
}
