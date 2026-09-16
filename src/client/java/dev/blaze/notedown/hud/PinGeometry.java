package dev.blaze.notedown.hud;

import dev.blaze.notedown.store.PinIndex;

public final class PinGeometry {

    public static final int MIN_W = 60;
    public static final int MIN_H = 30;
    public static final int PAD = 5;
    public static final int HEADER = 12;
    public static final int HANDLE = 8;
    public static final int EDGE = 6;
    public static final int GAP = 6;

    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }

        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }
    }

    private PinGeometry() {}

    public static Rect rect(PinIndex.Pin pin, int screenW, int screenH) {
        int w = clamp(pin.w(), MIN_W, Math.max(MIN_W, screenW));
        int h = clamp(pin.h(), MIN_H, Math.max(MIN_H, screenH));
        int x = clamp((int) Math.round(pin.x() * screenW), 0, Math.max(0, screenW - w));
        int y = clamp((int) Math.round(pin.y() * screenH), 0, Math.max(0, screenH - h));
        return new Rect(x, y, w, h);
    }

    public static PinIndex.Pin moved(PinIndex.Pin pin, Rect r, int dx, int dy, int screenW, int screenH) {
        int nx = clamp(r.x() + dx, 0, Math.max(0, screenW - r.w()));
        int ny = clamp(r.y() + dy, 0, Math.max(0, screenH - r.h()));
        return pin.withPosition((double) nx / screenW, (double) ny / screenH);
    }

    public static PinIndex.Pin resized(PinIndex.Pin pin, Rect r, int dx, int dy, int screenW, int screenH) {
        int nw = clamp(r.w() + dx, MIN_W, Math.max(MIN_W, screenW - r.x()));
        int nh = clamp(r.h() + dy, MIN_H, Math.max(MIN_H, screenH - r.y()));
        return pin.withSize(nw, nh);
    }

    public static PinIndex.Pin defaultPin(int index, int screenW, int screenH, float scale) {
        int w = clamp(screenW / 4, MIN_W, 200);
        int h = clamp(screenH / 3, MIN_H, 120);
        int x = Math.max(0, screenW - w - EDGE);
        int y = Math.min(Math.max(0, screenH - h), EDGE + index * (h + GAP));
        return new PinIndex.Pin((double) x / screenW, (double) y / screenH, w, h, scale, 0);
    }

    public static Rect header(Rect r) {
        return new Rect(r.x(), r.y(), r.w(), HEADER);
    }

    public static Rect handle(Rect r) {
        return new Rect(r.right() - HANDLE, r.bottom() - HANDLE, HANDLE, HANDLE);
    }

    public static Rect closeBox(Rect r) {
        return new Rect(r.right() - HEADER, r.y(), HEADER, HEADER);
    }

    public static int maxScroll(int contentHeight, float scale, Rect r, boolean interact) {
        int view = r.h() - 2 * PAD - (interact ? HEADER : 0);
        return Math.max(0, Math.round(contentHeight * scale) - view);
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
