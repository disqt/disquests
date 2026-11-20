package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.data.QuestGraphs;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.Messages;
import com.disqt.disquests.client.gui.helper.Toasts;
import com.disqt.disquests.client.gui.screen.PeopleText.ApplyDown;
import com.disqt.disquests.client.gui.screen.PeopleText.RemoveChoice;
import com.disqt.disquests.client.gui.tree.PeopleDraft;
import com.disqt.disquests.client.gui.tree.PeopleDraft.Person;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.graph.Plans;
import com.disqt.disquests.common.model.CollaborationRequestData;
import com.disqt.disquests.common.model.Role;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The people screen of a quest. On a server with quest trees, owners hold a draft ({@link
 * PeopleDraft}): each row has the three roles, Remove (or Undo) and "apply to subquests", an Add
 * row adds people last, and nothing is sent until Save. Join requests and "apply to subquests" act
 * at once. Anyone else sees the people and their roles without controls. On an older server it is
 * the old contributor list, where "can edit / view only" and Remove apply at once.
 */
public class ContributorScreen extends DisquestsBaseScreen {

  /** The dark panel holding the two explanation lines. */
  static final String HINTS_ID = "people-hints";

  private final Quest quest;
  private final UUID questId;
  private final UUID me;
  private final boolean trees;
  private final boolean manages;
  @Nullable private final PeopleDraft draft;
  private long seenCacheVersion;

  /**
   * The editor this screen was opened from, or null (opened from the reading view). Every way back
   * returns to that same instance, so its unsaved drafts survive; history would rebuild a fresh
   * editor.
   */
  @Nullable private final QuestScreen returnEditor;

  private FlowLayout root;
  private FlowLayout list;
  private ButtonComponent saveBtn;
  @Nullable private PlayerChooser chooser;

  public ContributorScreen(@Nullable Screen parent, Quest quest) {
    super(
        DataSource.asset(Identifier.fromNamespaceAndPath("disquests", "contributor_screen")),
        parent);
    // The saved quest as the cache has it now: the caller's copy (the editor's) can be older
    Quest saved = ClientCache.getQuestById(quest.getId());
    this.quest = saved != null ? saved : quest;
    this.questId = this.quest.getId();
    this.me = ClientSession.getEffectivePlayerUuid();
    this.trees = ClientSession.supportsTrees();
    this.manages = this.quest.isOwner(me);
    // Built once, when the screen opens; later server changes are merged in (see tick)
    this.draft = trees ? new PeopleDraft(this.quest) : null;
    this.seenCacheVersion = ClientCache.getVersion();
    this.returnEditor =
        parent instanceof QuestScreen questScreen && questScreen.isEditing() ? questScreen : null;
  }

  @Override
  public NavEntry toNavEntry() {
    return new NavEntry.QuestNav(quest.getId(), false);
  }

  @Override
  protected void build(FlowLayout root) {
    this.root = root;
    applyThemeRoot(root);
    applyThemePanel(root.childById(ParentUIComponent.class, "contributor-scroll"));
    applyThemePanel(root.childById(ParentUIComponent.class, "pending-list"));
    root.childById(LabelComponent.class, "title-label")
        .text(Component.translatable(PeopleText.TITLE));
    root.childById(LabelComponent.class, "pending-header")
        .text(Component.translatable(PeopleText.PENDING));

    buildPendingRequests();
    list = root.childById(FlowLayout.class, "contributor-list");

    if (trees) {
      buildPeople();
    } else {
      new LegacyContributorList(this, root, list, quest, manages).build();
    }

    wireBackButton(root);
  }

  // ===================== JOIN REQUESTS (act at once) =====================

  private void buildPendingRequests() {
    List<CollaborationRequestData> pendingRequests =
        ClientCache.getPendingRequestsForQuest(quest.getId());
    FlowLayout pendingSection = root.childById(FlowLayout.class, "pending-section");
    FlowLayout pendingList = root.childById(FlowLayout.class, "pending-list");

    if (pendingRequests.isEmpty()) {
      root.removeChild(pendingSection);
      return;
    }
    for (CollaborationRequestData req : pendingRequests) {
      String name = req.requesterName() != null ? req.requesterName() : "Unknown";
      final UUID requestId = req.id();
      final UUID reqQuestId = req.questId();

      FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
      row.verticalAlignment(VerticalAlignment.CENTER);
      row.gap(4);

      LabelComponent nameLabel = UIComponents.label(Component.literal(name));
      nameLabel.shadow(true);
      nameLabel.sizing(Sizing.fill(40), Sizing.content());
      row.child(nameLabel);

      // Spacer to push buttons right
      FlowLayout spacer = UIContainers.horizontalFlow(Sizing.fill(20), Sizing.fixed(1));
      row.child(spacer);

      ButtonComponent acceptBtn =
          UIComponents.button(
              Component.translatable("gui.disquests.btn.accept").withColor(0xFF55CC55),
              b -> respondToRequest(reqQuestId, requestId, true, row));
      acceptBtn.sizing(Sizing.fixed(60), Sizing.fixed(14));
      row.child(acceptBtn);

      ButtonComponent denyBtn =
          UIComponents.button(
              Component.translatable("gui.disquests.btn.deny").withColor(0xFFCC5555),
              b -> respondToRequest(reqQuestId, requestId, false, row));
      denyBtn.sizing(Sizing.fixed(60), Sizing.fixed(14));
      row.child(denyBtn);

      pendingList.child(row);
    }
  }

