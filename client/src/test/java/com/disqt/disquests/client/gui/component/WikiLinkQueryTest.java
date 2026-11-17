package com.disqt.disquests.client.gui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WikiLinkQueryTest {

  static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of("just opened", "[[", 2, ""),
        Arguments.of("partial title", "see [[Ga", 8, "Ga"),
        Arguments.of("closed link", "[[Gare]] x", 10, null),
        Arguments.of("second link open", "[[a]] [[b", 9, "b"),
        Arguments.of("cursor before the link", "ab [[c", 2, null),
        Arguments.of("cursor inside a closed link", "[[Gare]]", 4, "Ga"),
        Arguments.of("a [[ straddling the cursor", "[[ab[[", 5, null),
        Arguments.of("single bracket", "[a", 2, null),
        Arguments.of("cursor too early", "[[", 1, null),
        Arguments.of("cursor past the end", "[[a", 9, null),
        Arguments.of("no text", null, 0, null));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void findsTheLinkBeingTyped(String name, String text, int cursor, String expected) {
    assertEquals(expected, WikiLinkQuery.at(text, cursor));
  }
}
