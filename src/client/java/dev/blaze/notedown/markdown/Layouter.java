package dev.blaze.notedown.markdown;

import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

import java.util.ArrayList;
import java.util.List;

public final class Layouter {

    public static final int MARKER_WIDTH = 12;
    public static final int TASK_BOX = 9;
    public static final int QUOTE_INDENT = 8;
    public static final int CODE_PAD = 4;
    public static final int PARAGRAPH_GAP = 4;
    public static final int LINE_GAP = 2;
    public static final int MIN_WIDTH = 20;

    private static final float[] HEADING_SCALE = {1.5f, 1.25f, 1.1f, 1f, 1f, 1f};
    private static final String[] BULLETS = {"•", "◦", "▪"};

    private final TextMeasure measure;
    private final int width;
    private final CheckedTaskStyle checkedStyle;
    private final List<Layout.Line> lines = new ArrayList<>();
    private final List<Layout.HitBox> hits = new ArrayList<>();
    private int y;

    private Layouter(TextMeasure measure, int width, CheckedTaskStyle checkedStyle) {
        this.measure = measure;
        this.width = width;
        this.checkedStyle = checkedStyle;
    }

    public static Layout layout(Node document, int width, CheckedTaskStyle checkedStyle, TextMeasure measure) {
        Layouter l = new Layouter(measure, Math.max(MIN_WIDTH, width), checkedStyle);
        l.blocks(document, 0, 0, false);
        int height = l.lines.isEmpty() ? 0 : Math.max(0, l.y - PARAGRAPH_GAP);
        return new Layout(List.copyOf(l.lines), List.copyOf(l.hits), height);
    }

