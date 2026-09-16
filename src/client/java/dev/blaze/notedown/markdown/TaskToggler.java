package dev.blaze.notedown.markdown;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TaskToggler {

    private static final Pattern TASK = Pattern.compile("^(\\s*(?:[-*+]|\\d+[.)])\\s+\\[)([ xX])(\\].*)$");

    private TaskToggler() {}

    /** Flips the checkbox on the given zero-based line; returns the body unchanged if that line is not a task. */
    public static String toggleLine(String body, int lineIndex) {
        if (lineIndex < 0) {
            return body;
        }
        String[] lines = body.split("\n", -1);
        if (lineIndex >= lines.length) {
            return body;
        }
        Matcher m = TASK.matcher(lines[lineIndex]);
        if (!m.matches()) {
            return body;
        }
        String box = m.group(2).equals(" ") ? "x" : " ";
        lines[lineIndex] = m.group(1) + box + m.group(3);
        return String.join("\n", lines);
    }
}
