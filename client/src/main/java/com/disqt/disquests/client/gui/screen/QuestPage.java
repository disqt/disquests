package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.data.NoticeText.Message;
import com.disqt.disquests.client.gui.tree.QuestTreeRows;
import com.disqt.disquests.common.graph.Plans.Counts;
import com.disqt.disquests.common.graph.QuestGraph;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.Privatized;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import com.disqt.disquests.common.model.Visibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The rules of the quest reading view, Minecraft-free so they can be unit-tested: which buttons and
 * sections a player gets, the texts of the complete and delete dialogs and of the banners, and the
 * order of the people list. Texts are lang keys with arguments, turned into components by {@code
 * Messages}.
 */
public final class QuestPage {

  /** Lang keys the reading view uses without arguments decided here. */
  public static final String QUEST_TREE = "gui.disquests.section.quest_tree";

  public static final String PREREQUISITES = "gui.disquests.section.prerequisites";
  public static final String WAITING_PREREQUISITES = "gui.disquests.waiting.prerequisites";
  public static final String WAITING_SOMETHING_ELSE = "gui.disquests.waiting.something_else";
  public static final String WAITING_NOTHING_VISIBLE = "gui.disquests.waiting.nothing_visible";
  public static final String PREREQUISITE_HIDDEN = "gui.disquests.prerequisite.hidden";
  public static final String PREREQUISITE_DELETED = "gui.disquests.prerequisite.deleted";
  public static final String PREREQUISITE_AUTO_JOIN = "gui.disquests.prerequisite.auto_join";
  public static final String MANAGE_PEOPLE = "gui.disquests.btn.manage_people";
  public static final String KEEP_PRIVATE = "gui.disquests.btn.keep_private";

  /** Every fixed key above, for the lang-file test. */
  public static final List<String> FIXED_KEYS =
      List.of(
          QUEST_TREE,
          PREREQUISITES,
          WAITING_PREREQUISITES,
          WAITING_SOMETHING_ELSE,
          WAITING_NOTHING_VISIBLE,
          PREREQUISITE_HIDDEN,
          PREREQUISITE_DELETED,
          PREREQUISITE_AUTO_JOIN,
          MANAGE_PEOPLE,
          KEEP_PRIVATE);

  /**
   * What the reading view offers. {@code joinOrRequest}: the Join or Request button (the screen
   * picks which from the visibility). {@code prerequisites}: the Prerequisites section. The two
   * banners are for owners only.
   */
  public record Actions(
      boolean joinOrRequest,
      boolean edit,
      boolean leave,
      boolean complete,
      boolean reopen,
      boolean delete,
      boolean managePeople,
      boolean prerequisites,
      boolean privatizedBanner,
      boolean deletedPrerequisiteBanner) {}

  /** The additions to a confirm dialog: the checkbox label and the muted line, each optional. */
  public record Dialog(Message checkbox, Message extra) {}

  private QuestPage() {}

  /**
   * The buttons and sections for {@code me}. A locked view offers only Leave, to a player holding a
   * role. Otherwise: Join or Request without a role, Edit for Write and Owner, Leave for any role
   * except the last owner, Complete, Delete and the people screen for owners; a completed quest
   * keeps only Reopen and Delete, for owners. Servers before quest trees ({@code supportsTrees}
   * false) still let Write complete and reopen, as they did, and reach the people from the editor
   * only.
   */
  public static Actions actions(
      QuestData q, UUID me, boolean supportsCompletion, boolean supportsTrees) {
    Role role = q.roleOf(me);
    boolean leave = role != null && !isLastOwner(q, me);
    if (q.locked()) {
      return new Actions(false, false, leave, false, false, false, false, false, false, false);
    }
    boolean owner = role == Role.OWNER;
    boolean write = role != null && role.atLeast(Role.WRITE);
    boolean completes = supportsCompletion && (supportsTrees ? owner : write);
    if (q.isCompleted()) {
      return new Actions(false, false, false, false, completes, owner, false, false, false, false);
    }
    boolean hasLinks =
        !q.prerequisites().isEmpty() || q.hiddenPrerequisites() || q.deletedPrerequisite();
    return new Actions(
        role == null,
        write,
        leave,
        completes,
        false,
        owner,
        // An older server's people are reached from the editor only, as in 0.8.0
        owner && supportsTrees,
        write && hasLinks,
        owner && q.privatized() != null,
        owner && q.deletedPrerequisite());
  }