    private void blocks(Node parent, int indent, int depth, boolean tight) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
            block(n, indent, depth, tight);
        }
    }

    private void block(Node n, int indent, int depth, boolean tight) {
        int before = lines.size();
        switch (n) {
            case Heading h -> {
                float scale = HEADING_SCALE[Math.min(HEADING_SCALE.length, Math.max(1, h.getLevel())) - 1];
                paragraph(inlines(h, TextStyle.NORMAL.withBold(true)), indent, scale);
                gap();
            }
            case Paragraph p -> {
                paragraph(inlines(p, TextStyle.NORMAL), indent, 1f);
                if (!tight) {
                    gap();
                }
            }
            case FencedCodeBlock c -> code(c.getLiteral(), indent);
            case IndentedCodeBlock c -> code(c.getLiteral(), indent);
            case ThematicBreak t -> {
                lines.add(new Layout.Line(indent, y, 1f, List.of(), Layout.Deco.RULE, false, width - indent));
                y += measure.lineHeight() + LINE_GAP;
                gap();
            }
            case HtmlBlock h -> {
                for (String line : h.getLiteral().strip().split("\n")) {
                    paragraph(List.of(new Layout.Run(line, TextStyle.NORMAL, 0)), indent, 1f);
                }
                if (lines.size() > before) {
                    gap();
                }
            }
            case LinkReferenceDefinition d -> { }
            case BlockQuote q -> quote(q, indent, depth);
            case BulletList l -> {
                list(l, indent, depth, null);
                if (!tight) {
                    gap();
                }
            }
            case OrderedList l -> {
                list(l, indent, depth, l.getMarkerStartNumber() == null ? 1 : l.getMarkerStartNumber());
                if (!tight) {
                    gap();
                }
            }
            default -> {
                paragraph(inlines(n, TextStyle.NORMAL), indent, 1f);
                if (lines.size() > before) {
                    gap();
                }
            }
        }
    }

    private void gap() {
        y += PARAGRAPH_GAP;
    }

    private void code(String literal, int indent) {
        String text = literal.endsWith("\n") ? literal.substring(0, literal.length() - 1) : literal;
        int x = indent + CODE_PAD;
        for (String raw : text.split("\n", -1)) {
            String line = raw.replace("\t", "    ");
            List<Layout.Run> runs = line.isEmpty() ? List.of() : List.of(new Layout.Run(line, TextStyle.NORMAL.withCode(true), 0));
            lines.add(new Layout.Line(x, y, 1f, runs, Layout.Deco.CODE, false, width - indent - 2 * CODE_PAD));
            y += measure.lineHeight() + LINE_GAP;
        }
        gap();
    }

    private void quote(BlockQuote q, int indent, int depth) {
        int start = lines.size();
        blocks(q, indent + QUOTE_INDENT, depth, false);
        for (int i = start; i < lines.size(); i++) {
            lines.set(i, lines.get(i).withQuoted(true));
        }
    }

    private void list(ListBlock list, int indent, int depth, Integer start) {
        int number = start == null ? 0 : start;
        boolean tight = list.isTight();
        int itemCount = 0;
        for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            itemCount++;
        }
        int markerWidth = start == null ? MARKER_WIDTH
                : Math.max(MARKER_WIDTH, measure.width((start + Math.max(0, itemCount - 1)) + ".", false) + 4);
        for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            if (!(item instanceof ListItem li)) {
                continue;
            }
            TaskListItemMarker marker = taskMarker(li);
            String label = start == null ? BULLETS[depth % BULLETS.length] : (number++) + ".";
            int textIndent = indent + markerWidth;
            TextStyle base = marker == null ? TextStyle.NORMAL : checkedStyle(marker.isChecked());
            int before = lines.size();
            for (Node child = li.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof TaskListItemMarker) {
                    continue;
                }
                if (child instanceof Paragraph p) {
                    paragraph(inlines(p, base), textIndent, 1f);
                    if (!tight) {
                        gap();
                    }
                } else {
                    block(child, textIndent, depth + 1, tight);
                }
            }
            if (lines.size() == before) {
                lines.add(new Layout.Line(textIndent, y, 1f, List.of(), Layout.Deco.NONE, false, 0));
                y += measure.lineHeight() + LINE_GAP;
                if (!tight) {
                    gap();
                }
            }
            Layout.Line first = lines.get(before);
            int sourceLine = li.getSourceSpans().isEmpty() ? -1 : li.getSourceSpans().getFirst().getLineIndex();
            Layout.Deco deco = marker != null ? Layout.Deco.task(marker.isChecked(), sourceLine)
                    : start == null ? Layout.Deco.bullet(label) : Layout.Deco.number(label);
            lines.set(before, first.withDeco(deco));
            if (marker != null) {
                int boxY = first.y() + (measure.lineHeight() - TASK_BOX) / 2;
                hits.add(new Layout.HitBox(Layout.HitKind.TASK, first.x() - MARKER_WIDTH, boxY, TASK_BOX, TASK_BOX, sourceLine, null));
            }
        }
    }

    private static TaskListItemMarker taskMarker(ListItem li) {
        return li.getFirstChild() instanceof TaskListItemMarker m ? m : null;
    }

    private TextStyle checkedStyle(boolean checked) {
        if (!checked) {
            return TextStyle.NORMAL;
        }
        return switch (checkedStyle) {
            case STRIKE_MUTED -> TextStyle.NORMAL.withStrike(true).withMuted(true);
            case MUTED -> TextStyle.NORMAL.withMuted(true);
            case NONE -> TextStyle.NORMAL;
        };
    }

    private List<Layout.Run> inlines(Node container, TextStyle base) {
        List<Layout.Run> out = new ArrayList<>();
        collectInlines(container, base, out);
        return out;
    }

    private void collectInlines(Node parent, TextStyle style, List<Layout.Run> out) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
            switch (n) {
                case Text t -> out.add(new Layout.Run(t.getLiteral(), style, 0));
                case SoftLineBreak s -> out.add(new Layout.Run(" ", style, 0));
                case HardLineBreak h -> out.add(new Layout.Run("\n", style, 0));
                case Emphasis e -> collectInlines(e, style.withItalic(true), out);
                case StrongEmphasis e -> collectInlines(e, style.withBold(true), out);
                case Strikethrough s -> collectInlines(s, style.withStrike(true), out);
                case Code c -> out.add(new Layout.Run(c.getLiteral(), style.withCode(true), 0));
                case Link l -> collectInlines(l, style.withLink(l.getDestination()).withUnderline(true), out);
                case HtmlInline h -> out.add(new Layout.Run(h.getLiteral(), style, 0));
                case TaskListItemMarker m -> { }
                default -> collectInlines(n, style, out);
            }
        }
    }

    /** Word-wraps runs into lines starting at indent. */
    private void paragraph(List<Layout.Run> runs, int indent, float scale) {
        Wrapper w = new Wrapper(indent, scale);
        for (Layout.Run run : runs) {
            w.add(run.text(), run.style());
        }
        w.finish();
    }

    private final class Wrapper {
        private final int indent;
        private final float scale;
        private final int avail;
        private List<Layout.Run> runs = new ArrayList<>();
        private int cx;

        Wrapper(int indent, float scale) {
            this.indent = indent;
            this.scale = scale;
            this.avail = Math.max(1, Math.round((width - indent) / scale));
        }

        void add(String text, TextStyle style) {
            if (text.equals("\n")) {
                flush();
                return;
            }
            for (String token : text.split("(?<=\\s)(?=\\S)")) {
                if (cx > 0 && cx + measure.width(token.stripTrailing(), style.bold()) > avail) {
                    flush();
                }
                if (cx == 0) {
                    token = token.stripLeading();
                    if (token.isEmpty()) {
                        continue;
                    }
                }
                int w = measure.width(token, style.bold());
                if (w > avail) {
                    addBroken(token, style);
                } else {
                    append(token, style, w);
                }
            }
        }

        private void addBroken(String token, TextStyle style) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < token.length()) {
                int cp = token.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                i += Character.charCount(cp);
                if (cx + measure.width(sb + ch, style.bold()) > avail && (cx > 0 || !sb.isEmpty())) {
                    append(sb.toString(), style, measure.width(sb.toString(), style.bold()));
                    sb.setLength(0);
                    flush();
                }
                sb.append(ch);
            }
            if (!sb.isEmpty()) {
                append(sb.toString(), style, measure.width(sb.toString(), style.bold()));
            }
        }

        private void append(String text, TextStyle style, int w) {
            if (text.isEmpty()) {
                return;
            }
            if (!runs.isEmpty() && runs.getLast().style().equals(style)) {
                Layout.Run last = runs.getLast();
                runs.set(runs.size() - 1, new Layout.Run(last.text() + text, style, last.x()));
            } else {
                runs.add(new Layout.Run(text, style, cx));
            }
            cx += w;
        }

        void flush() {
            int lh = Math.round(measure.lineHeight() * scale);
            for (Layout.Run run : runs) {
                if (run.style().link() != null) {
                    int w = Math.round(measure.width(run.text(), run.style().bold()) * scale);
                    hits.add(new Layout.HitBox(Layout.HitKind.LINK, indent + Math.round(run.x() * scale), y, w, lh, -1, run.style().link()));
                }
            }
            lines.add(new Layout.Line(indent, y, scale, List.copyOf(runs), Layout.Deco.NONE, false, Math.round(cx * scale)));
            y += lh + LINE_GAP;
            runs = new ArrayList<>();
            cx = 0;
        }

        void finish() {
            if (!runs.isEmpty()) {
                flush();
            }
        }
    }
}
