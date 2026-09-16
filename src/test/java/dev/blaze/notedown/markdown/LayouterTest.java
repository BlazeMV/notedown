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
}
