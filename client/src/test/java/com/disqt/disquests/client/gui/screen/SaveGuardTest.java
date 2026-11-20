package com.disqt.disquests.client.gui.screen;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.JUJU;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.MAX;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.id;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.screen.SaveGuard.Outcome;
import com.disqt.disquests.client.gui.tree.TreeFixtures.Q;
import com.disqt.disquests.common.model.LinkChanges;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.TreeOp;
import com.disqt.disquests.common.model.Visibility;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SaveGuardTest {

  /** The quest as the cache holds it now: owned by MAX, JUJU (the saver) in the given role. */
  private static Q cachedWith(Role juju) {
    Q q = quest("Gare").owner(MAX);
    if (juju != null) q.role(JUJU, juju);
    return q;
  }

  private static Quest of(Q q) {
    return Quest.fromNetwork(q.build());
  }

  static Stream<Arguments> outcomes() {
    return Stream.of(
        Arguments.of("still Write", of(cachedWith(Role.WRITE)), false, Outcome.SEND),
        Arguments.of("now Owner", of(cachedWith(Role.OWNER)), false, Outcome.SEND),
        Arguments.of(
            "demoted to Read", of(cachedWith(Role.READ)), false, Outcome.NO_LONGER_EDITABLE),
        Arguments.of("removed", of(cachedWith(null)), false, Outcome.NO_LONGER_EDITABLE),
        Arguments.of(
            "locked meanwhile",
            of(cachedWith(Role.WRITE).after("Dig").locked()),
            false,
            Outcome.NO_LONGER_EDITABLE),
        Arguments.of(
            "completed meanwhile", of(cachedWith(Role.WRITE).completed()), false, Outcome.ARCHIVED),
        Arguments.of("gone", null, false, Outcome.GONE),
        Arguments.of("a new quest, not cached yet", null, true, Outcome.SEND));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("outcomes")
  void check(String name, Quest cached, boolean newQuest, Outcome expected) {
    assertEquals(expected, SaveGuard.check(cached, JUJU, newQuest));
  }

  private static final TreeOp MOVE = new TreeOp(TreeOp.Kind.ATTACH, id("A"), id("B"));

  /** Every kind of link change: a tree move and all the owner-only parts. */
  private static final LinkChanges EVERYTHING =
      new LinkChanges(
          Visibility.OPEN,
          List.of(MOVE),
          List.of(id("Add")),
          List.of(id("Gone")),
          true,
          true,
          Boolean.TRUE);

  private static final LinkChanges TREE_ONLY =
      new LinkChanges(null, List.of(MOVE), List.of(), List.of(), false, false, null);

  static Stream<Arguments> links() {
    return Stream.of(
        Arguments.of(
            "still Owner: everything sent",
            of(cachedWith(Role.OWNER)),
            EVERYTHING,
            new SaveGuard.Links(EVERYTHING, false)),
        Arguments.of(
            "demoted to Write with owner-only changes: stripped and flagged",
            of(cachedWith(Role.WRITE)),
            EVERYTHING,
            new SaveGuard.Links(TREE_ONLY, true)),
        Arguments.of(
            "demoted to Write with tree ops only: sent, no toast",
            of(cachedWith(Role.WRITE)),
            TREE_ONLY,
            new SaveGuard.Links(TREE_ONLY, false)),
        Arguments.of(
            "demoted to Write with nothing: nothing, no toast",
            of(cachedWith(Role.WRITE)),
            LinkChanges.NONE,
            new SaveGuard.Links(LinkChanges.NONE, false)),
        Arguments.of(
            "a new quest (not cached): everything sent",
            null,
            EVERYTHING,
            new SaveGuard.Links(EVERYTHING, false)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("links")
  void linksToSend(String name, Quest cached, LinkChanges draft, SaveGuard.Links expected) {
    assertEquals(expected, SaveGuard.linksToSend(cached, JUJU, draft));
  }

  @Test
  void demotedToReadIsStillRefused() {
    assertEquals(
        Outcome.NO_LONGER_EDITABLE, SaveGuard.check(of(cachedWith(Role.READ)), JUJU, false));
  }
}
