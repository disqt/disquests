package com.disqt.disquests.common.graph;

import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.Privatized;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What one player may learn about a quest. Every quest the server sends goes through {@link
 * #forRecipient}, so nothing about a quest the player cannot see leaves the server: no title, id,
 * count, content, place or name.
 *
 * <p>The view is output only. It is never put back into a graph and no decision is taken on it; in
 * particular the placeholder display owner of a locked view ({@link #NO_OWNER}) is not a player.
 */
public final class QuestView {

  /** The display owner of a locked view, which carries no people. */
  public static final UUID NO_OWNER = new UUID(0, 0);

  private QuestView() {}

  /**
   * The quest as {@code recipient} may have it, or null when they cannot see it.
   *
   * <ul>
   *   <li>{@code parentId} only when the recipient can see the parent. {@code hiddenParent} tells
   *       an owner of the quest that it sits inside a quest they cannot see, so they can detach it;
   *       nobody else learns that there is a parent.
   *   <li>{@code prerequisites} only those the recipient can see. {@code hiddenPrerequisites} says
   *       there are others, never how many. {@code deletedPrerequisite} says one of them no longer
   *       exists, and only to owners of the quest; for everyone else a deleted prerequisite counts
   *       as a hidden one.
   *   <li>{@code privatized} only for owners, without the deleted quest's title and its deleter's
   *       name for an owner who could not see it, and never with {@code sawDeleted}.
   *   <li>{@code locked} is whether the quest is locked for the recipient. A locked view keeps the
   *       id, title, visibility, the links above and the recipient's own entry if they hold a role;
   *       everything else is emptied, including the time of the last change, so an edit of the
   *       hidden text does not reach them.
   * </ul>
   */
  public static QuestData forRecipient(QuestGraph g, UUID recipient, UUID quest) {
    if (!g.canSee(recipient, quest)) return null;
    QuestData q = g.get(quest);
    boolean owner = q.roleOf(recipient) == Role.OWNER;

    QuestData parent = g.get(q.parentId());
    boolean parentVisible = parent != null && g.canSee(recipient, parent.id());
    UUID parentId = parentVisible ? parent.id() : null;
    boolean hiddenParent = owner && parent != null && !parentVisible;

    List<UUID> visible = new ArrayList<>();
    boolean hidden = false;
    boolean deleted = false;
    for (UUID prerequisite : q.prerequisites()) {
      if (g.get(prerequisite) == null) {
        if (owner) deleted = true;
        else hidden = true;
      } else if (g.canSee(recipient, prerequisite)) {
        visible.add(prerequisite);
      } else {
        hidden = true;
      }
    }

    boolean locked = g.lockedFor(recipient, quest);
    if (locked) return lockedView(q, recipient, parentId, visible, hidden, deleted);

    return q.withParent(parentId)
        .withLinksView(List.copyOf(visible), hidden, deleted, false)
        .withHiddenParent(hiddenParent)
        .withPrivatized(owner ? privatizedFor(q.privatized(), recipient) : null);
  }

  private static QuestData lockedView(
      QuestData q,
      UUID recipient,
      UUID parentId,
      List<UUID> prerequisites,
      boolean hidden,
      boolean deleted) {
    List<ContributorData> ownEntry =
        q.contributors().stream().filter(c -> recipient.equals(c.uuid())).toList();
    return new QuestData(
        q.id(),
        q.title(),
        "",
        NO_OWNER,
        "",
        q.visibility(),
        ownEntry,
        0L,
        null,
        false,
        null,
        null,
        List.of(),
        List.of(),
        0L,
        null,
        null,
        parentId,
        List.copyOf(prerequisites),
        hidden,
        deleted,
        false,
        false,
        true,
        null);
  }

  private static Privatized privatizedFor(Privatized stored, UUID owner) {
    if (stored == null) return null;
    boolean saw = stored.sawDeleted().contains(owner);
    return new Privatized(
        stored.previous(),
        saw ? stored.deletedTitle() : null,
        saw ? stored.deletedByName() : null,
        stored.at(),
        Set.of());
  }
}
