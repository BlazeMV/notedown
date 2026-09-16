package dev.blaze.notedown.markdown;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LayoutCache {

    private static final int CAPACITY = 16;

    private record Key(String body, int width, CheckedTaskStyle style) {}

    private final Map<Key, Layout> cache = new LinkedHashMap<>(CAPACITY, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Layout> eldest) {
            return size() > CAPACITY;
        }
    };

    public Layout get(String body, int width, CheckedTaskStyle style, TextMeasure measure) {
        Key key = new Key(body, width, style);
        Layout layout = cache.get(key);
        if (layout == null) {
            layout = Layouter.layout(MarkdownParser.parse(body), width, style, measure);
            cache.put(key, layout);
        }
        return layout;
    }

    public void clear() {
        cache.clear();
    }
}