  /** True when {@code me} is an owner and nobody else is: the server refuses their leaving. */
  static boolean isLastOwner(QuestData q, UUID me) {
    return q.roleOf(me) == Role.OWNER && q.owners().stream().allMatch(me::equals);
  }

  /**
   * The complete dialog's additions from the counts of unfinished subquests: "Also complete my N
   * unfinished subquests" and "N others belong to other players and stay active".
   */
  public static Dialog completeDialog(Counts unfinished) {
    return dialog(unfinished, "also_complete", "others_stay_active");
  }

  /**
   * The delete dialog's additions from the counts of all subquests: "Also delete my N subquests"
   * and "N others belong to other players and are kept".
   */
  public static Dialog deleteDialog(Counts all) {
    return dialog(all, "also_delete", "others_kept");
  }

  private static Dialog dialog(Counts counts, String checkboxKey, String extraKey) {
    return new Dialog(counted(checkboxKey, counts.mine()), counted(extraKey, counts.others()));
  }

  private static Message counted(String key, int n) {
    if (n <= 0) return null;
    return new Message(
        "gui.disquests.confirm." + key + (n == 1 ? "_one" : "_many"), List.<Object>of(n));
  }

  /** Which interact button a player without a role gets. */
  public enum Interact {
    NONE,
    JOIN,
    REQUEST
  }

  /** Join on an Open quest, Request on a Closed one, nothing otherwise or with a role. */
  public static Interact interact(Actions actions, Visibility visibility) {
    if (!actions.joinOrRequest()) return Interact.NONE;
    if (visibility == Visibility.OPEN) return Interact.JOIN;
    if (visibility == Visibility.CLOSED) return Interact.REQUEST;
    return Interact.NONE;
  }

  /** One prerequisite the player can see: a link, ticked when completed. */
  public record Link(UUID id, String title, boolean completed) {}

  /**
   * The lines of the Prerequisites section: the visible prerequisites in the view's order, then "…
   * and something you don't have access to" when the server hid some or the client does not have
   * one of them, "a deleted quest", and the auto-join line. With a deleted prerequisite, an id the
   * client does not have is that deleted quest: it shows as "a deleted quest" only.
   */
  public record Prerequisites(
      List<Link> links, boolean somethingHidden, boolean deleted, boolean autoJoin) {}

  public static Prerequisites prerequisites(QuestData q, QuestGraph graph) {
    List<Link> links = new ArrayList<>();
    boolean missing = false;
    for (UUID id : q.prerequisites()) {
      QuestData p = graph.get(id);
      if (p == null) {
        missing = true;
      } else {
        links.add(new Link(id, p.title(), p.isCompleted()));
      }
    }
    return new Prerequisites(
        List.copyOf(links),
        q.hiddenPrerequisites() || (missing && !q.deletedPrerequisite()),
        q.deletedPrerequisite(),
        q.autoJoin());
  }

  /** One line of the reading view's quest tree pane as drawn. */
  public record PaneLine(QuestTreeRows.Row row, String title, boolean completed) {}

  /**
   * Everything the reading view draws for a quest and a viewer: the quest's own view (so role,
   * people, visibility, completion, lock, banners and prerequisite flags), the pane's lines, and
   * the prerequisite lines (which also drive the locked page's waiting text). Two equal signatures
   * draw the same page; the open page is rebuilt when its signature changes. The dialog counts are
   * not part of it: the dialogs read them when they open.
   */
  public record PageSignature(QuestData quest, List<PaneLine> pane, Prerequisites prerequisites) {}

