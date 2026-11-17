package com.disqt.disquests.client.data;

import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.Notice;
import java.util.List;
import java.util.Locale;

/**
 * Which lang key and arguments a server notice is shown with. Arguments are strings, numbers or
 * nested {@link Message}s; the caller turns this into a translated component.
 */
public final class NoticeText {

  private NoticeText() {}

  public record Message(String key, List<Object> args) {}

  private static final String NOTICE = "gui.disquests.notice.";
  private static final String REFUSAL = "gui.disquests.refusal.";

  public static Message of(Notice notice, int maxQuestDepth) {
    String title = notice.title() != null ? notice.title() : "";
    return switch (notice.kind()) {
      case QUEST_UNLOCKED, QUEST_UNLOCKED_FOLLOWED ->
          new Message(NOTICE + "unlocked", List.of(title));
      case SET_PRIVATE -> new Message(NOTICE + "set_private", List.of(title));
      case CHANGE_REFUSED -> {
        Message reason = refusal(notice.reason(), maxQuestDepth);
        yield notice.title() != null
            ? new Message(NOTICE + "refused_titled", List.of(title, reason))
            : new Message(NOTICE + "refused", List.of(reason));
      }
    };
  }

  public static Message refusal(Refusal reason, int maxQuestDepth) {
    if (reason == null) return new Message(REFUSAL + "unknown", List.of());
    String key = REFUSAL + reason.name().toLowerCase(Locale.ROOT);
    return new Message(key, reason == Refusal.TOO_DEEP ? List.of(maxQuestDepth) : List.of());
  }
}
