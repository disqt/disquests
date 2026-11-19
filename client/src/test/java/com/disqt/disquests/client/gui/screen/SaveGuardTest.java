package com.disqt.disquests.client.gui.screen;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.JUJU;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.MAX;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.screen.SaveGuard.Outcome;
import com.disqt.disquests.client.gui.tree.TreeFixtures.Q;
import com.disqt.disquests.common.model.Role;
import java.util.stream.Stream;
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
}
