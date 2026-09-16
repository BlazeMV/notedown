package dev.blaze.notedown.markdown;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EditorCommands {

    public record Edit(String text, int cursor) {}

    public static final int MAX_TITLE = 40;

    private static final Pattern LIST_LINE = Pattern.compile("^(\\s*)([-*+]|\\d{1,9}[.)])(\\s+)(\\[[ xX]\\]\\s+)?(.*)$");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*)$");
    private static final Pattern LEADING_MARKERS = Pattern.compile("^(?:[-*+]|\\d+[.)])\\s+(?:\\[[ xX]\\]\\s*)?");
    private static final Pattern INLINE_MARKERS = Pattern.compile("[*_`~>]");

    private EditorCommands() {}

    public static Edit enter(String text, int cursor) {
        int lineStart = lineStart(text, cursor);
        Matcher m = LIST_LINE.matcher(text.substring(lineStart, cursor));
        if (!m.matches()) {
            return insert(text, cursor, "\n");
        }
        if (m.group(5).isEmpty() && cursor >= lineEnd(text, cursor)) {
            return new Edit(text.substring(0, lineStart) + text.substring(cursor), lineStart);
        }
        String marker = m.group(2);
        if (Character.isDigit(marker.charAt(0))) {
            String delimiter = marker.substring(marker.length() - 1);
            int n = Integer.parseInt(marker.substring(0, marker.length() - 1));
            marker = (n + 1) + delimiter;
        }
        String prefix = "\n" + m.group(1) + marker + m.group(3) + (m.group(4) != null ? "[ ] " : "");
        return insert(text, cursor, prefix);
    }

    public static Edit indent(String text, int cursor, boolean outdent) {
        int ls = lineStart(text, cursor);
        if (!outdent) {
            return new Edit(text.substring(0, ls) + "  " + text.substring(ls), cursor + 2);
        }
        int remove = 0;
        while (remove < 2 && ls + remove < text.length() && text.charAt(ls + remove) == ' ') {
            remove++;
        }
        return new Edit(text.substring(0, ls) + text.substring(ls + remove), Math.max(ls, cursor - remove));
    }

    public static Edit wrap(String text, int selStart, int selEnd, String marker) {
        int a = Math.min(selStart, selEnd);
        int b = Math.max(selStart, selEnd);
        String sel = text.substring(a, b);
        int ml = marker.length();
        if (sel.length() >= 2 * ml && sel.startsWith(marker) && sel.endsWith(marker)) {
            String inner = sel.substring(ml, sel.length() - ml);
            return new Edit(text.substring(0, a) + inner + text.substring(b), a + inner.length());
        }
        if (a >= ml && b + ml <= text.length() && text.startsWith(marker, a - ml) && text.startsWith(marker, b)) {
            return new Edit(text.substring(0, a - ml) + sel + text.substring(b + ml), a - ml + sel.length());
        }
        String wrapped = text.substring(0, a) + marker + sel + marker + text.substring(b);
        return new Edit(wrapped, sel.isEmpty() ? a + ml : a + ml + sel.length() + ml);
    }

    public static Edit toggleTask(String text, int cursor) {
        int ls = lineStart(text, cursor);
        int le = lineEnd(text, cursor);
        String line = text.substring(ls, le);
        String replaced = TaskToggler.flip(line).orElseGet(() -> {
            Matcher li = LIST_LINE.matcher(line);
            return li.matches() ? li.group(1) + li.group(2) + li.group(3) + "[ ] " + li.group(5) : "- [ ] " + line;
        });
        String result = text.substring(0, ls) + replaced + text.substring(le);
        return new Edit(result, Math.min(result.length(), cursor + (replaced.length() - line.length())));
    }

    public static String titleFromBody(String body) {
        for (String raw : body.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher h = HEADING.matcher(line);
            if (h.matches()) {
                line = h.group(1);
            }
            line = LEADING_MARKERS.matcher(line).replaceFirst("");
            line = INLINE_MARKERS.matcher(line).replaceAll("").strip();
            if (line.isEmpty()) {
                continue;
            }
            return line.length() > MAX_TITLE ? line.substring(0, MAX_TITLE).strip() : line;
        }
        return "";
    }

    static int lineStart(String text, int cursor) {
        if (cursor <= 0) {
            return 0;
        }
        return text.lastIndexOf('\n', cursor - 1) + 1;
    }

    static int lineEnd(String text, int cursor) {
        int end = text.indexOf('\n', cursor);
        return end < 0 ? text.length() : end;
    }

    private static Edit insert(String text, int at, String s) {
        return new Edit(text.substring(0, at) + s + text.substring(at), at + s.length());
    }
}
