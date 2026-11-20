package com.disqt.disquests.client.gui.tree;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.LEO;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.MAX;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.graph;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.id;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.ids;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.client.gui.tree.Suggestions.Suggestion;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.graph.Refusal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SuggestionsTest {

  private static final int DEPTH = 3;

  /** Seen by Leo. */
  private static final QuestGraph G =
      graph(
          quest("hub"),
          quest("Wagons").in("hub"),
          quest("Gare"),
          quest("Other"),
          quest("Quai").in("Other"),
          quest("Caché").hiddenParent(),
          quest("Roues").owner(MAX).reader(LEO),
          quest("Rails").owner(MAX).writer(LEO).after("Wagons"),
          quest("Banque").owner(MAX),
          quest("Fini").completed(),
          quest("Tour"),
          quest("Tour 2").in("Tour"),
          quest("Tour 3").in("Tour 2"),
          quest("Verrou").owner(MAX).reader(LEO).locked());

  private static Suggestion ok(String title) {
    return new Suggestion(id(title), title, null, null);
  }

  private static Suggestion inside(String title) {
    return new Suggestion(id(title), title, null, "INSIDE_ANOTHER");
  }

  private static Suggestion refused(String title, Refusal reason) {
    return new Suggestion(id(title), title, reason, null);
  }

  private static Suggestion only(List<Suggestion> list, String title) {
    List<Suggestion> matching = list.stream().filter(s -> s.id().equals(id(title))).toList();
    assertEquals(1, matching.size(), title + " in " + list);
    return matching.get(0);
  }

  @Nested
  class Subquests {

    @Test
    void anEmptyFieldListsOnlyWhatCanBeAddedNow() {
      assertEquals(
          List.of(
              inside("Caché"),
              ok("Gare"),
              ok("Other"),
              inside("Quai"),
              inside("Tour 2"),
              inside("Tour 3")),
          Suggestions.subquests(G, LEO, id("hub"), "", DEPTH));
    }

    @Test
    void typingListsEveryMatchAddableFirstWithTheReasonForTheRest() {
      assertEquals(
          List.of(
              ok("Gare"),
              ok("Other"),
              inside("Tour 2"),
              inside("Tour 3"),
              new Suggestion(id("Rails"), "Rails", Refusal.NEED_OWNER, "WRITE"),
              new Suggestion(id("Roues"), "Roues", Refusal.NEED_OWNER, "READ"),
              refused("Tour", Refusal.TOO_DEEP)),
          Suggestions.subquests(G, LEO, id("hub"), "R", DEPTH));
    }

    @ParameterizedTest(name = "{0}: {1} {2}")
    @CsvSource({
      "Gare,,",
      "Quai,,INSIDE_ANOTHER",
      "Caché,,INSIDE_ANOTHER",
      "Roues,NEED_OWNER,READ",
      "Rails,NEED_OWNER,WRITE",
      "Banque,NEED_OWNER,''",
      "Fini,COMPLETED,",
      "Tour,TOO_DEEP,",
      "Wagons,ALREADY_THERE,"
    })
    void eachQuestCarriesWhyItCannotGoUnderTheHub(String title, Refusal reason, String note) {
      assertEquals(
          new Suggestion(id(title), title, reason, note),
          only(Suggestions.subquests(G, LEO, id("hub"), title, DEPTH), title));
    }

    @Test
    void aQuestAboveIsALoop() {
      assertEquals(
          refused("hub", Refusal.LOOP),
          only(Suggestions.subquests(G, LEO, id("Wagons"), "hub", DEPTH), "hub"));
    }

    @Test
    void theQuestItselfAndLockedQuestsAreNeverSuggested() {
      assertEquals(List.of(), Suggestions.subquests(G, LEO, id("hub"), "hub", DEPTH));
      assertEquals(List.of(), Suggestions.subquests(G, LEO, id("hub"), "verrou", DEPTH));
    }

    @Test
    void noDepthLimitWhenTheSettingIsZero() {
      assertEquals(
          inside("Tour 2"), only(Suggestions.subquests(G, LEO, id("hub"), "", 0), "Tour 2"));
      assertEquals(ok("Tour"), only(Suggestions.subquests(G, LEO, id("hub"), "Tour", 0), "Tour"));
    }

    @Test
    void aQuestNotSavedYetChecksOwnershipCompletionAndDepth() {
      assertEquals(
          List.of(
              inside("Caché"),
              ok("Gare"),
              ok("hub"),
              ok("Other"),
              inside("Quai"),
              inside("Tour 2"),
              inside("Tour 3"),
              inside("Wagons")),
          Suggestions.subquests(G, LEO, null, "", DEPTH));
      assertEquals(
          List.of(
              inside("Wagons"),
              new Suggestion(id("Banque"), "Banque", Refusal.NEED_OWNER, ""),
              refused("Fini", Refusal.COMPLETED)),
          Suggestions.subquests(G, LEO, null, "n", DEPTH));
      assertEquals(
          refused("Tour", Refusal.TOO_DEEP),
          only(Suggestions.subquests(G, LEO, null, "Tour", DEPTH), "Tour"));
    }

    @Test
    void theListIsNeverCutShort() {
      List<TreeFixtures.Q> quests = new ArrayList<>();
      quests.add(quest("hub"));
      for (int i = 0; i < 30; i++) quests.add(quest(String.format("q%02d", i)));
      QuestGraph g = graph(quests.toArray(TreeFixtures.Q[]::new));
      assertEquals(30, Suggestions.subquests(g, LEO, id("hub"), "", DEPTH).size());
      assertEquals(30, Suggestions.subquests(g, LEO, id("hub"), "q", DEPTH).size());
    }
  }

  @Nested
  class Prerequisites {

    @Test
    void anEmptyFieldListsWhatWouldNotMakeARefusedLinkLockedOnesIncluded() {
      List<UUID> ids =
          Suggestions.prerequisites(G, LEO, id("Wagons"), ids("Gare"), "").stream()
              .map(Suggestion::id)
              .toList();
      assertEquals(
          ids(
              "Banque", "Caché", "Fini", "Other", "Quai", "Roues", "Tour", "Tour 2", "Tour 3",
              "Verrou"),
          ids);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
      "hub,LOOP",
      "Rails,LOOP",
      "Verrou,",
      "Roues,",
      "Fini,",
      "Tour,",
    })
    void eachQuestCarriesWhyWagonsCannotWaitOnIt(String title, Refusal reason) {
      assertEquals(
          new Suggestion(id(title), title, reason, null),
          only(Suggestions.prerequisites(G, LEO, id("Wagons"), List.of(), title), title));
    }

    @Test
    void chosenQuestsAndTheQuestItselfAreLeftOut() {
      assertEquals(List.of(), Suggestions.prerequisites(G, LEO, id("Wagons"), ids("Gare"), "gare"));
      assertEquals(List.of(), Suggestions.prerequisites(G, LEO, id("Wagons"), List.of(), "wagons"));
    }

    @Test
    void eightChosenIsTheLimit() {
      List<UUID> eight = ids("Gare", "Other", "Quai", "Caché", "Roues", "Banque", "Fini", "Tour");
      assertEquals(List.of(), Suggestions.prerequisites(G, LEO, id("Wagons"), eight, ""));
      assertEquals(
          List.of(refused("Verrou", Refusal.TOO_MANY_PREREQUISITES)),
          Suggestions.prerequisites(G, LEO, id("Wagons"), eight, "verrou"));
      assertEquals(
          List.of(refused("Verrou", Refusal.TOO_MANY_PREREQUISITES)),
          Suggestions.prerequisites(G, LEO, null, eight, "verrou"));
    }

    @ParameterizedTest(name = "hidden={0} deleted={1}: {2}")
    @CsvSource({
      "false,false,",
      "true,false,TOO_MANY_PREREQUISITES",
      "false,true,TOO_MANY_PREREQUISITES"
    })
    void unseenPrerequisitesCountOnceTowardTheLimit(
        boolean hidden, boolean deleted, Refusal expected) {
      TreeFixtures.Q edited = quest("E");
      if (hidden) edited.hiddenPrerequisites();
      if (deleted) edited.deletedPrerequisite();
      List<TreeFixtures.Q> quests = new ArrayList<>(List.of(edited));
      for (int i = 1; i <= 8; i++) quests.add(quest("c" + i));
      QuestGraph g = graph(quests.toArray(TreeFixtures.Q[]::new));
      List<UUID> seven = ids("c1", "c2", "c3", "c4", "c5", "c6", "c7");
      assertEquals(
          List.of(new Suggestion(id("c8"), "c8", expected, null)),
          Suggestions.prerequisites(g, LEO, id("E"), seven, "c8"));
    }

    @Test
    void aQuestNotSavedYetCanWaitOnAnything() {
      assertEquals(14, Suggestions.prerequisites(G, LEO, null, List.of(), "").size());
    }
  }
}
