package dev.blaze.notedown.ui;

public final class Scroller {

    private static final int MIN_THUMB = 8;
    private static final int PAGE_OVERLAP = 10;

    private int content;
    private int view;
    private int amount;

    public void update(int contentHeight, int viewHeight) {
        content = Math.max(0, contentHeight);
        view = Math.max(1, viewHeight);
        scrollTo(amount);
    }

    public int amount() {
        return amount;
    }

    public int max() {
        return Math.max(0, content - view);
    }

    public boolean visible() {
        return content > view;
    }

    public void scrollBy(int delta) {
        scrollTo(amount + delta);
    }

    public void scrollTo(int value) {
        amount = Math.max(0, Math.min(max(), value));
    }

    public void pageBy(int direction) {
        scrollBy(direction * Math.max(1, view - PAGE_OVERLAP));
    }

    public int thumbHeight(int track) {
        return visible() ? Math.max(MIN_THUMB, (int) ((long) track * view / content)) : track;
    }

    public int thumbY(int track) {
        int room = track - thumbHeight(track);
        return max() == 0 || room <= 0 ? 0 : (int) ((long) room * amount / max());
    }

    public void dragTo(int track, int thumbTop) {
        int room = track - thumbHeight(track);
        if (room <= 0) {
            return;
        }
        scrollTo((int) Math.round((double) Math.max(0, Math.min(room, thumbTop)) * max() / room));
    }
}
