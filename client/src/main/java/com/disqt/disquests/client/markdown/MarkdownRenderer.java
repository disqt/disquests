package com.disqt.disquests.client.markdown;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

public class MarkdownRenderer {
  public static final String WIKI_LINK_COMMAND_PREFIX = "/disquests:wikilink:";
  public static final String WIKI_LINK_BROKEN = "broken";

  private static final Parser PARSER =
      Parser.builder()
          .extensions(List.of(StrikethroughExtension.create(), TaskListItemsExtension.create()))
          .build();

  private static final Pattern WIKI_LINK_PATTERN =
      Pattern.compile("\\[\\[([^|\\]]*)\\|([^\\]]*)\\]\\]");
  private static final Pattern DQLINK_ATTR_PATTERN =
      Pattern.compile("uuid=\"([^\"]*)\"\\s+title=\"([^\"]*)\"");

  private static String preprocessWikiLinks(String content) {
    return WIKI_LINK_PATTERN
        .matcher(content)
        .replaceAll(
            m -> {
              String uuid = m.group(1);
              String title = m.group(2);
              title =
                  title
                      .replace("&", "&amp;")
                      .replace("<", "&lt;")
                      .replace(">", "&gt;")
                      .replace("\"", "&quot;");
              return "<dqlink uuid=\"" + uuid + "\" title=\"" + title + "\"/>";
            });
  }

  /**
   * Converts server-resolved wiki-links back to raw form for editing. {@code [[uuid|title]]}
   * becomes {@code [[Current Title]]} using cache lookup, or {@code [[title]]} if not found.
   */
  public static String reverseResolveWikiLinks(String content) {
    if (content == null || content.isEmpty()) return content;
    return WIKI_LINK_PATTERN
        .matcher(content)
        .replaceAll(
            m -> {
              String uuidStr = m.group(1);
              String displayTitle = m.group(2);
              if (uuidStr.isEmpty()) {
                return java.util.regex.Matcher.quoteReplacement("[[" + displayTitle + "]]");
              }
              try {
                java.util.UUID questId = java.util.UUID.fromString(uuidStr);
                var quest = com.disqt.disquests.client.ClientCache.getQuestById(questId);
                String title = quest != null ? quest.getTitle() : displayTitle;
                return java.util.regex.Matcher.quoteReplacement("[[" + title + "]]");
              } catch (IllegalArgumentException e) {
                return java.util.regex.Matcher.quoteReplacement("[[" + displayTitle + "]]");
              }
            });
  }

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

