package dev.blaze.notedown.markdown;

import java.util.List;

public record Layout(List<Line> lines, List<HitBox> hitBoxes, int height) {

    public static final Layout EMPTY = new Layout(List.of(), List.of(), 0);

    public enum DecoKind { NONE, BULLET, NUMBER, TASK, CODE, RULE }

    public enum HitKind { TASK, LINK }

    public record Deco(DecoKind kind, String label, boolean checked, int sourceLine) {
        public static final Deco NONE = new Deco(DecoKind.NONE, "", false, -1);
        public static final Deco CODE = new Deco(DecoKind.CODE, "", false, -1);
        public static final Deco RULE = new Deco(DecoKind.RULE, "", false, -1);

        public static Deco bullet(String label) {
            return new Deco(DecoKind.BULLET, label, false, -1);
        }

        public static Deco number(String label) {
            return new Deco(DecoKind.NUMBER, label, false, -1);
        }

        public static Deco task(boolean checked, int sourceLine) {
            return new Deco(DecoKind.TASK, "", checked, sourceLine);
        }
    }

    public record Run(String text, TextStyle style, int x) {}

    public record Line(int x, int y, float scale, List<Run> runs, Deco deco, boolean quoted, int width) {
        public Line withDeco(Deco d) {
            return new Line(x, y, scale, runs, d, quoted, width);
        }

        public Line withQuoted(boolean q) {
            return new Line(x, y, scale, runs, deco, q, width);
        }

        public String text() {
            StringBuilder sb = new StringBuilder();
            for (Run r : runs) {
                sb.append(r.text());
            }
            return sb.toString();
        }
    }

    public record HitBox(HitKind kind, int x, int y, int w, int h, int sourceLine, String url) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }
}
