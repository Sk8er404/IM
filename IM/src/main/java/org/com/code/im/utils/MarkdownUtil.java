package org.com.code.im.utils;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class MarkdownUtil {
    
    private final Parser parser;
    private final HtmlRenderer renderer;
    
    public MarkdownUtil() {
        List<Extension> extensions = Arrays.asList(
            TablesExtension.create(),
            StrikethroughExtension.create()
        );
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder().extensions(extensions).build();
    }
    
    public String renderToHtml(String markdownContent) {
        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            return "";
        }
        
        Node document = parser.parse(markdownContent);
        return renderer.render(document);
    }
} 