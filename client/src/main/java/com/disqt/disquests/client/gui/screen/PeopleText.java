package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.data.NoticeText.Message;
import com.disqt.disquests.client.gui.tree.PeopleDraft;
import com.disqt.disquests.common.graph.Plans;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import java.util.List;
import java.util.UUID;

/**
 * The people screen's texts and decisions, Minecraft-free: what Save does, which question Remove
 * asks, and when "apply to subquests" is offered.
 */
public final class PeopleText {

  private static final String P = "gui.disquests.people.";

  public static final String TITLE = P + "title";
  public static final String PENDING = P + "pending";
  public static final String YOU = P + "you";
  public static final String NOT_SAVED = EditorText.NOT_SAVED;
  public static final String REMOVED = P + "removed";
  public static final String REMOVE = "gui.disquests.btn.remove";
  public static final String UNDO = P + "undo";
  public static final String SAVE = P + "save";
  public static final String CANCEL = P + "cancel";
  public static final String ADD = "gui.disquests.btn.add";
  public static final String ADD_HINT = P + "add_hint";
  public static final String UNKNOWN_PLAYER = P + "unknown_player";
  public static final String APPLY_DOWN = P + "apply_down";
  public static final String SAVE_FIRST = P + "save_first";
  public static final String APPLY_DOWN_EXTRA = P + "apply_down_extra";
  public static final String APPLY_DOWN_SENT = P + "apply_down_sent";
  public static final String NEED_OWNER = P + "need_owner";
  public static final String GIVE_UP_OWNER = P + "give_up_owner";
  public static final String ROLES_HINT = P + "roles_hint";
  public static final String SAVE_HINT = P + "save_hint";
  public static final String REMOVE_QUESTION = P + "remove_question";
  public static final String REMOVE_THIS_ONLY = P + "remove_this_only";

  private PeopleText() {}

  /** What pressing Save does with the draft. */
  public enum SaveStep {
    /** Nothing changed: the button is disabled. */
    NOTHING,
    /** No owner would remain: refused with a toast, nothing sent. */
    NO_OWNER_LEFT,
    /** The player gives up their own Owner role: asked first. */
    ASK_GIVING_UP_OWNER,
    SEND
  }

  /**
   * Nothing to do without a net change; then refused when no owner would remain, else asked first
   * when the player gives up their own Owner role.
   */
  public static SaveStep saveStep(PeopleDraft draft, UUID me) {
    if (draft.ops(me).isEmpty()) return SaveStep.NOTHING;
    if (!draft.keepsAnOwner()) return SaveStep.NO_OWNER_LEFT;
    if (draft.givesUpOwnOwner(me)) return SaveStep.ASK_GIVING_UP_OWNER;
    return SaveStep.SEND;
  }

  /** The three-button question Remove asks. */
  public record RemoveChoice(Message question, Message thisOnly, Message withMine) {}

  /**
   * "From this quest only" or "From this quest and my N subquests", where N counts the player's
   * subquests on which the person holds a role. Null when there are none, or for someone added in
   * this draft: the row is removed without asking.
   */
  public static RemoveChoice removeChoice(
      PeopleDraft draft, UUID person, String name, int subquestsWithRole) {
    if (subquestsWithRole <= 0 || draft.isNew(person)) return null;
    return new RemoveChoice(
        new Message(REMOVE_QUESTION, List.of(name)),
        new Message(REMOVE_THIS_ONLY, List.of()),
        new Message(
            P + "remove_with_mine_" + (subquestsWithRole == 1 ? "one" : "many"),
            List.of(subquestsWithRole)));
  }

  /**
   * How many of the player's subquests of the quest ({@link Plans#mySubquests}) the person is on.
   */
  public static int subquestsWithRole(QuestGraph g, UUID me, UUID quest, UUID person) {
    int n = 0;
    for (UUID id : Plans.mySubquests(g, me, quest)) {
      QuestData q = g.get(id);
      if (q != null && q.roleOf(person) != null) n++;
    }
    return n;
  }

  /** How a row's "apply to subquests" button shows. */
  public enum ApplyDown {
    HIDDEN,
    SAVE_FIRST,
    ENABLED
  }

  /**
   * Hidden when the player owns no subquest of the quest, and on the player's own row (they already
   * own those subquests); "Save first" on a row with unsaved changes or removed in the draft.
   */
  public static ApplyDown applyDown(PeopleDraft.Person person, UUID me, int mySubquests) {
    if (mySubquests <= 0 || person.uuid().equals(me)) return ApplyDown.HIDDEN;
    if (person.dirty() || person.removed()) return ApplyDown.SAVE_FIRST;
    return ApplyDown.ENABLED;
  }

  /** "Give Juju Write on your N subquests of Westenheim hub?", N the player's own subquests. */
  public static Message applyDownQuestion(String name, Role role, int mySubquests, String title) {
    return new Message(
        P + "apply_down_question_" + (mySubquests == 1 ? "one" : "many"),
        List.of(name, QuestPage.role(role), mySubquests, title));
  }

  /** Every key the people screen draws, for the lang-file test. */
  public static List<String> keys() {
    return List.of(
        TITLE,
        PENDING,
        YOU,
        NOT_SAVED,
        REMOVED,
        REMOVE,
        UNDO,
        SAVE,
        CANCEL,
        ADD,
        ADD_HINT,
        UNKNOWN_PLAYER,
        APPLY_DOWN,
        SAVE_FIRST,
        APPLY_DOWN_EXTRA,
        APPLY_DOWN_SENT,
        NEED_OWNER,
        GIVE_UP_OWNER,
        ROLES_HINT,
        SAVE_HINT,
        REMOVE_QUESTION,
        REMOVE_THIS_ONLY,
        P + "remove_with_mine_one",
        P + "remove_with_mine_many",
        P + "apply_down_question_one",
        P + "apply_down_question_many");
  }
}
