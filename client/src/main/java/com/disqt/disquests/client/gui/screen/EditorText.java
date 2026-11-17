package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.common.model.Visibility;
import java.util.List;

/**
 * Lang keys of the quest editor's tree pane and link controls, Minecraft-free so the lang-file test
 * can list them.
 */
public final class EditorText {

  public static final String SUBQUESTS = "gui.disquests.section.subquests";
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
  public static final String NOT_SAVED = "gui.disquests.tree.not_saved";
  public static final String TAKEN_OUT = "gui.disquests.tree.taken_out";

  private EditorText() {}

  /** The visibility's name, as on the badge. */
  public static String visibility(Visibility v) {
    return "gui.disquests.visibility." + v.name().toLowerCase(java.util.Locale.ROOT);
  }

  /** The visibility's tooltip, shown as a muted line beside the three buttons. */
  public static String visibilityHint(Visibility v) {
    return "gui.disquests.tooltip." + v.name().toLowerCase(java.util.Locale.ROOT);
  }

  /** Every key above, for the lang-file test. */
  public static List<String> keys() {
    List<String> keys = new java.util.ArrayList<>();
    keys.addAll(
        List.of(
            SUBQUESTS,
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
            NOT_SAVED,
            TAKEN_OUT));
    for (Visibility v : Visibility.values()) {
      keys.add(visibility(v));
      keys.add(visibilityHint(v));
    }
    return keys;
  }
}
