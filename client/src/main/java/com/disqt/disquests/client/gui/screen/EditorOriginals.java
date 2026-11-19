package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.markdown.WikiLinks;
import java.util.UUID;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * The quest's title and content as the editor opened, without Minecraft. The editor compares its
 * fields with {@code title} and {@code editingContent} (the content in the editing form, see {@link
 * WikiLinks#toEditingForm}); Cancel puts back {@code receivedContent}, the content exactly as the
 * server sent it, so the cached quest is left as it was.
 */
record EditorOriginals(String title, @Nullable String receivedContent, String editingContent) {

  /** The quest as it is now; {@code lookup} finds the cached quests its wiki-links name. */
  static EditorOriginals of(Quest quest, Function<UUID, Quest> lookup) {
    String content = quest.getContent();
    return new EditorOriginals(
        quest.getTitle() != null ? quest.getTitle() : "",
        content,
        content != null ? WikiLinks.toEditingForm(content, lookup) : "");
  }

  /** True when the fields differ from what the editor opened with. */
  boolean textChanged(String currentTitle, String currentContent) {
    return !title.equals(currentTitle) || !editingContent.equals(currentContent);
  }

  /** Puts the title and the received content back into the quest. */
  void restore(Quest quest) {
    quest.setTitle(title);
    quest.setContent(receivedContent);
  }
}
