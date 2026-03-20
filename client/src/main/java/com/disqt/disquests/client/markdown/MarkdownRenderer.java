package com.disqt.disquests.client.markdown;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class MarkdownRenderer {
    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(
                    StrikethroughExtension.create(),
                    TaskListItemsExtension.create()
            )).build();

    public static List<RenderedLine> render(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return List.of(RenderedLine.empty());
        }

        // Strip leading whitespace per line to prevent commonmark
        // from treating 4+ spaces as indented code blocks
        String[] rawLines = markdown.split("\n", -1);
        StringBuilder processed = new StringBuilder();
        for (int i = 0; i < rawLines.length; i++) {
            if (i > 0) processed.append('\n');
            processed.append(rawLines[i].stripLeading());
        }

        Node document = PARSER.parse(processed.toString());
        List<RenderedLine> lines = new ArrayList<>();
        renderChildren(document, lines, 0, Style.EMPTY);
        return lines;
    }

    /**
     * Strips all markdown formatting and returns plain text.
     * Useful for HUD display where styled text is not needed.
     */
    public static String stripToPlainText(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";
        Node document = PARSER.parse(markdown);
        StringBuilder sb = new StringBuilder();
        collectPlainText(document, sb);
        return sb.toString().trim();
    }

    private static void collectPlainText(Node node, StringBuilder sb) {
        if (node instanceof org.commonmark.node.Text textNode) {
            sb.append(textNode.getLiteral());
        } else if (node instanceof SoftLineBreak) {
            sb.append(' ');
        } else if (node instanceof HardLineBreak) {
            sb.append('\n');
        } else if (node instanceof Code code) {
            sb.append(code.getLiteral());
        } else if (node instanceof FencedCodeBlock code) {
            sb.append(code.getLiteral());
        } else if (node instanceof IndentedCodeBlock code) {
            sb.append(code.getLiteral());
        } else if (node instanceof TaskListItemMarker marker) {
            sb.append(marker.isChecked() ? "\u2611 " : "\u2610 ");
        } else if (node instanceof Paragraph) {
            Node child = node.getFirstChild();
            while (child != null) {
                collectPlainText(child, sb);
                child = child.getNext();
            }
            sb.append('\n');
        } else {
            Node child = node.getFirstChild();
            while (child != null) {
                collectPlainText(child, sb);
                child = child.getNext();
            }
        }
    }

    private static void renderChildren(Node parent, List<RenderedLine> lines, int indent, Style style) {
        Node child = parent.getFirstChild();
        while (child != null) {
            renderBlock(child, lines, indent, style);
            child = child.getNext();
        }
    }

    private static void renderBlock(Node node, List<RenderedLine> lines, int indent, Style style) {
        if (node instanceof Heading heading) {
            MutableText text = collectInlineText(heading, Style.EMPTY.withBold(true));
            float scale = switch (heading.getLevel()) {
                case 1 -> 1.5f;
                case 2 -> 1.25f;
                default -> 1.0f;
            };
            if (!lines.isEmpty()) lines.add(RenderedLine.empty());
            lines.add(RenderedLine.heading(text, scale));
        } else if (node instanceof Paragraph) {
            MutableText text = collectInlineText(node, style);
            lines.add(RenderedLine.normal(text, indent));
        } else if (node instanceof BulletList) {
            renderChildren(node, lines, indent, style);
        } else if (node instanceof OrderedList orderedList) {
            Integer startNumber = orderedList.getMarkerStartNumber();
            int num = startNumber != null ? startNumber : 1;
            Node item = node.getFirstChild();
            while (item != null) {
                if (item instanceof ListItem) {
                    renderListItem(item, lines, indent, style, num + ". ");
                    num++;
                }
                item = item.getNext();
            }
        } else if (node instanceof ListItem) {
            // Check for task list.
            // TaskListItemsExtension prepends TaskListItemMarker as the first child of
            // ListItem (not inside the Paragraph), so the structure is:
            //   ListItem > [TaskListItemMarker, Paragraph > Text("...")]
            Node firstChild = node.getFirstChild();
            if (firstChild instanceof TaskListItemMarker marker) {
                Node secondChild = firstChild.getNext();
                if (secondChild instanceof Paragraph para) {
                    String checkbox = marker.isChecked() ? "[x] " : "[ ] ";
                    MutableText prefix = Text.literal(checkbox).setStyle(
                            marker.isChecked() ? Style.EMPTY.withColor(Formatting.GREEN) : Style.EMPTY.withColor(Formatting.GRAY));
                    MutableText content = collectInlineText(para, style);
                    if (marker.isChecked()) {
                        content = content.formatted(Formatting.STRIKETHROUGH, Formatting.GRAY);
                    }
                    lines.add(RenderedLine.normal(prefix.append(content), indent));
                }
            } else {
                renderListItem(node, lines, indent, style, "* ");
            }
            // Render nested lists
            Node child = node.getFirstChild();
            while (child != null) {
                if (child instanceof BulletList || child instanceof OrderedList) {
                    renderChildren(child, lines, indent + 12, style);
                }
                child = child.getNext();
            }
        } else if (node instanceof BlockQuote) {
            List<RenderedLine> inner = new ArrayList<>();
            renderChildren(node, inner, 0, style.withColor(Formatting.GRAY));
            for (RenderedLine line : inner) {
                MutableText prefixed = Text.literal("| ").setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY))
                        .append(line.text());
                lines.add(RenderedLine.normal(prefixed, indent));
            }
        } else if (node instanceof FencedCodeBlock code) {
            String[] codeLines = code.getLiteral().split("\n", -1);
            for (String cl : codeLines) {
                lines.add(RenderedLine.normal(
                        Text.literal(cl).setStyle(Style.EMPTY.withColor(Formatting.GRAY)), indent + 8));
            }
        } else if (node instanceof IndentedCodeBlock code) {
            String[] codeLines = code.getLiteral().split("\n", -1);
            for (String cl : codeLines) {
                lines.add(RenderedLine.normal(
                        Text.literal(cl).setStyle(Style.EMPTY.withColor(Formatting.GRAY)), indent + 8));
            }
        } else if (node instanceof ThematicBreak) {
            lines.add(RenderedLine.normal(
                    Text.literal("---").setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY)), indent));
        } else {
            // Fallback: try to render children
            renderChildren(node, lines, indent, style);
        }
    }

    private static void renderListItem(Node item, List<RenderedLine> lines, int indent, Style style, String bullet) {
        Node firstChild = item.getFirstChild();
        if (firstChild instanceof Paragraph) {
            MutableText bulletText = Text.literal(bullet).setStyle(Style.EMPTY.withColor(Formatting.GRAY));
            MutableText content = collectInlineText(firstChild, style);
            lines.add(RenderedLine.normal(bulletText.append(content), indent));
        }
    }

    private static MutableText collectInlineText(Node parent, Style style) {
        return collectInlineText(parent, style, parent.getFirstChild());
    }

    private static MutableText collectInlineText(Node parent, Style style, Node startFrom) {
        MutableText result = Text.empty().copy();
        Node child = startFrom;
        while (child != null) {
            appendInline(child, result, style);
            child = child.getNext();
        }
        return result;
    }

    private static void appendInline(Node node, MutableText target, Style style) {
        if (node instanceof org.commonmark.node.Text textNode) {
            target.append(Text.literal(textNode.getLiteral()).setStyle(style));
        } else if (node instanceof Emphasis) {
            MutableText inner = collectInlineText(node, style.withItalic(true));
            target.append(inner);
        } else if (node instanceof StrongEmphasis) {
            MutableText inner = collectInlineText(node, style.withBold(true));
            target.append(inner);
        } else if (node instanceof Strikethrough) {
            MutableText inner = collectInlineText(node, style.withStrikethrough(true));
            target.append(inner);
        } else if (node instanceof Code code) {
            target.append(Text.literal(code.getLiteral()).setStyle(
                    style.withColor(Formatting.GRAY)));
        } else if (node instanceof Link link) {
            MutableText inner = collectInlineText(node, style.withColor(Formatting.AQUA).withUnderline(true));
            try {
                inner.setStyle(inner.getStyle().withClickEvent(
                        new ClickEvent.OpenUrl(URI.create(link.getDestination()))));
            } catch (IllegalArgumentException ignored) {
                // Invalid URI, skip click event
            }
            target.append(inner);
        } else if (node instanceof SoftLineBreak) {
            target.append(Text.literal(" "));
        } else if (node instanceof HardLineBreak) {
            target.append(Text.literal(" "));
        } else {
            // Unknown inline -- try children
            MutableText inner = collectInlineText(node, style);
            target.append(inner);
        }
    }
}
