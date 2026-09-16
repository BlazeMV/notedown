package dev.blaze.notedown.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LayoutCacheTest {

    @Test
    void reusesLayoutForSameInputs() {
        LayoutCache cache = new LayoutCache();
        Layout a = cache.get("hi", 100, CheckedTaskStyle.NONE, LayouterTest.FAKE);
        Layout b = cache.get("hi", 100, CheckedTaskStyle.NONE, LayouterTest.FAKE);
        assertSame(a, b);
        assertNotSame(a, cache.get("hi", 120, CheckedTaskStyle.NONE, LayouterTest.FAKE));
        assertNotSame(a, cache.get("hi", 100, CheckedTaskStyle.MUTED, LayouterTest.FAKE));
        assertNotSame(a, cache.get("ho", 100, CheckedTaskStyle.NONE, LayouterTest.FAKE));
        cache.clear();
        assertNotSame(a, cache.get("hi", 100, CheckedTaskStyle.NONE, LayouterTest.FAKE));
    }
}