    Node document = PARSER.parse(preprocessWikiLinks(processed.toString()));
    List<RenderedLine> lines = new ArrayList<>();
    renderChildren(document, lines, 0, Style.EMPTY);
    return lines;
  }

  /**
   * Returns the first non-heading content line as a styled MutableText with muted colors. Headings
   * (scale != 1.0) and empty lines are skipped. Wiki-links are resolved to display titles. Returns
   * null if no content line is found.
   */
  public static MutableText renderPreviewLine(String markdown) {
    if (markdown == null || markdown.isEmpty()) return null;
    List<RenderedLine> lines = render(markdown);
    for (RenderedLine line : lines) {
      if (line.scale() != 1.0f) continue; // skip headings
      String plain = line.text().getString();
      if (plain.isBlank()) continue;
      return muteColors(line.text());
    }
    return null;
  }

  /** Copies a MutableText tree, dimming all colors for use as a content preview. */
  private static MutableText muteColors(MutableText original) {
    Style style = original.getStyle();
    // Dim existing colors; default to GRAY
    if (style.getColor() != null) {
      int rgb = style.getColor().getRgb();
      // Blend toward gray: reduce brightness by ~40%
      int r = ((rgb >> 16) & 0xFF) * 6 / 10;
      int g = ((rgb >> 8) & 0xFF) * 6 / 10;
      int b = (rgb & 0xFF) * 6 / 10;
      style = style.withColor((r << 16) | (g << 8) | b);
    } else {
      style = style.withColor(Formatting.GRAY);
    }
    // Remove click events from preview
    style = style.withClickEvent(null);
    MutableText result = Text.literal(original.getString()).setStyle(style);
    for (Text sibling : original.getSiblings()) {
      if (sibling instanceof MutableText mt) {
        result.append(muteColors(mt));
      }
    }
    return result;
  }

  /**
   * Strips all markdown formatting and returns plain text. Useful for HUD display where styled text
   * is not needed.
   */
  public static String stripToPlainText(String markdown) {
    if (markdown == null || markdown.isEmpty()) return "";
    // Resolve wiki-links to just their display title before parsing
    String resolved =
        WIKI_LINK_PATTERN
            .matcher(markdown)
            .replaceAll(
                m -> {
                  String title = m.group(2);
                  return title != null ? java.util.regex.Matcher.quoteReplacement(title) : "";
                });
    Node document = PARSER.parse(resolved);
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

  private static void renderChildren(
      Node parent, List<RenderedLine> lines, int indent, Style style) {
    Node child = parent.getFirstChild();
    while (child != null) {
      renderBlock(child, lines, indent, style);
      child = child.getNext();
    }
  }

  private static void renderBlock(Node node, List<RenderedLine> lines, int indent, Style style) {
    if (node instanceof Heading heading) {
      MutableText text = collectInlineText(heading, Style.EMPTY.withBold(true));
      float scale =
          switch (heading.getLevel()) {
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
          MutableText prefix =
              Text.literal(checkbox)
                  .setStyle(
                      marker.isChecked()
                          ? Style.EMPTY.withColor(Formatting.GREEN)
                          : Style.EMPTY.withColor(Formatting.GRAY));
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
        MutableText prefixed =
            Text.literal("| ")
                .setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY))
                .append(line.text());
        lines.add(RenderedLine.normal(prefixed, indent));
      }
    } else if (node instanceof FencedCodeBlock code) {
      renderCodeLines(code.getLiteral(), lines, indent);
    } else if (node instanceof IndentedCodeBlock code) {
      renderCodeLines(code.getLiteral(), lines, indent);
    } else if (node instanceof ThematicBreak) {
      lines.add(
          RenderedLine.normal(
              Text.literal("---").setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY)), indent));
    } else if (node instanceof org.commonmark.node.HtmlBlock htmlBlock) {
      // HtmlBlock occurs when <dqlink .../> is on its own line after a blank line.
      // Scan for dqlink tags and render them the same way as inline wiki-links.
      String literal = htmlBlock.getLiteral();
      Matcher m = DQLINK_ATTR_PATTERN.matcher(literal);
      if (m.find()) {
        String uuid = m.group(1);
        String title =
            m.group(2)
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"");
        boolean isValid = isWikiLinkResolvable(uuid);
        int color = isValid ? 0xe8a86d : 0xe86d6d;
        Style wikiStyle = style.withColor(color).withUnderline(true);
        if (!isValid) wikiStyle = wikiStyle.withStrikethrough(true);
        String command =
            isValid ? WIKI_LINK_COMMAND_PREFIX + uuid : WIKI_LINK_COMMAND_PREFIX + WIKI_LINK_BROKEN;
        wikiStyle = wikiStyle.withClickEvent(new ClickEvent.RunCommand(command));
        lines.add(RenderedLine.normal(Text.literal(title).setStyle(wikiStyle), indent));
      }
    } else {
      // Fallback: try to render children
      renderChildren(node, lines, indent, style);
    }
  }

  private static void renderCodeLines(String literal, List<RenderedLine> lines, int indent) {
    String[] codeLines = literal.split("\n", -1);
    for (String cl : codeLines) {
      lines.add(
          RenderedLine.normal(
              Text.literal(cl).setStyle(Style.EMPTY.withColor(Formatting.GRAY)), indent + 8));
    }
  }

  private static void renderListItem(
      Node item, List<RenderedLine> lines, int indent, Style style, String bullet) {
    Node firstChild = item.getFirstChild();
    if (firstChild instanceof Paragraph) {
      MutableText bulletText =
          Text.literal(bullet).setStyle(Style.EMPTY.withColor(Formatting.GRAY));
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
      target.append(Text.literal(code.getLiteral()).setStyle(style.withColor(Formatting.GRAY)));
    } else if (node instanceof Link link) {
      MutableText inner =
          collectInlineText(node, style.withColor(Formatting.AQUA).withUnderline(true));
      try {
        inner.setStyle(
            inner
                .getStyle()
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(link.getDestination()))));
      } catch (IllegalArgumentException ignored) {
        // Invalid URI, skip click event
      }
      target.append(inner);
    } else if (node instanceof org.commonmark.node.HtmlInline htmlInline) {
      String literal = htmlInline.getLiteral();
      Matcher m = DQLINK_ATTR_PATTERN.matcher(literal);
      if (m.find()) {
        String uuid = m.group(1);
        String title =
            m.group(2)
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"");
        boolean isValid = isWikiLinkResolvable(uuid);
        int color = isValid ? 0xe8a86d : 0xe86d6d; // amber or red
        Style wikiStyle = style.withColor(color).withUnderline(true);
        if (!isValid) wikiStyle = wikiStyle.withStrikethrough(true);
        // Use RunCommand as a marker for MarkdownWidget click handling
        String command =
            isValid ? WIKI_LINK_COMMAND_PREFIX + uuid : WIKI_LINK_COMMAND_PREFIX + WIKI_LINK_BROKEN;
        wikiStyle = wikiStyle.withClickEvent(new ClickEvent.RunCommand(command));
        target.append(Text.literal(title).setStyle(wikiStyle));
      }
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

  /** A wiki-link is resolvable if the UUID is non-empty and the quest exists in the cache. */
  private static boolean isWikiLinkResolvable(String uuid) {
    if (uuid == null || uuid.isEmpty()) return false;
    try {
      return com.disqt.disquests.client.ClientCache.getQuestById(java.util.UUID.fromString(uuid))
          != null;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
