package dev.blaze.notedown.markdown;

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.List;

public final class MarkdownParser {

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TaskListItemsExtension.create(), StrikethroughExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

    private MarkdownParser() {}

    public static Node parse(String markdown) {
        return PARSER.parse(markdown == null ? "" : markdown);
    }
}