  /** Answers at once and drops the request's row in place, leaving the draft as it is. */
  private void respondToRequest(UUID reqQuestId, UUID requestId, boolean accept, FlowLayout row) {
    PacketSender.respondCollaboration(requestId, accept);
    ClientCache.removePendingRequest(reqQuestId, requestId);
    ClientSession.setPendingRequestCount(Math.max(0, ClientSession.getPendingRequestCount() - 1));
    root.queue(
        () -> {
          FlowLayout pendingList = root.childById(FlowLayout.class, "pending-list");
          if (pendingList == null) return;
          pendingList.removeChild(row);
          if (pendingList.children().isEmpty()) {
            root.removeChild(root.childById(FlowLayout.class, "pending-section"));
          }
          dropStaleFocus();
        });
  }

  // ===================== PEOPLE (V4 servers) =====================

  private void buildPeople() {
    root.removeChild(root.childById(LabelComponent.class, "empty-label"));
    FlowLayout hints = root.childById(FlowLayout.class, HINTS_ID);
    applyThemePanel(hints);
    LabelComponent rolesHint = root.childById(LabelComponent.class, "people-roles-hint");
    rolesHint.text(Component.translatable(PeopleText.ROLES_HINT));
    rolesHint.color(Color.ofArgb(Colors.TEXT_MUTED));
    LabelComponent saveHint = root.childById(LabelComponent.class, "people-save-hint");
    saveHint.text(Component.translatable(PeopleText.SAVE_HINT));
    saveHint.color(Color.ofArgb(Colors.TEXT_MUTED));

    if (!manages) {
      // Read-only: the people and their roles, no controls
      root.removeChild(root.childById(FlowLayout.class, "add-person-row"));
      hints.removeChild(saveHint);
      root.removeChild(root.childById(FlowLayout.class, "people-buttons"));
      refreshRows();
      return;
    }

    saveBtn = root.childById(ButtonComponent.class, "btn-save-people");
    saveBtn.setMessage(Component.translatable(PeopleText.SAVE));
    saveBtn.onPress(ignored -> save());
    ButtonComponent cancelBtn = root.childById(ButtonComponent.class, "btn-cancel-people");
    cancelBtn.setMessage(Component.translatable(PeopleText.CANCEL));
    cancelBtn.onPress(ignored -> onClose());

    chooser =
        new PlayerChooser(root, draft, this::refreshLater, this::showToast, this::dropStaleFocus);
    refreshRows();
  }

  /** Redraws the rows in the draft's fixed order, in the same list, and Save's state. */
  private void refreshRows() {
    if (list == null || draft == null) return;
    int mySubquests = manages ? Plans.mySubquests(QuestGraphs.current(), me, questId).size() : 0;
    List<Person> rows = draft.rows();
    // One layout pass for the whole refill; the scroll container and its offset stay
    list.<FlowLayout>configure(
        l -> {
          l.clearChildren();
          for (Person p : rows) l.child(row(p, mySubquests));
        });
    if (saveBtn != null) {
      saveBtn.active(PeopleText.saveStep(draft, me) != PeopleText.SaveStep.NOTHING);
    }
    dropStaleFocus();
  }

  private void refreshLater() {
    root.queue(this::refreshRows);
  }

