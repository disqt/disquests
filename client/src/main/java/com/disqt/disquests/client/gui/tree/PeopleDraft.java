package com.disqt.disquests.client.gui.tree;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.PersonOp;
import com.disqt.disquests.common.model.PersonOp.Kind;
import com.disqt.disquests.common.model.QuestData;
import com.disqt.disquests.common.model.Role;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The people screen's draft: role changes, additions and removals held until Save. The rows are
 * sorted once, owners first, then Write, then Read, each group in the order the quest lists them; a
 * role change never moves a row and a person added in the draft goes last.
 */
public final class PeopleDraft {

  /** One row of the people screen. {@code dirty}: added, removed or role changed in the draft. */
  public record Person(UUID uuid, String name, Role role, boolean removed, boolean dirty) {}

  private static final class Entry {
    final UUID uuid;
    final String name;
    final Role baseRole; // null: added in the draft
    Role role;
    boolean removed;
    boolean withMySubquests;

    Entry(UUID uuid, String name, Role baseRole, Role role) {
      this.uuid = uuid;
      this.name = name;
      this.baseRole = baseRole;
      this.role = role;
    }

    boolean dirty() {
      return baseRole == null || removed || role != baseRole;
    }
  }

  private final List<Entry> entries = new ArrayList<>();

  /** The quest's display owner and contributors. */
  public PeopleDraft(Quest quest) {
    QuestData q = quest.toData();
    List<Entry> people = new ArrayList<>();
    people.add(new Entry(q.ownerUuid(), q.ownerName(), Role.OWNER, Role.OWNER));
    for (ContributorData c : q.contributors()) {
      if (c.uuid().equals(q.ownerUuid())) continue;
      people.add(new Entry(c.uuid(), c.name(), c.role(), c.role()));
    }
    people.sort(Comparator.comparing((Entry e) -> e.baseRole).reversed());
    entries.addAll(people);
  }

  /** The rows in their fixed order. */
  public List<Person> rows() {
    List<Person> rows = new ArrayList<>();
    for (Entry e : entries) rows.add(new Person(e.uuid, e.name, e.role, e.removed, e.dirty()));
    return rows;
  }

  public void setRole(UUID uuid, Role role) {
    Entry e = find(uuid);
    if (e != null) e.role = role;
  }

  /**
   * Marks the person removed, from this quest only or from the player's subquests too. A person
   * added in this draft is simply dropped.
   */
  public void remove(UUID uuid, boolean withMySubquests) {
    Entry e = find(uuid);
    if (e == null) return;
    if (e.baseRole == null) {
      entries.remove(e);
      return;
    }
    e.removed = true;
    e.withMySubquests = withMySubquests;
  }

  public void undoRemove(UUID uuid) {
    Entry e = find(uuid);
    if (e != null) e.removed = false;
  }

  /** Adds the person last; someone already listed gets the role instead and is not removed. */
  public void add(UUID uuid, String name, Role role) {
    Entry e = find(uuid);
    if (e == null) {
      entries.add(new Entry(uuid, name, null, role));
      return;
    }
    e.removed = false;
    e.role = role;
  }

  public boolean isDirty(UUID uuid) {
    Entry e = find(uuid);
    return e != null && e.dirty();
  }

  /** At least one owner is left after the draft. */
  public boolean keepsAnOwner() {
    return entries.stream().anyMatch(e -> !e.removed && e.role == Role.OWNER);
  }

  /** {@code me} was an owner and would not be after Save, demoted or removed. */
  public boolean givesUpOwnOwner(UUID me) {
    Entry e = find(me);
    return e != null && e.baseRole == Role.OWNER && (e.removed || e.role != Role.OWNER);
  }

  /** The net changes, promotions to Owner first, in row order. */
  public List<PersonOp> ops() {
    return ops(null);
  }

  /**
   * The net changes: promotions to Owner first, then the others in row order, and {@code me}'s own
   * change last, so the player keeps the rights the other changes need until the end.
   */
  public List<PersonOp> ops(UUID me) {
    List<PersonOp> promotions = new ArrayList<>();
    List<PersonOp> others = new ArrayList<>();
    PersonOp mine = null;
    for (Entry e : entries) {
      if (!e.dirty()) continue;
      PersonOp op =
          e.removed
              ? new PersonOp(
                  e.withMySubquests ? Kind.REMOVE_WITH_MY_SUBQUESTS : Kind.REMOVE, e.uuid, null)
              : new PersonOp(Kind.SET_ROLE, e.uuid, e.role);
      if (e.uuid.equals(me)) mine = op;
      else if (op.kind() == Kind.SET_ROLE && op.role() == Role.OWNER) promotions.add(op);
      else others.add(op);
    }
    List<PersonOp> ops = new ArrayList<>(promotions);
    ops.addAll(others);
    if (mine != null) ops.add(mine);
    return ops;
  }

  private Entry find(UUID uuid) {
    for (Entry e : entries) {
      if (e.uuid.equals(uuid)) return e;
    }
    return null;
  }
}
