package dev.blaze.notedown.markdown;

public record TextStyle(boolean bold, boolean italic, boolean strike, boolean underline, boolean code, boolean muted, String link) {

    public static final TextStyle NORMAL = new TextStyle(false, false, false, false, false, false, null);

    public TextStyle withBold(boolean v) {
        return new TextStyle(v, italic, strike, underline, code, muted, link);
    }

    public TextStyle withItalic(boolean v) {
        return new TextStyle(bold, v, strike, underline, code, muted, link);
    }

    public TextStyle withStrike(boolean v) {
        return new TextStyle(bold, italic, v, underline, code, muted, link);
    }

    public TextStyle withUnderline(boolean v) {
        return new TextStyle(bold, italic, strike, v, code, muted, link);
    }

    public TextStyle withCode(boolean v) {
        return new TextStyle(bold, italic, strike, underline, v, muted, link);
    }

    public TextStyle withMuted(boolean v) {
        return new TextStyle(bold, italic, strike, underline, code, v, link);
    }

    public TextStyle withLink(String url) {
        return new TextStyle(bold, italic, strike, underline, code, muted, url);
    }
}
