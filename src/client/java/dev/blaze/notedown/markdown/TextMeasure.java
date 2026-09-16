package dev.blaze.notedown.markdown;

public interface TextMeasure {
    int width(String text, boolean bold);

    int lineHeight();
}
