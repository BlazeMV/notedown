package dev.blaze.notedown.markdown;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class UndoStack {

    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private final int limit;
    private final long coalesceMs;
    private String current;
    private long lastRecord = Long.MIN_VALUE / 2;

    public UndoStack(String initial, int limit, long coalesceMs) {
        this.current = initial;
        this.limit = limit;
        this.coalesceMs = coalesceMs;
    }

    public String current() {
        return current;
    }

    public void record(String value, long nowMs) {
        if (value.equals(current)) {
            return;
        }
        boolean quickTyping = nowMs - lastRecord < coalesceMs && Math.abs(value.length() - current.length()) == 1;
        if (!quickTyping || undo.isEmpty()) {
            undo.push(current);
            while (undo.size() > limit) {
                undo.removeLast();
            }
        }
        current = value;
        redo.clear();
        lastRecord = nowMs;
    }

    public Optional<String> undo() {
        if (undo.isEmpty()) {
            return Optional.empty();
        }
        redo.push(current);
        current = undo.pop();
        lastRecord = Long.MIN_VALUE / 2;
        return Optional.of(current);
    }

    public Optional<String> redo() {
        if (redo.isEmpty()) {
            return Optional.empty();
        }
        undo.push(current);
        current = redo.pop();
        lastRecord = Long.MIN_VALUE / 2;
        return Optional.of(current);
    }
}