  private FlowLayout row(Person p, int mySubquests) {
    String id = p.uuid().toString();
    FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
    row.id("person-row-" + id);
    row.verticalAlignment(VerticalAlignment.CENTER);
    row.gap(4);

    MutableComponent name =
        p.uuid().equals(me)
            ? Component.translatable(PeopleText.YOU, nameOf(p))
            : Component.literal(nameOf(p));
    if (p.removed()) name.withStyle(style -> style.withStrikethrough(true));
    LabelComponent nameLabel = UIComponents.label(name);
    nameLabel.shadow(true);
    if (p.removed()) nameLabel.color(Color.ofArgb(Colors.TEXT_DISABLED));
    nameLabel.horizontalSizing(Sizing.expand(100));
    row.child(nameLabel);

    if (!manages) {
      row.child(muted(Messages.toComponent(QuestPage.role(p.role()))));
      return row;
    }

    if (p.removed()) {
      LabelComponent removed = muted(Component.translatable(PeopleText.REMOVED));
      removed.id("person-status-" + id);
      row.child(removed);
      row.child(button("btn-undo-" + id, Component.translatable(PeopleText.UNDO), () -> undo(p)));
    } else {
      for (Role role : Role.values()) {
        ButtonComponent roleBtn =
            button(
                "role-" + role.name().toLowerCase(Locale.ROOT) + "-" + id,
                Messages.toComponent(QuestPage.role(role)),
                () -> {
                  draft.setRole(p.uuid(), role);
                  refreshLater();
                });
        // The current role shows pressed, as the visibility buttons do
        roleBtn.active(p.role() != role);
        row.child(roleBtn);
      }
      if (p.dirty()) {
        LabelComponent notSaved =
            muted(
                Component.translatable(
                    p.droppedMeanwhile() ? PeopleText.DROPPED_MEANWHILE : PeopleText.NOT_SAVED));
        notSaved.id("person-status-" + id);
        row.child(notSaved);
      }
      row.child(
          button("btn-remove-" + id, Component.translatable(PeopleText.REMOVE), () -> remove(p)));
    }

    ApplyDown applyDown = PeopleText.applyDown(p, me, mySubquests);
    if (applyDown != ApplyDown.HIDDEN) {
      ButtonComponent apply =
          button(
              "btn-apply-down-" + id,
              Component.translatable(PeopleText.APPLY_DOWN),
              () -> confirmApplyDown(p, mySubquests));
      if (applyDown == ApplyDown.SAVE_FIRST) {
        apply.active(false);
        apply.tooltip(Component.translatable(PeopleText.SAVE_FIRST));
      }
      row.child(apply);
    }
    return row;
  }

  private static String nameOf(Person p) {
    return p.name() != null ? p.name() : "?";
  }

  static ButtonComponent button(String id, Component text, Runnable action) {
    ButtonComponent button = UIComponents.button(text, ignored -> action.run());
    button.id(id);
    button.sizing(Sizing.content(), Sizing.fixed(14));
    return button;
  }

  private static LabelComponent muted(Component text) {
    LabelComponent label = UIComponents.label(text);
    label.color(Color.ofArgb(Colors.TEXT_MUTED));
    label.shadow(true);
    return label;
  }

  /**
   * Removes in the draft; asks "this quest only / and my N subquests" when the person holds a role
   * on subquests the player owns.
   */
  private void remove(Person p) {
    int onMine =
        draft.isNew(p.uuid()) || p.uuid().equals(me)
            ? 0
            : PeopleText.subquestsWithRole(QuestGraphs.current(), me, questId, p.uuid());
    RemoveChoice choice = PeopleText.removeChoice(draft, me, p.uuid(), nameOf(p), onMine);
    if (choice == null) {
      draft.remove(p.uuid(), false);
      refreshLater();
      return;
    }
    showChoiceOverlay(
        Messages.toComponent(choice.question()),
        null,
        List.of(
            new Choice(
                "btn-remove-this-only",
                Messages.toComponent(choice.thisOnly()),
                () -> {
                  draft.remove(p.uuid(), false);
                  refreshLater();
                }),
            new Choice(
                "btn-remove-with-subquests",
                Messages.toComponent(choice.withMine()),
                () -> {
                  draft.remove(p.uuid(), true);
                  refreshLater();
                }),
            new Choice(CONFIRM_NO_ID, Component.translatable(PeopleText.CANCEL), () -> {})));
  }

  private void undo(Person p) {
    draft.undoRemove(p.uuid());
    refreshLater();
  }

  /** Gives the person their saved role on the player's subquests, at once, after asking. */
  private void confirmApplyDown(Person p, int mySubquests) {
    Quest current = ClientCache.getQuestById(questId);
    String title = (current != null ? current : quest).getTitle();
    if (title == null) title = "";
    String question =
        Messages.toComponent(PeopleText.applyDownQuestion(nameOf(p), p.role(), mySubquests, title))
            .getString();
    String extra = Component.translatable(PeopleText.APPLY_DOWN_EXTRA).getString();
    showConfirmOverlay(
        question,
        extra,
        null,
        ticked -> {
          if (archivedSinceOpened()) return;
          PacketSender.applyRoleDown(questId, p.uuid());
          showToast(
              Component.translatable(
                  PeopleText.APPLY_DOWN_SENT,
                  nameOf(p),
                  Messages.toComponent(QuestPage.role(p.role()))));
        });
  }

