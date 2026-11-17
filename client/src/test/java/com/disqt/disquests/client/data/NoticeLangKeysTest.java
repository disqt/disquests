package com.disqt.disquests.client.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.Notice;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Checks that every lang key {@link NoticeText} can emit exists in both language files. The files
 * use owo's nested syntax, so a key {@code gui.disquests.refusal.loop} is looked up as the block
 * {@code "gui.disquests.refusal.{}": { ... }} (text from its opening brace to the first closing
 * brace, the blocks hold only flat strings) containing {@code "loop":}.
 */
class NoticeLangKeysTest {

  private static final Path LANG = Path.of("src/main/resources/assets/disquests/lang");

  private static List<String> emittedKeys() {
    List<String> keys = new ArrayList<>();
    for (Notice.Kind kind : Notice.Kind.values()) {
      NoticeText.Message m =
          NoticeText.of(new Notice(kind, UUID.randomUUID(), "t", Refusal.LOOP), 3);
      keys.add(m.key());
      NoticeText.Message titleless = NoticeText.of(new Notice(kind, null, null, Refusal.LOOP), 3);
      keys.add(titleless.key());
    }
    for (Refusal r : Refusal.values()) keys.add(NoticeText.refusal(r, 3).key());
    keys.add(NoticeText.refusal(null, 3).key());
    return keys.stream().distinct().toList();
  }

  private static boolean declared(String file, String key) {
    int dot = key.lastIndexOf('.');
    String block = "\"" + key.substring(0, dot) + ".{}\"";
    String inner = "\"" + key.substring(dot + 1) + "\"";
    int start = file.indexOf(block);
    if (start < 0) return false;
    int open = file.indexOf('{', start + block.length());
    int close = file.indexOf('}', open);
    return open >= 0 && close > open && file.substring(open, close).contains(inner + ":");
  }

  @TestFactory
  Stream<DynamicTest> everyEmittedKeyIsTranslated() throws IOException {
    List<DynamicTest> tests = new ArrayList<>();
    for (String lang : List.of("en_us.json5", "fr_fr.json5")) {
      String text = Files.readString(LANG.resolve(lang), StandardCharsets.UTF_8);
      for (String key : emittedKeys()) {
        tests.add(
            DynamicTest.dynamicTest(
                lang + " " + key, () -> assertTrue(declared(text, key), key + " missing")));
      }
    }
    return tests.stream();
  }
}
