package com.disqt.disquests.client.gui.screen;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.LEO;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.id;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.client.data.Quest;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class EditorOriginalsTest {

  private static final Quest TARGET = Quest.fromNetwork(quest("Target").owner(LEO).build());
  private static final Function<UUID, Quest> LOOKUP = Map.of(id("Target"), TARGET)::get;
  private static final String RECEIVED = "see [[" + id("Target") + "|Target]]";

  private static Quest edited() {
    return Quest.fromNetwork(quest("Old title").owner(LEO).content(RECEIVED).build());
  }

  @Test
  void comparesTheFieldsWithTheEditingForm() {
    EditorOriginals originals = EditorOriginals.of(edited(), LOOKUP);
    assertFalse(originals.textChanged("Old title", "see [[Target]]"));
    assertTrue(originals.textChanged("Old title", "see [[Target]] more"));
    assertTrue(originals.textChanged("New title", "see [[Target]]"));
  }

  @Test
  void cancelPutsBackWhatTheServerSent() {
    Quest q = edited();
    EditorOriginals originals = EditorOriginals.of(q, LOOKUP);
    // The editor writes its fields into the quest while it is open
    q.setTitle("Typed");
    q.setContent("see [[Target]] typed");

    originals.restore(q);

    assertEquals("Old title", q.getTitle());
    assertEquals(RECEIVED, q.getContent());
  }
}