  // --- Save, Cancel, leaving ---

  private void save() {
    if (draft == null || archivedSinceOpened()) return;
    switch (PeopleText.saveStep(draft, me)) {
      case NOTHING -> {}
      case NO_OWNER_LEFT -> showRefusedToast(Component.translatable(PeopleText.NEED_OWNER));
      case ASK_GIVING_UP_OWNER ->
          showChoiceOverlay(
              Component.translatable(PeopleText.GIVE_UP_OWNER),
              null,
              List.of(
                  new Choice(CONFIRM_YES_ID, Component.translatable(PeopleText.SAVE), this::send),
                  new Choice(CONFIRM_NO_ID, Component.translatable(PeopleText.CANCEL), () -> {})));
      case SEND -> send();
    }
  }

  /** Sends the draft and goes back where the screen was opened from. */
  private void send() {
    if (draft == null || archivedSinceOpened()) return;
    PacketSender.updatePeople(questId, draft.ops(me));
    leave();
  }

  /** Back, forward, Escape and Cancel: a draft with changes asks before it is dropped. */
  @Override
  protected void confirmLeave(Runnable leave) {
    if (draft == null || !manages || draft.ops(me).isEmpty()) {
      leave.run();
      return;
    }
    showConfirmOverlay(Component.translatable("gui.disquests.confirm.discard"), leave);
  }

  @Override
  public void onClose() {
    confirmLeave(this::leave);
  }

  /** Mouse back: to the editor this screen was opened from, when there is one. */
  @Override
  protected void goBack() {
    if (returnEditor != null) {
      goBackTo(returnEditor);
    } else {
      super.goBack();
    }
  }

  /**
   * Back where the screen was opened from, without asking: the same editor instance (using up the
   * history entry it pushed, as mouse back does), else the parent.
   */
  private void leave() {
    if (returnEditor != null) {
      goBackTo(returnEditor);
    } else {
      super.onClose();
    }
  }

  @Override
  public boolean keyPressed(KeyEvent keyInput) {
    // Arrows, Enter, Tab and Escape go to the name suggestions while they are open
    if (chooser != null && chooser.onKey(keyInput)) return true;
    return super.keyPressed(keyInput);
  }

  /**
   * Follows the cache while the screen is open. When the quest is gone, or the player managed it
   * and is no longer an owner, the screen closes without asking (its changes could not be saved),
   * the latter with a toast. Otherwise changes the server made meanwhile (a join request accepted,
   * another owner's change) are merged into the draft so nothing the player changed is lost; that
   * waits while a dialog is open. A quest completed meanwhile is left alone: Save shows it
   * archived.
   */
  @Override
  public void tick() {
    super.tick();
    if (root == null) return;
    long version = ClientCache.getVersion();
    if (version == seenCacheVersion) return;
    Quest cached = ClientCache.getQuestById(questId);
    switch (PeopleText.openState(cached, me, manages)) {
      case CLOSE_GONE -> {
        leave();
        return;
      }
      case CLOSE_NO_LONGER_OWNER -> {
        showToast(Component.translatable(PeopleText.NO_LONGER_OWNER));
        leave();
        return;
      }
      case STAY -> {}
    }
    if (draft == null) {
      seenCacheVersion = version;
      return;
    }
    if (root.childById(OverlayContainer.class, CONFIRM_OVERLAY_ID) != null) return;
    seenCacheVersion = version;
    if (cached.isCompleted()) return;
    draft.merge(cached);
    refreshRows();
  }

  void showToast(Component text) {
    Toasts.show(text);
  }

  /**
   * Clears the focus when it sits on a component a refresh just removed (a pressed role or remove
   * button), so Enter or Space cannot press it again.
   */
  void dropStaleFocus() {
    if (root == null || root.focusHandler() == null) return;
    UIComponent focused = root.focusHandler().focused();
    if (focused == null) return;
    UIComponent top = focused;
    while (top.parent() != null) top = top.parent();
    if (top != root) root.focusHandler().focus(null, UIComponent.FocusSource.MOUSE_CLICK);
  }

  /** True (and bounces to the archived quest) when the quest was completed meanwhile. */
  boolean archivedSinceOpened() {
    Quest archived = archivedMeanwhile(quest.getId());
    if (archived == null) return false;
    // Skip the stale quest screen this one was opened from.
    Screen viewParent =
        this.parent instanceof QuestScreen questScreen
            ? questScreen.getParentScreen()
            : this.parent;
    showArchivedInstead(viewParent, archived);
    return true;
  }
}
