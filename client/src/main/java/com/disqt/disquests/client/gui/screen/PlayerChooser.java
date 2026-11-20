package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.data.Contributor;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.SuggestionDropdown;
import com.disqt.disquests.client.gui.helper.Messages;
import com.disqt.disquests.client.gui.screen.PeopleText.AddMatch;
import com.disqt.disquests.client.gui.screen.PeopleText.Candidate;
import com.disqt.disquests.client.gui.tree.PeopleDraft;
import com.disqt.disquests.common.model.Role;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * The people screen's Add row ({@code add-person-row}): a name field ({@code add-name-field}) with
 * suggestions ({@code add-name-suggestions}), the role to give ({@code add-role-read/write/owner},
 * Read by default) and Add ({@code btn-add-person}). Which names are offered and what Add does with
 * a typed name are {@link PeopleText#suggestions} and {@link PeopleText#addMatch}; the players come
 * from the tab list and the people of the quests the player can see.
 */
final class PlayerChooser {

  private static final int SUGGESTIONS = 8;

  private final FlowLayout root;
  private final PeopleDraft draft;
  private final Runnable onChanged;
  private final Consumer<Component> toast;
  private final Runnable dropStaleFocus;
  private final TextBoxComponent field;
  private final SuggestionDropdown<Candidate> dropdown;
  private Role role = Role.READ;

  /**
   * @param onChanged called (queued on the root) after the draft changed
   * @param toast shows a short message
   */
  PlayerChooser(
      FlowLayout root,
      PeopleDraft draft,
      Runnable onChanged,
      Consumer<Component> toast,
      Runnable dropStaleFocus) {
    this.root = root;
    this.draft = draft;
    this.onChanged = onChanged;
    this.toast = toast;
    this.dropStaleFocus = dropStaleFocus;

    FlowLayout addRow = root.childById(FlowLayout.class, "add-person-row");
    field = UIComponents.textBox(Sizing.fixed(110));
    field.id("add-name-field");
    field.setHint(Component.translatable(PeopleText.ADD_HINT));
    field.onChanged().subscribe(this::suggest);
    addRow.child(field);
    dropdown =
        new SuggestionDropdown<Candidate>(
                root,
                "add-name-suggestions",
                c -> new SuggestionDropdown.Entry(c.name(), null, true, null),
                c -> field.text(c.name()))
            .pickOnTab(true)
            .preferAbove(true);

    FlowLayout roles = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
    roles.id("add-role-buttons");
    roles.gap(2);
    addRow.child(roles);
    fillRoles(roles);

    addRow.child(
        ContributorScreen.button(
            "btn-add-person", Component.translatable(PeopleText.ADD), this::add));
  }

  /** Arrows, Enter, Tab and Escape go to the suggestions while they are open. */
  boolean onKey(KeyEvent key) {
    return dropdown.onKey(key);
  }

  private void fillRoles(FlowLayout roles) {
    roles.clearChildren();
    for (Role r : Role.values()) {
      ButtonComponent b =
          ContributorScreen.button(
              "add-role-" + r.name().toLowerCase(Locale.ROOT),
              Messages.toComponent(QuestPage.role(r)),
              () -> {
                role = r;
                root.queue(
                    () -> {
                      fillRoles(roles);
                      dropStaleFocus.run();
                    });
              });
      b.active(role != r);
      roles.child(b);
    }
  }

  private void suggest(String text) {
    dropdown.show(PeopleText.suggestions(text, known(), draft.rows(), SUGGESTIONS), field);
  }

  /**
   * Adds a known player last with the chosen role, or restores someone removed in the draft (as
   * Undo); someone already listed is left as they are.
   */
  private void add() {
    AddMatch match = PeopleText.addMatch(field.getValue(), known(), draft.rows());
    switch (match.kind()) {
      case NOTHING -> {
        return;
      }
      case ADD -> draft.add(match.uuid(), match.name(), role);
      case RESTORE -> draft.undoRemove(match.uuid());
      case ALREADY_LISTED -> {
        toast.accept(Component.translatable(PeopleText.ALREADY_LISTED, match.name()));
        return;
      }
      case UNKNOWN -> {
        toast.accept(Component.translatable(PeopleText.UNKNOWN_PLAYER, match.name()));
        return;
      }
    }
    field.text("");
    dropdown.hide();
    onChanged.run();
  }

  /** Players online, then the people of every quest the player can see, once each. */
  private static Collection<Candidate> known() {
    Map<UUID, Candidate> found = new LinkedHashMap<>();
    var connection = Minecraft.getInstance().getConnection();
    if (connection != null) {
      for (PlayerInfo info : connection.getOnlinePlayers()) {
        var profile = info.getProfile();
        if (profile.id() != null && profile.name() != null) {
          found.putIfAbsent(profile.id(), new Candidate(profile.id(), profile.name()));
        }
      }
    }
    List<Quest> quests = new ArrayList<>(ClientCache.getMyQuests());
    quests.addAll(ClientCache.getServerQuests());
    for (Quest q : quests) {
      if (q.getOwnerUuid() != null && q.getOwnerName() != null) {
        found.putIfAbsent(q.getOwnerUuid(), new Candidate(q.getOwnerUuid(), q.getOwnerName()));
      }
      for (Contributor c : q.getContributors()) {
        if (c.getUuid() != null && c.getName() != null) {
          found.putIfAbsent(c.getUuid(), new Candidate(c.getUuid(), c.getName()));
        }
      }
    }
    return found.values();
  }
}
