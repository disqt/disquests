package com.disqt.disquests.client.markdown;

import static com.disqt.disquests.client.gui.tree.TreeFixtures.LEO;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.id;
import static com.disqt.disquests.client.gui.tree.TreeFixtures.quest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.disqt.disquests.client.data.Quest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WikiLinksTest {

  /** The cache: "Mine" fully visible, "Gate" held as a locked view; "Away" is not cached. */
  private static final Map<UUID, Quest> CACHE = new HashMap<>();

  static {
    CACHE.put(id("Mine"), Quest.fromNetwork(quest("Mine").owner(LEO).build()));
    CACHE.put(id("Gate"), Quest.fromNetwork(quest("Gate").owner(LEO).locked().build()));
  }

  private static final Function<UUID, Quest> LOOKUP = CACHE::get;

  @Nested
  class Target {

    @Test
    void aFullyVisibleQuestResolves() {
      assertSame(CACHE.get(id("Mine")), WikiLinks.target(id("Mine").toString(), LOOKUP));
    }

    static Stream<Arguments> unresolved() {
      return Stream.of(
          Arguments.of("locked view", id("Gate").toString()),
          Arguments.of("not in the cache", id("Away").toString()),
          Arguments.of("no id", ""),
          Arguments.of("null id", null),
          Arguments.of("malformed id", "not-a-uuid"),
          Arguments.of("broken marker", "broken"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unresolved")
    void doesNotResolve(String name, String linkId) {
      assertNull(WikiLinks.target(linkId, LOOKUP));
    }
  }

  @Nested
  class EditingForm {

    static Stream<Arguments> conversions() {
      String mine = id("Mine").toString();
      String gate = id("Gate").toString();
      String away = id("Away").toString();
      return Stream.of(
          Arguments.of("visible quest: its current title", "[[" + mine + "|Old]]", "[[Mine]]"),
          Arguments.of(
              "locked view: kept as sent",
              "[[" + gate + "|Private Quest]]",
              "[[" + gate + "|Private Quest]]"),
          Arguments.of(
              "not cached: kept as sent",
              "[[" + away + "|Private Quest]]",
              "[[" + away + "|Private Quest]]"),
          Arguments.of("broken link: its text", "[[|Nowhere]]", "[[Nowhere]]"),
          Arguments.of("malformed id: its text", "[[abc|Text]]", "[[Text]]"),
          Arguments.of("raw link untouched", "see [[Mine]]", "see [[Mine]]"),
          Arguments.of(
              "mixed",
              "a [[" + mine + "|x]] b [[" + away + "|Private Quest]] c",
              "a [[Mine]] b [[" + away + "|Private Quest]] c"),
          Arguments.of("empty", "", ""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("conversions")
    void converts(String name, String received, String editing) {
      assertEquals(editing, WikiLinks.toEditingForm(received, LOOKUP));
    }

    /** Both dirty-tracking entry points convert what they get again: nothing may change twice. */
    @Test
    void convertingTwiceChangesNothing() {
      String received =
          "[[" + id("Mine") + "|x]] [[" + id("Gate") + "|Private Quest]] [[|Nowhere]]";
      String once = WikiLinks.toEditingForm(received, LOOKUP);
      assertEquals(once, WikiLinks.toEditingForm(once, LOOKUP));
    }

    @Test
    void nullStaysNull() {
      assertNull(WikiLinks.toEditingForm(null, LOOKUP));
    }
  }
}
