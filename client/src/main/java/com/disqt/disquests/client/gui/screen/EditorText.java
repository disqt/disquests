package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.common.model.Visibility;
import java.util.List;

/**
 * Lang keys of the quest editor's tree pane and link controls, Minecraft-free so the lang-file test
 * can list them.
 */
public final class EditorText {

  public static final String QUEST_TREE = "gui.disquests.section.quest_tree";
  public static final String ADD_SUBQUEST = "gui.disquests.edit.add_subquest";
  public static final String NEW_QUEST = "gui.disquests.edit.new_quest";
  public static final String DROP_OUT = "gui.disquests.edit.drop_out";
  public static final String TREE_HINT = "gui.disquests.edit.tree_hint";
  public static final String HIDDEN_PARENT = "gui.disquests.edit.hidden_parent";
  public static final String DETACH = "gui.disquests.edit.detach";
  public static final String VISIBILITY = "gui.disquests.edit.visibility";
  public static final String VISIBILITY_IN_PARENT = "gui.disquests.edit.visibility_in_parent";
  public static final String PREREQUISITES = QuestPage.PREREQUISITES;
  public static final String PREREQUISITE_PLACEHOLDER = "gui.disquests.edit.prerequisite_field";
  public static final String PREREQUISITE_HIDDEN = "gui.disquests.edit.prerequisite_hidden";
  public static final String PREREQUISITE_DELETED = QuestPage.PREREQUISITE_DELETED;
  public static final String AUTO_JOIN = QuestPage.PREREQUISITE_AUTO_JOIN;
  public static final String OPEN_ONLY = "gui.disquests.edit.open_only";
  public static final String NOW_INSIDE = "gui.disquests.edit.now_inside";
  public static final String FORMATTING = "gui.disquests.edit.formatting";
  public static final String FORMATTING_HIDE = "gui.disquests.edit.formatting_hide";
  public static final String FORMATTING_SHOW = "gui.disquests.edit.formatting_show";

  /** The words of the formatting help's examples. */
  public static final String FMT_TEXT = "gui.disquests.edit.fmt_text";

  public static final String FMT_HEADING = "gui.disquests.edit.fmt_heading";
  public static final String FMT_TODO = "gui.disquests.edit.fmt_todo";
  public static final String FMT_DONE = "gui.disquests.edit.fmt_done";
  public static final String FMT_QUOTE = "gui.disquests.edit.fmt_quote";
  public static final String FMT_LINK = "gui.disquests.edit.fmt_link";
  public static final String FMT_QUEST_NAME = "gui.disquests.edit.fmt_quest_name";
  public static final String FMT_QUEST_LINK = "gui.disquests.edit.fmt_quest_link";
  public static final String NOT_SAVED = "gui.disquests.tree.not_saved";
  public static final String TAKEN_OUT = "gui.disquests.tree.taken_out";
  public static final String EDITING = "gui.disquests.tree.editing";
  public static final String TAKE_OUT_OF = "gui.disquests.tree.take_out_of";

  private EditorText() {}

  /** The visibility's name, as on the badge. */
  public static String visibility(Visibility v) {
    return "gui.disquests.visibility." + v.name().toLowerCase(java.util.Locale.ROOT);
  }

  /** The visibility's explanation sentence, also the tooltip of 0.8.0's visibility button. */
  public static String visibilityHint(Visibility v) {
    return "gui.disquests.tooltip." + v.name().toLowerCase(java.util.Locale.ROOT);
  }

  /**
   * The sentences of the explanation line under the visibility buttons, in order: the setting's own
   * sentence, then, for Closed and Open on a quest that sits inside another, the sentence saying it
   * applies among those who can see that quest. Private stands alone: it does not depend on the
   * parent.
   */
  public static List<String> visibilityHintKeys(Visibility v, boolean insideAnother) {
    if (v == Visibility.PRIVATE || !insideAnother) return List.of(visibilityHint(v));
    return List.of(visibilityHint(v), VISIBILITY_IN_PARENT);
  }

  /** Every key above, for the lang-file test. */
  public static List<String> keys() {
    List<String> keys = new java.util.ArrayList<>();
    keys.addAll(
        List.of(
            QUEST_TREE,
            ADD_SUBQUEST,
            NEW_QUEST,
            DROP_OUT,
            TREE_HINT,
            HIDDEN_PARENT,
            DETACH,
            VISIBILITY,
            VISIBILITY_IN_PARENT,
            PREREQUISITES,
            PREREQUISITE_PLACEHOLDER,
            PREREQUISITE_HIDDEN,
            PREREQUISITE_DELETED,
            AUTO_JOIN,
            OPEN_ONLY,
            NOW_INSIDE,
            FORMATTING,
            FORMATTING_HIDE,
            FORMATTING_SHOW,
            FMT_TEXT,
            FMT_HEADING,
            FMT_TODO,
            FMT_DONE,
            FMT_QUOTE,
            FMT_LINK,
            FMT_QUEST_NAME,
            FMT_QUEST_LINK,
            NOT_SAVED,
            TAKEN_OUT,
            EDITING,
            TAKE_OUT_OF));
    for (Visibility v : Visibility.values()) {
      keys.add(visibility(v));
      keys.add(visibilityHint(v));
    }
    return keys;
  }
}
