package dev.blaze.notedown.markdown;

public enum CheckedTaskStyle {
    STRIKE_MUTED, MUTED, NONE;

    public CheckedTaskStyle next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
