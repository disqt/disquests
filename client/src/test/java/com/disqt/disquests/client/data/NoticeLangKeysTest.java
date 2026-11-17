package com.disqt.disquests.client.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.disqt.disquests.client.gui.screen.EditorText;
import com.disqt.disquests.client.gui.screen.PeopleText;
import com.disqt.disquests.client.gui.screen.QuestPage;
import com.disqt.disquests.client.gui.tree.SuggestionText;
import com.disqt.disquests.client.gui.tree.Suggestions;
import com.disqt.disquests.common.graph.Plans;
import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.Notice;
import com.disqt.disquests.common.model.Privatized;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
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
 * Checks that every lang key {@link NoticeText}, {@link SuggestionText}, {@link QuestPage} and
 * {@link PeopleText} can emit, and the ones the tree rows and quest list entries draw, exist in
 * both language files. The files use owo's nested syntax, so a key {@code
 * gui.disquests.refusal.loop} is looked up as the block {@code "gui.disquests.refusal.{}": { ... }}
 * (text from its opening brace to the first closing brace, the blocks hold only flat strings)
 * containing {@code "loop":}.
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
    for (String note : new String[] {null, "", "READ", "WRITE", Suggestions.INSIDE_ANOTHER}) {
      for (Refusal r : new Refusal[] {null, Refusal.NEED_OWNER}) {
        SuggestionText.Line line =
            SuggestionText.of(new Suggestions.Suggestion(UUID.randomUUID(), "t", r, note), 3);
        if (line.reason() != null) collect(line.reason(), keys);
        if (line.note() != null) collect(line.note(), keys);
      }
    }
    for (int n = 1; n <= 2; n++) {
      Plans.Counts counts = new Plans.Counts(n, n);
      for (QuestPage.Dialog d :
          List.of(QuestPage.completeDialog(counts), QuestPage.deleteDialog(counts))) {
        collect(d.checkbox(), keys);
        collect(d.extra(), keys);
      }
    }
    for (Role role : new Role[] {null, Role.READ, Role.WRITE, Role.OWNER}) {
      keys.add(QuestPage.role(role).key());
    }
    for (Visibility v : Visibility.values()) {
      collect(QuestPage.privatizedBanner(new Privatized(v, "t", "n", 1L), "d"), keys);
      collect(QuestPage.privatizedBanner(new Privatized(v, null, null, 1L), "d"), keys);
      collect(QuestPage.restoreVisibility(v), keys);
    }
    keys.add(QuestPage.deletedPrerequisiteBanner().key());
    keys.addAll(QuestPage.FIXED_KEYS);
    keys.addAll(EditorText.keys());
    keys.addAll(PeopleText.keys());
    keys.addAll(TREE_ROW_KEYS);
    keys.addAll(QUEST_ENTRY_KEYS);
    return keys.stream().distinct().toList();
  }

  /** Keys {@code TreeRowComponent} draws (a Minecraft class, so listed here by hand). */
  private static final List<String> TREE_ROW_KEYS =
      List.of("gui.disquests.tree.not_saved", "gui.disquests.tree.taken_out");

  /** Keys {@code QuestEntryComponent} draws for its marks (a Minecraft class, listed by hand). */
  private static final List<String> QUEST_ENTRY_KEYS =
      List.of("gui.disquests.label.new", "gui.disquests.tooltip.needs_attention");

  private static void collect(NoticeText.Message m, List<String> keys) {
    keys.add(m.key());
    for (Object arg : m.args()) {
      if (arg instanceof NoticeText.Message nested) collect(nested, keys);
    }
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
