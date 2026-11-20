package com.disqt.disquests.client.gui.tree;

import com.disqt.disquests.client.data.NoticeText;
import com.disqt.disquests.client.data.NoticeText.Message;
import com.disqt.disquests.common.graph.Refusal;
import java.util.List;

/**
 * How one line of the "add a subquest..." and "Prerequisites" dropdowns reads. An addable quest
 * shows its title and, right-aligned, its note ("inside another quest"); a refused one is greyed,
 * with the reason in brackets after its title, and for {@link Refusal#NEED_OWNER} the role the
 * player holds on it ("you need Owner on it, you have Read").
 */
public final class SuggestionText {

  private static final String KEY = "gui.disquests.suggestion.";

  /**
   * The title; the bracketed reason drawn after it, kept whole when the title has to be shortened
   * (null when the quest can be added); whether it can be picked; the right-aligned note (null for
   * none).
   */
  public record Line(String title, Message reason, boolean enabled, Message note) {}

  private SuggestionText() {}

  public static Line of(Suggestions.Suggestion s, int maxQuestDepth) {
    String title = s.title() != null ? s.title() : "";
    if (s.reason() == null) {
      Message note =
          Suggestions.INSIDE_ANOTHER.equals(s.note())
              ? new Message(KEY + "inside_another", List.of())
              : null;
      return new Line(title, null, true, note);
    }
    Message reason = NoticeText.refusal(s.reason(), maxQuestDepth);
    String role = s.reason() == Refusal.NEED_OWNER ? roleKey(s.note()) : null;
    if (role != null) {
      reason = new Message(KEY + "with_role", List.of(reason, new Message(role, List.of())));
    }
    return new Line(title, new Message(KEY + "reason", List.of(reason)), false, null);
  }

  /** The "you have ..." key for the role the note names ("" = none); null when it names none. */
  private static String roleKey(String note) {
    if (note == null) return null;
    return switch (note) {
      case "" -> KEY + "role_none";
      case "READ" -> KEY + "role_read";
      case "WRITE" -> KEY + "role_write";
      default -> null;
    };
  }
}
