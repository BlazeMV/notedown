package dev.blaze.notedown.hud;

import dev.blaze.notedown.store.PinIndex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PinGeometryTest {

    @Test
    void rectScalesFractionsAndClampsOnScreen() {
        PinIndex.Pin pin = new PinIndex.Pin(0.5, 0.25, 100, 50, 1f, 0);
        assertEquals(new PinGeometry.Rect(200, 60, 100, 50), PinGeometry.rect(pin, 400, 240));
        PinIndex.Pin offscreen = new PinIndex.Pin(0.95, 0.95, 100, 50, 1f, 0);
        assertEquals(new PinGeometry.Rect(300, 190, 100, 50), PinGeometry.rect(offscreen, 400, 240));
        PinIndex.Pin tiny = new PinIndex.Pin(0, 0, 1, 1, 1f, 0);
        assertEquals(new PinGeometry.Rect(0, 0, PinGeometry.MIN_W, PinGeometry.MIN_H), PinGeometry.rect(tiny, 400, 240));
    }

    @Test
    void movedStaysOnScreenAndStoresFractions() {
        PinIndex.Pin pin = new PinIndex.Pin(0.5, 0.5, 100, 50, 1f, 0);
        PinGeometry.Rect r = PinGeometry.rect(pin, 400, 240);
        PinIndex.Pin moved = PinGeometry.moved(pin, r, 1000, -1000, 400, 240);
        assertEquals(0.75, moved.x(), 1e-9);
        assertEquals(0.0, moved.y(), 1e-9);
        assertEquals(100, moved.w());
    }

    @Test
    void resizedRespectsMinimumAndScreen() {
        PinIndex.Pin pin = new PinIndex.Pin(0.5, 0.5, 100, 50, 1f, 0);
        PinGeometry.Rect r = PinGeometry.rect(pin, 400, 240);
        assertEquals(new PinIndex.Pin(0.5, 0.5, PinGeometry.MIN_W, PinGeometry.MIN_H, 1f, 0), PinGeometry.resized(pin, r, -500, -500, 400, 240));
        PinIndex.Pin grown = PinGeometry.resized(pin, r, 500, 500, 400, 240);
        assertEquals(200, grown.w());
        assertEquals(120, grown.h());
    }

    @Test
    void defaultPinsStackDownTheRightEdge() {
        PinIndex.Pin a = PinGeometry.defaultPin(0, 400, 240, 1.5f);
        PinIndex.Pin b = PinGeometry.defaultPin(1, 400, 240, 1.5f);
        PinGeometry.Rect ra = PinGeometry.rect(a, 400, 240);
        PinGeometry.Rect rb = PinGeometry.rect(b, 400, 240);
        assertEquals(400 - ra.w() - 6, ra.x());
        assertEquals(6, ra.y());
        assertEquals(1.5f, a.scale());
        assertTrue(rb.y() > ra.bottom());
        assertTrue(rb.bottom() <= 240);
    }

    @Test
    void regionsAndScroll() {
        PinGeometry.Rect r = new PinGeometry.Rect(10, 20, 100, 60);
        assertEquals(new PinGeometry.Rect(10, 20, 100, PinGeometry.HEADER), PinGeometry.header(r));
        assertEquals(new PinGeometry.Rect(102, 72, 8, 8), PinGeometry.handle(r));
        assertEquals(new PinGeometry.Rect(98, 20, 12, 12), PinGeometry.closeBox(r));
        assertEquals(0, PinGeometry.maxScroll(40, 1f, r, false));
        assertEquals(50, PinGeometry.maxScroll(100, 1f, r, false));
        assertEquals(62, PinGeometry.maxScroll(100, 1f, r, true));
        assertEquals(150, PinGeometry.maxScroll(100, 2f, r, false));
    }
}