  /** {@code trees}: the server supports quest trees, so the pane is drawn. */
  public static PageSignature signature(
      QuestData quest, QuestGraph graph, UUID me, Set<UUID> expanded, boolean trees) {
    List<PaneLine> pane = new ArrayList<>();
    if (trees) {
      for (QuestTreeRows.Row row : QuestTreeRows.readingTree(graph, me, quest.id(), expanded)) {
        QuestData q = graph.get(row.id());
        if (q != null) pane.add(new PaneLine(row, q.title(), q.isCompleted()));
      }
    }
    return new PageSignature(drawn(quest), List.copyOf(pane), prerequisites(quest, graph));
  }

  /**
   * The quest as the page draws it: the time of the last save is never shown, and the server stamps
   * a new one on every save, including the echo of the player's own checkbox tick.
   */
  private static QuestData drawn(QuestData q) {
    return new QuestData(
        q.id(),
        q.title(),
        q.content(),
        q.ownerUuid(),
        q.ownerName(),
        q.visibility(),
        q.contributors(),
        0L,
        q.coordinates(),
        q.isRegion(),
        q.coordinates2(),
        q.map(),
        q.tags(),
        q.polygon(),
        q.completedAt(),
        q.completedBy(),
        q.completedByName(),
        q.parentId(),
        q.prerequisites(),
        q.hiddenPrerequisites(),
        q.deletedPrerequisite(),
        q.hiddenParent(),
        q.autoJoin(),
        q.locked(),
        q.privatized());
  }

  /** "Owner", "Write", "Read", or "not joined" without a role. */
  public static Message role(Role role) {
    String name = role == null ? "none" : role.name().toLowerCase(Locale.ROOT);
    return new Message("gui.disquests.role." + name, List.of());
  }

  /**
   * The "Set to Private" banner: with the deleted quest's title and who deleted it when the server
   * sent both (the owner could see that quest), otherwise without.
   */
  public static Message privatizedBanner(Privatized p, String date) {
    Message previous = visibility(p.previous());
    if (p.deletedTitle() != null && p.deletedByName() != null) {
      return new Message(
          "gui.disquests.banner.privatized_titled",
          List.of(date, p.deletedTitle(), p.deletedByName(), previous));
    }
    return new Message("gui.disquests.banner.privatized", List.of(date, previous));
  }

  /** "This quest waits on a deleted quest and stays locked. Open Edit to remove that link." */
  public static Message deletedPrerequisiteBanner() {
    return new Message("gui.disquests.banner.deleted_prerequisite", List.of());
  }

  /** "Set back to Open" (or Closed). */
  public static Message restoreVisibility(Visibility previous) {
    return new Message("gui.disquests.btn.restore_visibility", List.of(visibility(previous)));
  }

  private static Message visibility(Visibility v) {
    Visibility shown = v == null ? Visibility.PRIVATE : v;
    return new Message(
        "gui.disquests.visibility." + shown.name().toLowerCase(Locale.ROOT), List.of());
  }

  /**
   * Everyone holding a role, owners first (the display owner leading), then Write, then Read; the
   * order within a role is the server's.
   */
  public static List<ContributorData> people(QuestData q) {
    List<ContributorData> people = new ArrayList<>();
    if (q.ownerUuid() != null) {
      people.add(new ContributorData(q.ownerUuid(), q.ownerName(), Role.OWNER));
    }
    List<ContributorData> others = new ArrayList<>();
    for (ContributorData c : q.contributors()) {
      if (!c.uuid().equals(q.ownerUuid())) others.add(c);
    }
    others.sort(Comparator.comparing(ContributorData::role).reversed());
    people.addAll(others);
    return people;
  }
}
