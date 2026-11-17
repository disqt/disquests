package com.disqt.disquests.client.gui.tree;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.JUJU;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.MAX;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.graph;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.id;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.tree.WaitingText.Waiting;
import com.disqt.disquests.common.graph.QuestGraph;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WaitingTextTest {

  private static List<UUID> titles(String list) {
    if (list == null || list.isBlank()) return List.of();
    return Arrays.stream(list.split(";")).map(TreeFixtures::id).toList();
  }

  @ParameterizedTest(name = "[{0}] hidden={1} deleted={2}")
  @CsvSource({
    // listed prerequisites, hidden, deleted -> visible, something else, nothing visible
    "A;B, false, false, A;B, false, false",
    "A,   true,  false, A,   true,  false",
    "'',  true,  false, '',  true,  true",
    "A,   false, true,  A,   true,  false",
    "A;Gone, false, false, A, true, false",
    "Gone, false, false, '', true, true",
  })
  void theThreeWaysOfSayingWhatTheQuestWaitsOn(
      String listed,
      boolean hidden,
      boolean deleted,
      String visible,
      boolean somethingElse,
      boolean nothingVisible) {
    // "Gone" is listed but not in the graph.
    TreeFixtures.Q gate = quest("Gate").owner(MAX).reader(JUJU).locked();
    if (!listed.isBlank()) gate.after(listed.split(";"));
    if (hidden) gate.hiddenPrerequisites();
    if (deleted) gate.deletedPrerequisite();
    QuestGraph g = graph(quest("A").owner(MAX), quest("B").owner(MAX), gate);
    Quest quest = Quest.fromNetwork(g.get(id("Gate")));

    assertEquals(
        new Waiting(titles(visible), somethingElse, nothingVisible), WaitingText.of(quest, g));
  }
}
