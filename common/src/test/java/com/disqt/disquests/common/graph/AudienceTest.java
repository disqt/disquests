package com.disqt.disquests.common.graph;

import static com.disqt.disquests.common.graph.GraphFixtures.JUJU;
import static com.disqt.disquests.common.graph.GraphFixtures.LEO;
import static com.disqt.disquests.common.graph.GraphFixtures.MAX;
import static com.disqt.disquests.common.graph.GraphFixtures.STRANGER;
import static com.disqt.disquests.common.graph.GraphFixtures.graph;
import static com.disqt.disquests.common.graph.GraphFixtures.id;
import static com.disqt.disquests.common.graph.GraphFixtures.quest;
import static com.disqt.disquests.common.graph.GraphFixtures.replace;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.common.graph.Audience.Change;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AudienceTest {

  @Nested
  class HubMadePrivate {

    /** LEO's Open hub with JUJU reading it; MAX has no role anywhere. */
    private final QuestGraph before =
        graph(
            quest("hub").open().reader(JUJU),
            quest("Intérieurs").open().in("hub"),
            quest("Wagons").closed().in("hub"),
            quest("Église").in("hub"),
            quest("elsewhere").open());

    private final QuestGraph after =
        replace(before, id("hub"), q -> q.withVisibility(Visibility.PRIVATE));

    @Test
    void nonMemberLosesTheHubAndItsSubquests() {
      assertEquals(
          List.of(
              new Change(id("hub"), null),
              new Change(id("Intérieurs"), null),
              new Change(id("Wagons"), null)),
          Audience.changes(before, after, MAX));
    }

    @Test
    void memberOnlySeesTheHubsNewSetting() {
      assertEquals(
          List.of(new Change(id("hub"), after.get(id("hub")))),
          Audience.changes(before, after, JUJU));
    }
  }

  @Nested
  class PrerequisiteCompleted {

    /** "gate" waits on "first"; JUJU reads "gate" only. */
    private final QuestGraph before =
        graph(
            quest("first").open(),
            quest("gate").open().rich().after("first").reader(JUJU),
            quest("unrelated").open());

    private final QuestGraph after =
        replace(before, id("first"), q -> q.withCompletion(5L, LEO, "p1"));

    @Test
    void readerGetsTheUnlockedQuest() {
      assertEquals(
          List.of(
              new Change(id("first"), after.get(id("first"))),
              new Change(id("gate"), after.get(id("gate")))),
          Audience.changes(before, after, JUJU));
    }

    @Test
    void unlockedViewCarriesTheContent() {
      QuestData gate =
          Audience.changes(before, after, JUJU).stream()
              .filter(c -> c.questId().equals(id("gate")))
              .findFirst()
              .orElseThrow()
              .view();
      assertEquals(List.of(false, "the text of gate"), List.of(gate.locked(), gate.content()));
    }
  }

  @Nested
  class LockedQuestEdited {

    /** "gate" waits on "first" and is locked for JUJU, who reads it; MAX writes on it. */
    private QuestGraph withGate(String content, long lastModified) {
      return graph(
          quest("first").open(),
          quest("gate")
              .open()
              .rich()
              .content(content)
              .lastModified(lastModified)
              .after("first")
              .reader(JUJU)
              .writer(MAX));
    }

    @Test
    void writersEditDoesNotReachAReaderWhoSeesItLocked() {
      QuestGraph before = withGate("chapter one", 1L);
      QuestGraph after = withGate("chapter one, revised", 2L);
      assertEquals(
          List.of(List.of(), List.of(new Change(id("gate"), after.get(id("gate"))))),
          List.of(Audience.changes(before, after, JUJU), Audience.changes(before, after, MAX)));
    }
  }

  @Nested
  class Listing {

    private final QuestGraph before =
        graph(quest("leo's secret"), quest("open").open(), quest("doomed").open());

    @Test
    void questSeenInNeitherGraphIsNotListed() {
      QuestGraph after = replace(before, id("leo's secret"), q -> q.withContent("new text"));
      assertEquals(List.of(), Audience.changes(before, after, STRANGER));
    }

    @Test
    void unchangedViewIsNotListed() {
      assertEquals(List.of(), Audience.changes(before, before, STRANGER));
    }

    @Test
    void newAndDeletedQuests() {
      QuestGraph after =
          QuestGraph.of(
              List.of(
                  before.get(id("leo's secret")),
                  before.get(id("open")),
                  quest("new").open().build()));
      assertEquals(
          List.of(new Change(id("doomed"), null), new Change(id("new"), after.get(id("new")))),
          Audience.changes(before, after, STRANGER));
    }

    @Test
    void ownerSeesAChangeOthersCannot() {
      UnaryOperator<QuestData> edit = q -> q.withContent("new text");
      QuestGraph after = replace(before, id("leo's secret"), edit);
      Map<UUID, Integer> counts =
          List.of(LEO, STRANGER).stream()
              .collect(Collectors.toMap(p -> p, p -> Audience.changes(before, after, p).size()));
      assertEquals(Map.of(LEO, 1, STRANGER, 0), counts);
    }
  }
}
