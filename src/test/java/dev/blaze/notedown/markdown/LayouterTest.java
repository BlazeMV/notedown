package dev.blaze.notedown.markdown;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LayouterTest {

    /** 6 px per code point, 7 px bold, 9 px line height: close to Minecraft's default font. */
    static final TextMeasure FAKE = new TextMeasure() {
        @Override
        public int width(String text, boolean bold) {
            return text.codePointCount(0, text.length()) * (bold ? 7 : 6);
        }

        @Override
        public int lineHeight() {
            return 9;
        }
    };

    static Layout layout(String md, int width) {
        return Layouter.layout(MarkdownParser.parse(md), width, CheckedTaskStyle.STRIKE_MUTED, FAKE);
    }

    static List<String> texts(Layout l) {
        return l.lines().stream().map(line -> line.text().strip()).toList();
    }

    @Test
    void singleLineParagraph() {
        Layout l = layout("hello world", 200);
        assertEquals(List.of("hello world"), texts(l));
        assertEquals(1, l.lines().getFirst().runs().size());
        assertEquals(0, l.lines().getFirst().y());
        assertEquals(11, l.height());
    }

    @Test
    void wrapsAtWordBoundaries() {
        Layout l = layout("aaa bbb ccc", 40);
        assertEquals(List.of("aaa", "bbb", "ccc"), texts(l));
        assertEquals(List.of(0, 11, 22), l.lines().stream().map(Layout.Line::y).toList());
        assertEquals(33, l.height());
    }

    @Test
    void breaksOverlongWordsPerCharacter() {
        Layout l = layout("abcdefghij", 30);
        assertEquals(List.of("abcde", "fghij"), texts(l));
    }

    @Test
    void inlineStylesBecomeRuns() {
        Layout l = layout("**b** *i* ~~s~~ `c`", 300);
        List<Layout.Run> runs = l.lines().getFirst().runs();
        assertEquals(7, runs.size());
        assertTrue(runs.get(0).style().bold());
        assertEquals("b", runs.get(0).text());
        assertTrue(runs.get(2).style().italic());
        assertTrue(runs.get(4).style().strike());
        assertTrue(runs.get(6).style().code());
        assertEquals(7 + 6, runs.get(2).x());
    }

    @Test
    void headingsAreScaledAndBold() {
        Layout l = layout("# Title\ntext", 300);
        Layout.Line h = l.lines().get(0);
        assertEquals(1.5f, h.scale());
        assertTrue(h.runs().getFirst().style().bold());
        assertEquals(14 + 2 + 4, l.lines().get(1).y());
        assertEquals(1.25f, layout("## Two", 300).lines().getFirst().scale());
        assertEquals(1.1f, layout("### Three", 300).lines().getFirst().scale());
        assertEquals(1f, layout("#### Four", 300).lines().getFirst().scale());
    }

    @Test
    void hardBreakSplitsSoftBreakJoins() {
        assertEquals(List.of("a", "b"), texts(layout("a\\\nb", 300)));
        assertEquals(List.of("a b"), texts(layout("a\nb", 300)));
    }

    @Test
    void paragraphsAreSeparatedByGap() {
        Layout l = layout("a\n\nb", 300);
        assertEquals(9 + 2 + 4, l.lines().get(1).y());
    }

    @Test
    void codeBlockLinesKeepSpacingAndDecoration() {
        Layout l = layout("```\nx\n  y\n```", 300);
        assertEquals(2, l.lines().size());
        for (Layout.Line line : l.lines()) {
            assertEquals(Layout.DecoKind.CODE, line.deco().kind());
            assertEquals(Layouter.CODE_PAD, line.x());
            assertTrue(line.runs().getFirst().style().code());
        }
        assertEquals("  y", l.lines().get(1).runs().getFirst().text());
    }

    @Test
    void thematicBreakIsRuleLine() {
        Layout l = layout("a\n\n---\n\nb", 300);
        assertEquals(Layout.DecoKind.RULE, l.lines().get(1).deco().kind());
        assertTrue(l.lines().get(1).runs().isEmpty());
    }

    @Test
    void linksProduceHitBoxes() {
        Layout l = layout("[go](http://x)", 300);
        assertEquals(1, l.hitBoxes().size());
        Layout.HitBox hit = l.hitBoxes().getFirst();
        assertEquals(Layout.HitKind.LINK, hit.kind());
        assertEquals("http://x", hit.url());
        assertEquals(0, hit.x());
        assertEquals(12, hit.w());
        assertEquals(9, hit.h());
        assertTrue(hit.contains(5, 4));
        assertFalse(hit.contains(13, 4));
        assertTrue(l.lines().getFirst().runs().getFirst().style().underline());
    }

    @Test
    void htmlBlockKeepsItsLines() {
        Layout l = layout("<div>\nraw\n</div>", 300);
        assertEquals(List.of("<div>", "raw", "</div>"), texts(l));
        assertEquals(List.of(0, 11, 22), l.lines().stream().map(Layout.Line::y).toList());
    }

    @Test
    void linkReferenceDefinitionsTakeNoSpace() {
        Layout l = layout("[x]: http://y\n\ntext", 300);
        assertEquals(List.of("text"), texts(l));
        assertEquals(0, l.lines().getFirst().y());
        assertEquals(11, l.height());
        assertEquals(11, layout("See [one][a]\n\n[a]: http://a", 300).height());
    }

    @Test
    void htmlIsRenderedLiterally() {
        assertEquals(List.of("<b>raw</b>"), texts(layout("<b>raw</b>", 300)));
    }

    @Test
    void emptyDocumentHasNoLines() {
        Layout l = layout("", 300);
        assertTrue(l.lines().isEmpty());
        assertEquals(0, l.height());
    }

    @Test
    void tinyWidthIsClampedNotCrashing() {
        assertFalse(layout("word", 1).lines().isEmpty());
    }

    @Test
    void bulletListMarkersAndIndent() {
        Layout l = layout("- a\n- b", 300);
        assertEquals(List.of("a", "b"), texts(l));
        for (Layout.Line line : l.lines()) {
            assertEquals(Layout.DecoKind.BULLET, line.deco().kind());
            assertEquals("•", line.deco().label());
            assertEquals(Layouter.MARKER_WIDTH, line.x());
        }
        assertEquals(11, l.lines().get(1).y());
    }

    @Test
    void nestedListsIndentAndCycleBullets() {
        Layout l = layout("- a\n  - b\n    - c", 300);
        assertEquals(Layouter.MARKER_WIDTH * 2, l.lines().get(1).x());
        assertEquals("◦", l.lines().get(1).deco().label());
        assertEquals("▪", l.lines().get(2).deco().label());
    }

    @Test
    void orderedListsNumberFromStart() {
        Layout l = layout("3. a\n4. b", 300);
        assertEquals("3.", l.lines().get(0).deco().label());
        assertEquals("4.", l.lines().get(1).deco().label());
        assertEquals(Layout.DecoKind.NUMBER, l.lines().get(0).deco().kind());
        assertEquals(16, l.lines().get(0).x());
    }

    @Test
    void orderedListsAlignOnTheWidestNumber() {
        Layout l = layout("9. nine\n10. ten", 300);
        assertEquals(22, l.lines().get(0).x());
        assertEquals(22, l.lines().get(1).x());
        assertEquals("9.", l.lines().get(0).deco().label());
        assertEquals("10.", l.lines().get(1).deco().label());
    }

    @Test
    void taskItemsProduceDecoAndHitBoxes() {
        Layout l = layout("- [ ] a\n- [x] b", 300);
        assertEquals(Layout.DecoKind.TASK, l.lines().get(0).deco().kind());
        assertFalse(l.lines().get(0).deco().checked());
        assertTrue(l.lines().get(1).deco().checked());
        assertEquals(0, l.lines().get(0).deco().sourceLine());
        assertEquals(1, l.lines().get(1).deco().sourceLine());
        assertEquals(2, l.hitBoxes().size());
        Layout.HitBox second = l.hitBoxes().get(1);
        assertEquals(Layout.HitKind.TASK, second.kind());
        assertEquals(0, second.x());
        assertEquals(11, second.y());
        assertEquals(Layouter.TASK_BOX, second.w());
        assertEquals(1, second.sourceLine());
        assertTrue(l.lines().get(1).runs().getFirst().style().strike());
        assertTrue(l.lines().get(1).runs().getFirst().style().muted());
        assertFalse(l.lines().get(0).runs().getFirst().style().strike());
    }

    @Test
    void checkedStyleNoneLeavesTextPlain() {
        Layout l = Layouter.layout(MarkdownParser.parse("- [x] b"), 300, CheckedTaskStyle.NONE, FAKE);
        assertFalse(l.lines().getFirst().runs().getFirst().style().strike());
        assertFalse(l.lines().getFirst().runs().getFirst().style().muted());
    }

    @Test
    void taskSourceLineSurvivesPrecedingContent() {
        Layout l = layout("# Head\n\ntext\n\n- [ ] later", 300);
        assertEquals(4, l.hitBoxes().getFirst().sourceLine());
    }

    @Test
    void tasksInsideCodeBlocksAreNotTasks() {
        assertTrue(layout("```\n- [ ] x\n```", 300).hitBoxes().isEmpty());
    }

    @Test
    void blockquotesAreMarkedAndIndented() {
        Layout l = layout("> q", 300);
        assertTrue(l.lines().getFirst().quoted());
        assertEquals(Layouter.QUOTE_INDENT, l.lines().getFirst().x());
    }

    @Test
    void looseListsGetParagraphGaps() {
        assertEquals(11, layout("- a\n- b", 300).lines().get(1).y());
        assertEquals(15, layout("- a\n\n- b", 300).lines().get(1).y());
    }

    @Test
    void emptyListItemStillGetsMarker() {
        Layout l = layout("-", 300);
        assertEquals(1, l.lines().size());
        assertEquals(Layout.DecoKind.BULLET, l.lines().getFirst().deco().kind());
        assertTrue(l.lines().getFirst().runs().isEmpty());
    }

    @Test
    void continuationParagraphInItemHasNoMarker() {
        Layout l = layout("- a\n\n  second", 300);
        assertEquals(Layout.DecoKind.NONE, l.lines().get(1).deco().kind());
        assertEquals(Layouter.MARKER_WIDTH, l.lines().get(1).x());
    }
}
