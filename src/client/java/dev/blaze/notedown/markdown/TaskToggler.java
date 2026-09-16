package dev.blaze.notedown.markdown;

import java.util.Optional;
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
        Optional<String> flipped = flip(lines[lineIndex]);
        if (flipped.isEmpty()) {
            return body;
        }
        lines[lineIndex] = flipped.get();
        return String.join("\n", lines);
    }

    /** The line with its checkbox flipped, or empty when the line is not a task item. */
    static Optional<String> flip(String line) {
        Matcher m = TASK.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(m.group(1) + (m.group(2).equals(" ") ? "x" : " ") + m.group(3));
    }
}
