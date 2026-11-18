package com.disqt.disquests.client.gui.screen;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.ANNA;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.JUJU;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.LEO;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.MAX;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.graph;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.id;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.disqt.disquests.client.data.NoticeText.Message;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.screen.PeopleText.AddKind;
import com.disqt.disquests.client.gui.screen.PeopleText.AddMatch;
import com.disqt.disquests.client.gui.screen.PeopleText.ApplyDown;
import com.disqt.disquests.client.gui.screen.PeopleText.Candidate;
import com.disqt.disquests.client.gui.screen.PeopleText.OpenState;
import com.disqt.disquests.client.gui.screen.PeopleText.RemoveChoice;
import com.disqt.disquests.client.gui.screen.PeopleText.SaveStep;
import com.disqt.disquests.client.gui.tree.PeopleDraft;
import com.disqt.disquests.client.gui.tree.PeopleDraft.Person;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.Role;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class PeopleTextTest {

  private static final UUID NEWCOMER = new UUID(0, 5);

  /** Leo (the player) is the display owner; Juju reads, Max owns, Anna writes. */
  private static PeopleDraft draft() {
    return new PeopleDraft(
        Quest.fromNetwork(quest("hub").reader(JUJU).coOwner(MAX).writer(ANNA).build()));
  }

  @Nested
  class Save {

    static Stream<Arguments> steps() {
      return Stream.of(
          Arguments.of("nothing changed", (Consumer<PeopleDraft>) d -> {}, SaveStep.NOTHING),
          Arguments.of(
              "changed and back",
              (Consumer<PeopleDraft>)
                  d -> {
                    d.setRole(JUJU, Role.WRITE);
                    d.setRole(JUJU, Role.READ);
                  },
              SaveStep.NOTHING),
          Arguments.of(
              "someone else's role",
              (Consumer<PeopleDraft>) d -> d.setRole(JUJU, Role.WRITE),
              SaveStep.SEND),
          Arguments.of(
              "someone added",
              (Consumer<PeopleDraft>) d -> d.add(NEWCOMER, "p5", Role.READ),
              SaveStep.SEND),
          Arguments.of(
              "giving up my Owner by demotion",
              (Consumer<PeopleDraft>) d -> d.setRole(LEO, Role.WRITE),
              SaveStep.ASK_GIVING_UP_OWNER),
          Arguments.of(
              "giving up my Owner by removal",
              (Consumer<PeopleDraft>) d -> d.remove(LEO, false),
              SaveStep.ASK_GIVING_UP_OWNER),
          Arguments.of(
              "no owner left beats the question",
              (Consumer<PeopleDraft>)
                  d -> {
                    d.setRole(LEO, Role.READ);
                    d.remove(MAX, false);
                  },
              SaveStep.NO_OWNER_LEFT),
          Arguments.of(
              "no owner left by removals",
              (Consumer<PeopleDraft>)
                  d -> {
                    d.remove(MAX, false);
                    d.remove(LEO, true);
                  },
              SaveStep.NO_OWNER_LEFT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("steps")
    void step(String name, Consumer<PeopleDraft> change, SaveStep expected) {
      PeopleDraft d = draft();
      change.accept(d);
      assertEquals(expected, PeopleText.saveStep(d, LEO));
    }
  }

  @Nested
  class Remove {

    @Test
    void asksWithOneSubquest() {
      assertEquals(
          new RemoveChoice(
              new Message("gui.disquests.people.remove_question", List.of("Juju")),
              new Message("gui.disquests.people.remove_this_only", List.of()),
              new Message("gui.disquests.people.remove_with_mine_one", List.of(1))),
          PeopleText.removeChoice(draft(), LEO, JUJU, "Juju", 1));
    }

    @Test
    void asksWithSeveralSubquests() {
      assertEquals(
          new RemoveChoice(
              new Message("gui.disquests.people.remove_question", List.of("Juju")),
              new Message("gui.disquests.people.remove_this_only", List.of()),
              new Message("gui.disquests.people.remove_with_mine_many", List.of(3))),
          PeopleText.removeChoice(draft(), LEO, JUJU, "Juju", 3));
    }

    @Test
    void doesNotAskWithoutSubquestsHoldingTheirRole() {
      assertNull(PeopleText.removeChoice(draft(), LEO, JUJU, "Juju", 0));
    }

    @Test
    void doesNotAskForSomeoneAddedInTheDraft() {
      PeopleDraft d = draft();
      d.add(NEWCOMER, "p5", Role.READ);
      assertNull(PeopleText.removeChoice(d, LEO, NEWCOMER, "p5", 2));
    }

    @Test
    void doesNotAskOnMyOwnRow() {
      assertNull(PeopleText.removeChoice(draft(), LEO, LEO, "Leo", 3));
    }

    /**
     * Leo owns hub, a, b and d (under a); Juju holds a role on a and d, and on c, which Max owns; y
     * is Leo's but sits under x, a Private quest of Max's that Leo cannot see.
     */
    private static final QuestGraph TREE =
        graph(
            quest("hub").reader(JUJU),
            quest("a").in("hub").reader(JUJU),
            quest("b").in("hub"),
            quest("c").in("hub").owner(MAX).reader(JUJU),
            quest("d").in("a").writer(JUJU),
            quest("x").in("hub").owner(MAX).privateQuest(),
            quest("y").in("x").reader(JUJU));

    @ParameterizedTest(name = "{0} holds a role on {1} of Leo''s subquests")
    @CsvSource({"juju, 2", "anna, 0", "leo, 3"})
    void countsMySubquestsWhereTheyHoldARole(String who, int expected) {
      UUID person =
          switch (who) {
            case "juju" -> JUJU;
            case "anna" -> ANNA;
            default -> LEO;
          };
      assertEquals(expected, PeopleText.subquestsWithRole(TREE, LEO, id("hub"), person));
    }
  }

  @Nested
  class Open {

    static Stream<Arguments> states() {
      Quest mine = Quest.fromNetwork(quest("hub").reader(JUJU).build());
      Quest demoted = Quest.fromNetwork(quest("hub").owner(MAX).writer(LEO).build());
      Quest removed = Quest.fromNetwork(quest("hub").owner(MAX).build());
      return Stream.of(
          Arguments.of("still an owner", mine, true, OpenState.STAY),
          Arguments.of("demoted", demoted, true, OpenState.CLOSE_NO_LONGER_OWNER),
          Arguments.of("removed", removed, true, OpenState.CLOSE_NO_LONGER_OWNER),
          Arguments.of("gone", null, true, OpenState.CLOSE_GONE),
          Arguments.of("read-only viewer, quest changed", demoted, false, OpenState.STAY),
          Arguments.of("read-only viewer, quest gone", null, false, OpenState.CLOSE_GONE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("states")
    void state(String name, Quest cached, boolean managed, OpenState expected) {
      assertEquals(expected, PeopleText.openState(cached, LEO, managed));
    }
  }

  @Nested
  class Chooser {

    private static final List<Candidate> KNOWN =
        List.of(
            new Candidate(NEWCOMER, "Juniper"),
            new Candidate(JUJU, "Juju"),
            new Candidate(new UUID(0, 6), "Bob"));

    /** Leo (me), Juju (removed in the draft), Anna. */
    private static List<Person> rows() {
      return List.of(
          new Person(LEO, "Leo", Role.OWNER, false, false),
          new Person(JUJU, "Juju", Role.READ, true, true),
          new Person(ANNA, "Anna", Role.WRITE, false, false));
    }

    @ParameterizedTest(name = "\"{0}\" offers {1}")
    @CsvSource({
      "ju, 'Juniper,Juju'",
      "JU, 'Juniper,Juju'",
      "b, Bob",
      "le, ''",
      "an, ''",
      "' ', ''",
      "juju, ''"
    })
    void suggestions(String typed, String expected) {
      List<String> names =
          PeopleText.suggestions(typed, KNOWN, rows(), 8).stream().map(Candidate::name).toList();
      assertEquals(expected.isEmpty() ? List.of() : List.of(expected.split(",")), names);
    }

    @Test
    void suggestionsStopAtTheLimit() {
      assertEquals(1, PeopleText.suggestions("u", KNOWN, rows(), 1).size());
    }

    static Stream<Arguments> matches() {
      return Stream.of(
          Arguments.of("", new AddMatch(AddKind.NOTHING, null, "")),
          Arguments.of("bob", new AddMatch(AddKind.ADD, new UUID(0, 6), "Bob")),
          Arguments.of(" Juniper ", new AddMatch(AddKind.ADD, NEWCOMER, "Juniper")),
          Arguments.of("juju", new AddMatch(AddKind.RESTORE, JUJU, "Juju")),
          Arguments.of("ANNA", new AddMatch(AddKind.ALREADY_LISTED, ANNA, "Anna")),
          Arguments.of("leo", new AddMatch(AddKind.ALREADY_LISTED, LEO, "Leo")),
          Arguments.of("Zed", new AddMatch(AddKind.UNKNOWN, null, "Zed")));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @MethodSource("matches")
    void addMatch(String typed, AddMatch expected) {
      assertEquals(expected, PeopleText.addMatch(typed, KNOWN, rows()));
    }
  }

  @Nested
  class ApplyToSubquests {

    static Stream<Arguments> states() {
      Person clean = new Person(JUJU, "Juju", Role.READ, false, false);
      Person changed = new Person(JUJU, "Juju", Role.WRITE, false, true);
      Person removed = new Person(JUJU, "Juju", Role.READ, true, true);
      Person me = new Person(LEO, "Leo", Role.OWNER, false, false);
      return Stream.of(
          Arguments.of("no subquests of mine", clean, 0, ApplyDown.HIDDEN),
          Arguments.of("a saved row", clean, 2, ApplyDown.ENABLED),
          Arguments.of("a changed row", changed, 2, ApplyDown.SAVE_FIRST),
          Arguments.of("a removed row", removed, 1, ApplyDown.SAVE_FIRST),
          Arguments.of("my own row", me, 2, ApplyDown.HIDDEN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("states")
    void button(String name, Person person, int mySubquests, ApplyDown expected) {
      assertEquals(expected, PeopleText.applyDown(person, LEO, mySubquests));
    }

    @ParameterizedTest(name = "{0} subquests")
    @CsvSource({
      "1, gui.disquests.people.apply_down_question_one",
      "4, gui.disquests.people.apply_down_question_many"
    })
    void question(int n, String key) {
      assertEquals(
          new Message(
              key, List.of("Juju", new Message("gui.disquests.role.write", List.of()), n, "Hub")),
          PeopleText.applyDownQuestion("Juju", Role.WRITE, n, "Hub"));
    }
  }
}
