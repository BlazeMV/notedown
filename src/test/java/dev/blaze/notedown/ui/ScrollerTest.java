package dev.blaze.notedown.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScrollerTest {

    @Test
    void noScrollWhenContentFits() {
        Scroller s = new Scroller();
        s.update(50, 100);
        assertFalse(s.visible());
        assertEquals(0, s.max());
        s.scrollBy(30);
        assertEquals(0, s.amount());
        assertEquals(100, s.thumbHeight(100));
    }

    @Test
    void clampsAndTracksThumb() {
        Scroller s = new Scroller();
        s.update(400, 100);
        assertTrue(s.visible());
        assertEquals(300, s.max());
        s.scrollBy(-10);
        assertEquals(0, s.amount());
        s.scrollBy(1000);
        assertEquals(300, s.amount());
        assertEquals(25, s.thumbHeight(100));
        assertEquals(75, s.thumbY(100));
        s.scrollTo(150);
        assertEquals(37, s.thumbY(100));
    }

    @Test
    void dragMapsThumbToAmount() {
        Scroller s = new Scroller();
        s.update(400, 100);
        s.dragTo(100, 75);
        assertEquals(300, s.amount());
        s.dragTo(100, 0);
        assertEquals(0, s.amount());
        s.dragTo(100, 500);
        assertEquals(300, s.amount());
    }

    @Test
    void pageMovesByViewHeightMinusOverlap() {
        Scroller s = new Scroller();
        s.update(400, 100);
        s.pageBy(1);
        assertEquals(90, s.amount());
        s.pageBy(-1);
        assertEquals(0, s.amount());
    }

    @Test
    void updateKeepsAmountInRange() {
        Scroller s = new Scroller();
        s.update(400, 100);
        s.scrollTo(300);
        s.update(150, 100);
        assertEquals(50, s.amount());
    }
}
