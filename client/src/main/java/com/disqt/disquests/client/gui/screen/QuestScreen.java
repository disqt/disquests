package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.NoticeText;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.data.QuestGraphs;
import com.disqt.disquests.client.gui.component.MinHeightFlow;
import com.disqt.disquests.client.gui.component.MinHeightScroll;
import com.disqt.disquests.client.gui.component.QuestSearchField;
import com.disqt.disquests.client.gui.component.SuggestionDropdown;
import com.disqt.disquests.client.gui.component.TagChipComponent;
import com.disqt.disquests.client.gui.component.TextFieldComponent;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.Messages;
import com.disqt.disquests.client.gui.tree.QuestEditDraft;
import com.disqt.disquests.client.gui.widget.MarkdownWidget;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import com.disqt.disquests.client.markdown.RenderedLine;
import com.disqt.disquests.client.markdown.WikiLinks;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.DimensionKeys;
import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.TagConstraints;
import com.disqt.disquests.common.graph.Plans;
import com.disqt.disquests.common.graph.Refusal;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.LinkChanges;
import com.disqt.disquests.common.model.Notice;
import com.disqt.disquests.common.model.PointData;
import com.disqt.disquests.common.model.Visibility;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.CollapsibleContainer;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Unified quest screen with view and edit modes, using owo-ui. Switches between two XML models by
 * re-opening with different state.
 */
public class QuestScreen extends DisquestsBaseScreen {

  /** The [[ autocomplete lists at most this many titles. */
  private static final int WIKI_LINK_SUGGESTIONS = 5;

  private final Quest quest;
  private final boolean editing;

  /** The cache version the people button's label was last set from. */
  private long contributorsLabelVersion = -1;

  private final boolean isNewQuest;

  // Edit mode state preserved across rebuilds
  @Nullable private EditorOriginals originals;
  private LocationCard.LocationSnapshot originalLocation;

  /**
   * The editor's tree, visibility, prerequisites and auto-join changes; carried across the editor's
   * own rebuilds (map editor, tag picker) like the originals above.
   */
  @Nullable private QuestEditDraft editDraft;

  @Nullable private QuestEditTreePane editPane;
  @Nullable private QuestLinksForm linksForm;

  // Permission cache
  private boolean canEdit;
  private boolean isOwner;
  private boolean hideContent;
  private QuestPage.Actions actions;

  /** The content scroll never shrinks below this, even when the sections above it are tall. */
  private static final int MIN_CONTENT_HEIGHT = 40;

  /** The editor's text area never shrinks below this. */
  private static final int MIN_EDITOR_HEIGHT = 48;

  // What the reading view draws, to rebuild it when that changes (see tick)
  @Nullable private QuestPage.PageSignature shownPage;
  private long seenCacheVersion;
  @Nullable private QuestTreePane treePane;
  @Nullable private MinHeightScroll<FlowLayout> contentScroll;

  /**
   * Scroll positions carried from the page this one replaced after a change to what it shows (null
   * for an ordinary opening); {@code pane} is null when that page had no tree pane.
   */
  private record ReaderPlace(double content, @Nullable Double pane) {}

  @Nullable private ReaderPlace carriedPlace;

  // Widget references for edit mode
  private TextFieldComponent titleFieldComponent;
  private TextFieldComponent contentFieldComponent;
  private List<LocationCard.VertexRow> vertexRows = List.of();

  /** Open in view mode for an existing quest. */
  public QuestScreen(@Nullable Screen parent, Quest quest) {
    this(parent, quest, false, false, null, null, null);
  }

  /** The editor of a quest the server has not stored yet (Create). */
  public static QuestScreen forNewQuest(@Nullable Screen parent, Quest quest) {
    return new QuestScreen(parent, quest, true, true, null, null, null);
  }

  /** The editor of an existing quest, for instance rebuilt from the history. */
  public static QuestScreen editorFor(@Nullable Screen parent, Quest quest) {
    return new QuestScreen(parent, quest, true, false, null, null, null);
  }

  /** Internal constructor preserving edit state across mode switches. */
  private QuestScreen(
      @Nullable Screen parent,
      Quest quest,
      boolean editing,
      boolean isNewQuest,
      @Nullable EditorOriginals originals,
      @Nullable LocationCard.LocationSnapshot originalLocation,
      @Nullable QuestEditDraft editDraft) {
    super(
        DataSource.asset(
            Identifier.fromNamespaceAndPath(
                "disquests", editing ? "quest_screen_edit" : "quest_screen_view")),
        parent);
    this.quest = quest;
    this.editing = editing;
    this.isNewQuest = isNewQuest;
    this.originals = originals;
    this.originalLocation = originalLocation;
    this.editDraft = editDraft;
  }

  // --- Accessors ---

  public Quest getQuest() {
    return quest;
  }

  public boolean isEditing() {
    return editing;
  }

  @Override
  public NavEntry toNavEntry() {
    return new NavEntry.QuestNav(quest.getId(), editing);
  }

  public boolean hasLeaveButton() {
    return !editing && actions != null && actions.leave();
  }

  @Override
  protected void build(FlowLayout root) {
    applyThemeRoot(root);
    if (!editing) installContentScroll(root);

    // View mode panels
    ParentUIComponent contentScroll = root.childById(ParentUIComponent.class, "content-scroll");
    if (contentScroll != null) applyThemePanel(contentScroll);
    ParentUIComponent locationCard = root.childById(ParentUIComponent.class, "location-card");
    if (locationCard != null) applyThemePanel(locationCard);

    // Edit mode panels
    ParentUIComponent titleRow = root.childById(ParentUIComponent.class, "title-row");
    if (titleRow != null) applyThemePanel(titleRow);
    ParentUIComponent editorPanel = root.childById(ParentUIComponent.class, "editor-panel");
    if (editorPanel != null) applyThemePanel(editorPanel);
    ParentUIComponent formattingPanel = root.childById(ParentUIComponent.class, "formatting-panel");
    if (formattingPanel != null) applyThemePanel(formattingPanel);
    ParentUIComponent linksPanel = root.childById(ParentUIComponent.class, QuestLinksForm.ID);
    if (linksPanel != null) applyThemePanel(linksPanel);

    UUID myUuid = ClientSession.getEffectivePlayerUuid();
    this.isOwner = quest.isOwner(myUuid);
    this.canEdit = quest.canEdit(myUuid);
    this.hideContent = quest.isContentHidden(myUuid);
    this.actions =
        QuestPage.actions(
            quest.toData(),
            myUuid,
            ClientSession.supportsCompletion(),
            ClientSession.supportsTrees());
    // Every way of opening a quest builds this screen: the quest is no longer new.
    if (!isNewQuest) ClientSession.newMarks().opened(quest.getId());

    if (editing) {
      buildEditMode(root);
    } else {
      buildViewMode(root);
    }
  }

  // ===================== VIEW MODE =====================

  private void buildViewMode(FlowLayout root) {
    FlowLayout column = root.childById(FlowLayout.class, "quest-column");
    this.treePane = QuestTreePane.attach(this, root, quest.getId());

    // Title
    root.childById(LabelComponent.class, "title-label")
        .text(Component.literal(quest.getTitle() != null ? quest.getTitle() : "Untitled"));
    buildHeaderInfo(root);

    if (quest.isLocked()) {
      LockedQuestView.build(this, root, quest);
    } else {
      buildQuestBody(root, column);
    }
    QuestBanners.attach(root, quest, actions);
    buildButtons(root, column);
    wireBackButton(root);
    rememberShownPage();
  }

  /**
   * The content scroll ({@code content-scroll}, holding {@code content-area}) takes the height the
   * header, banners, sections, location card, people and buttons leave, down to a minimum, so the
   * button row stays on screen.
   */
  private void installContentScroll(FlowLayout root) {
    FlowLayout area = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
    area.id("content-area");
    area.gap(2);
    MinHeightScroll<FlowLayout> scroll =
        new MinHeightScroll<>(Sizing.fill(85), Sizing.expand(100), area, MIN_CONTENT_HEIGHT);
    scroll.id("content-scroll");
    scroll.surface(Surface.DARK_PANEL);
    scroll.padding(Insets.of(4));
    replaceSlot(root.childById(FlowLayout.class, "quest-column"), "content-scroll-slot", scroll);
    this.contentScroll = scroll;
  }

  /** Records what the page now draws; later cache changes are compared against it. */
  void rememberShownPage() {
    this.shownPage = pageSignature(quest);
    this.seenCacheVersion = ClientCache.getVersion();
  }

  private static QuestPage.PageSignature pageSignature(Quest shown) {
    return QuestPage.signature(
        shown.toData(),
        QuestGraphs.current(),
        ClientSession.getEffectivePlayerUuid(),
        ClientSession.getExpandedQuests(),
        ClientSession.supportsTrees());
  }

  /**
   * "by owner", the visibility badge (the quest's own setting) and, on a server with quest trees,
   * the player's role. A locked view has no owner name: only the badge and the role.
   */
  private void buildHeaderInfo(FlowLayout root) {
    boolean hasOwnerName = quest.getOwnerName() != null && !quest.getOwnerName().isBlank();
    MutableComponent ownerText =
        Component.literal(hasOwnerName ? "by " + quest.getOwnerName() : "")
            .withColor(Colors.TEXT_MUTED);
    if (quest.getVisibility() != null) {
      int visColor =
          switch (quest.getVisibility()) {
            case PRIVATE -> 0xFF5555; // red
            case CLOSED -> 0xFFAA00; // amber
            case OPEN -> 0x55FF55; // green
          };
      ownerText.append(
          Component.literal("  [")
              .append(Component.translatable(EditorText.visibility(quest.getVisibility())))
              .append("]")
              .withColor(visColor));
    }
    LabelComponent ownerLabel = UIComponents.label(ownerText);
    ownerLabel.shadow(true);
    ownerLabel.sizing(Sizing.content(), Sizing.content());
    FlowLayout headerRow = root.childById(FlowLayout.class, "header-row");
    headerRow.child(ownerLabel);

    // Roles come with quest trees: an older server's page shows none, as in 0.8.0
    if (!ClientSession.supportsTrees()) return;
    UUID me = ClientSession.getEffectivePlayerUuid();
    LabelComponent roleLabel =
        UIComponents.label(Messages.toComponent(QuestPage.role(quest.roleOf(me))));
    roleLabel.id("role-label");
    roleLabel.color(Color.ofArgb(Colors.TEXT_MUTED));
    roleLabel.shadow(true);
    roleLabel.margins(Insets.left(6));
    headerRow.child(roleLabel);
  }

  /** Tags, content, location, prerequisites and people: everything a locked page leaves out. */
  private void buildQuestBody(FlowLayout root, FlowLayout column) {
    // Tag display -- inline chips (no collapsible)
    FlowLayout tagDisplay = UIContainers.ltrTextFlow(Sizing.fill(85), Sizing.content());
    tagDisplay.id("tag-display");
    tagDisplay.gap(4);
    tagDisplay.margins(Insets.bottom(4));
    for (String tag : quest.getTags()) {
      tagDisplay.child(new TagChipComponent(tag));
    }
    replaceSlot(column, "tag-display-slot", tagDisplay);

    // Content area -- add MarkdownWidget
    String contentToRender =
        hideContent
            ? "Request access to view this quest"
            : Objects.requireNonNullElse(quest.getContent(), "");
    List<RenderedLine> rendered = MarkdownRenderer.render(contentToRender);
    MarkdownWidget markdownWidget = new MarkdownWidget(rendered);
    markdownWidget.sizing(Sizing.fill(100), Sizing.fill(100));
    markdownWidget.id("content-markdown");

    if (!hideContent) {
      markdownWidget.setCheckboxToggleListener(
          (checkboxIndex, nowChecked) -> {
            // Archived quests are read-only (the server refuses saves while completed).
            if (!canEdit || quest.isCompleted()) return;
            String content = quest.getContent();
            if (content == null) return;
            String updated = toggleCheckbox(content, checkboxIndex, nowChecked);
            quest.setContent(updated);
            List<RenderedLine> rerendered = MarkdownRenderer.render(updated);
            markdownWidget.setContent(rerendered);
            PacketSender.saveQuest(
                quest.getId(),
                quest.getTitle(),
                updated,
                quest.getCoordinates(),
                quest.isRegion(),
                quest.getCoordinates2(),
                quest.getMap(),
                quest.getTags(),
                quest.getPolygon());
            ClientCache.addOrUpdateMyQuest(quest);
            // The page already shows the toggle: do not rebuild it for this change.
            rememberShownPage();
          });
    }

    FlowLayout contentArea = root.childById(FlowLayout.class, "content-area");
    contentArea.child(markdownWidget);

    // Location card (mini map, coords, map, BlueMap button)
    LocationCard.buildView(this, root, quest);

    // Prerequisites (Write and Owner), before the people
    if (actions.prerequisites()) {
      UIComponent slot = column.childById(UIComponent.class, "contributors-row-slot");
      column.child(column.children().indexOf(slot), PrerequisiteLinks.section(this, quest));
    }

    // Everyone holding a role, owners first -- wrapped in collapsible
    List<ContributorData> people = QuestPage.people(quest.toData());
    int count = people.size();
    CollapsibleContainer contributorsCollapse =
        UIContainers.collapsible(
            Sizing.fill(85),
            Sizing.content(),
            Component.translatable("gui.disquests.section.contributors", count),
            count > 0 && count <= 3);
    contributorsCollapse.id("contributors-collapse");
    contributorsCollapse.margins(Insets.top(2));

    if (count > 0) {
      MutableComponent names = Component.empty();
      for (int i = 0; i < people.size(); i++) {
        ContributorData person = people.get(i);
        if (i > 0) names.append(Component.literal(", "));
        names.append(Component.literal(person.name() != null ? person.name() : "?"));
        names.append(Component.literal(" ("));
        names.append(Messages.toComponent(QuestPage.role(person.role())));
        names.append(Component.literal(")"));
      }
      LabelComponent contribLabel = UIComponents.label(names);
      contribLabel.id("contributors-label");
      contribLabel.color(Color.ofArgb(Colors.TEXT_MUTED));
      contribLabel.horizontalSizing(Sizing.fill(100));
      contribLabel.shadow(true);
      FlowLayout contributorsRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
      contributorsRow.id("contributors-row");
      contributorsRow.padding(Insets.left(4));
      contributorsRow.child(contribLabel);
      contributorsCollapse.child(contributorsRow);
    }

    replaceSlot(column, "contributors-row-slot", contributorsCollapse);
  }

  /** The button row, from {@link QuestPage#actions}; ids are kept for the E2E journeys. */
  private void buildButtons(FlowLayout root, FlowLayout column) {
    FlowLayout buttonRow = root.childById(FlowLayout.class, "button-row");

    if (quest.isCompleted() && !quest.isLocked()) {
      LabelComponent completedLabel = UIComponents.label(completedText());
      completedLabel.id("completed-label");
      completedLabel.color(Color.ofArgb(Colors.TEXT_MUTED));
      completedLabel.shadow(true);
      completedLabel.margins(Insets.top(4));
      column.child(column.children().indexOf(buttonRow), completedLabel);
    }

    // Interact button: Join (OPEN), Request (CLOSED); absent otherwise
    ButtonComponent interactBtn = root.childById(ButtonComponent.class, "btn-interact");
    switch (QuestPage.interact(actions, quest.getVisibility())) {
      case JOIN -> {
        interactBtn.setMessage(Component.translatable("gui.disquests.btn.join"));
        interactBtn.onPress(ignored -> joinQuest());
      }
      case REQUEST -> {
        if (ClientSession.isRequested(quest.getId())) {
          interactBtn.setMessage(Component.translatable("gui.disquests.btn.requested"));
          interactBtn.active(false);
          interactBtn.tooltip(Component.translatable("gui.disquests.tooltip.already_requested"));
        } else {
          interactBtn.setMessage(Component.translatable("gui.disquests.btn.request"));
          interactBtn.onPress(ignored -> requestAccess());
        }
      }
      // A member, an archived or locked quest, or a Private quest seen without a role
      case NONE -> buttonRow.removeChild(interactBtn);
    }

    wire(buttonRow, "btn-edit", actions.edit(), this::enterEditMode);
    wire(buttonRow, "btn-leave", actions.leave(), this::leaveQuest);
    // Complete and Reopen texts come from lang keys
    ButtonComponent completeBtn =
        wire(buttonRow, "btn-complete", actions.complete(), this::confirmComplete);
    if (completeBtn != null) {
      completeBtn.setMessage(Component.translatable("gui.disquests.btn.complete"));
    }
    ButtonComponent reopenBtn =
        wire(buttonRow, "btn-reopen", actions.reopen(), this::confirmReopen);
    if (reopenBtn != null) {
      reopenBtn.setMessage(Component.translatable("gui.disquests.btn.reopen"));
    }

    // The people screen, for owners, before Delete
    if (actions.managePeople()) {
      ButtonComponent manage =
          UIComponents.button(
              Component.translatable(QuestPage.MANAGE_PEOPLE), ignored -> openContributors());
      manage.id("btn-manage-people");
      manage.sizing(Sizing.content(), Sizing.fixed(20));
      ButtonComponent deleteBtn = buttonRow.childById(ButtonComponent.class, "btn-delete");
      buttonRow.child(buttonRow.children().indexOf(deleteBtn), manage);
    }
    wire(buttonRow, "btn-delete", actions.delete(), this::confirmDelete);
  }

  /**
   * Wires the button to {@code action} and returns it, or removes it and returns null when it does
   * not apply.
   */
  @Nullable
  private static ButtonComponent wire(
      FlowLayout buttonRow, String id, boolean shown, Runnable action) {
    ButtonComponent button = buttonRow.childById(ButtonComponent.class, id);
    if (shown) {
      button.onPress(ignored -> action.run());
      return button;
    }
    buttonRow.removeChild(button);
    return null;
  }

  /** Opens another quest from this page (tree pane, prerequisite links); back returns here. */
  void openOther(UUID id) {
    Quest other = ClientCache.getQuestById(id);
    if (other != null) navigateToScreen(new QuestScreen(this.parent, other));
  }

  // ===================== EDIT MODE =====================

  private void buildEditMode(FlowLayout root) {
    String editableContent =
        quest.getContent() != null
            ? MarkdownRenderer.reverseResolveWikiLinks(quest.getContent())
            : "";

    // Snapshot originals for dirty tracking
    if (originals == null) {
      originals = EditorOriginals.of(quest, ClientCache::getQuestById);
    }
    if (originalLocation == null) {
      originalLocation = LocationCard.LocationSnapshot.of(quest);
    }
    if (editDraft == null) {
      editDraft = newEditDraft();
    }
    installContentRow(root);

    // Title field
    FlowLayout titleRow = root.childById(FlowLayout.class, "title-row");
    MultiLineTextFieldWidget titleField =
        new MultiLineTextFieldWidget(
            minecraft.font,
            0,
            0,
            300,
            16,
            quest.getTitle(),
            Component.translatable("gui.disquests.placeholder.title").getString(),
            1,
            false);
    titleFieldComponent = new TextFieldComponent(titleField);
    titleFieldComponent.sizing(Sizing.fill(90), Sizing.fixed(16));
    titleFieldComponent.id("title-field");
    titleRow.child(titleFieldComponent);

    // Tag editor (only if player can edit)
    if (canEdit) {
      buildTagEditor(root);
    }

    // Content editor
    FlowLayout editorPanel = root.childById(FlowLayout.class, "editor-panel");
    MultiLineTextFieldWidget contentField =
        new MultiLineTextFieldWidget(
            minecraft.font,
            0,
            0,
            400,
            200,
            editableContent,
            Component.translatable("gui.disquests.placeholder.content").getString(),
            Integer.MAX_VALUE,
            true,
            true);
    contentFieldComponent = new TextFieldComponent(contentField);
    contentFieldComponent.sizing(Sizing.fill(100), Sizing.fill(100));
    contentFieldComponent.id("content-field");
    editorPanel.child(contentFieldComponent);
    titleFieldComponent.tabTarget(contentFieldComponent);

    // Attach autocomplete dropdown for wiki-link syntax ([[Quest Name]])
    SuggestionDropdown<String> autocomplete =
        new SuggestionDropdown<String>(
            root,
            "autocomplete-overlay",
            title -> new SuggestionDropdown.Entry(title, null, true, null),
            title -> {
              // Replace the partial query after [[ with the full title + ]]
              String current = contentFieldComponent.getText();
              int cursorPos = contentFieldComponent.getDelegate().getCursorAbsolute();
              int openBracket = current.lastIndexOf("[[", cursorPos - 1);
              if (openBracket >= 0) {
                String before = current.substring(0, openBracket + 2);
                String after = current.substring(cursorPos);
                contentFieldComponent.getDelegate().setText(before + title + "]]" + after);
              }
            });
    // The text the list was last shown for: caret moves and Escape leave the list as it is
    String[] shownFor = {null};
    contentFieldComponent.setAutocomplete(
        autocomplete,
        () -> {
          String text = contentFieldComponent.getText();
          String query = contentFieldComponent.wikiLinkQuery();
          if (query == null) {
            autocomplete.hide();
            shownFor[0] = text;
            return;
          }
          if (text.equals(shownFor[0])) return;
          shownFor[0] = text;
          autocomplete.show(
              wikiLinkTitles(query),
              contentFieldComponent.cursorScreenX(),
              contentFieldComponent.cursorScreenY(),
              contentFieldComponent.getDelegate().getLineHeight(),
              contentFieldComponent);
        });

    // Location card
    vertexRows = LocationCard.buildEdit(this, root, quest);

    // Tree pane (Write and Owner, servers with quest trees) and link controls (owners)
    if (ClientSession.supportsTrees()) {
      editPane =
          QuestEditTreePane.attach(
              this,
              root,
              editDraft,
              isNewQuest,
              () -> titleFieldComponent != null ? titleFieldComponent.getText() : quest.getTitle(),
              () -> {
                if (linksForm != null) linksForm.refresh();
              });
    }
    // An older server keeps 0.8.0's visibility button in the settings row instead of the form
    boolean trees = ClientSession.supportsTrees();
    linksForm = QuestLinksForm.attach(this, root, editDraft, isOwner && trees, this::treeChanged);

    // People (owner only)
    FlowLayout settingsRow = root.childById(FlowLayout.class, "settings-row");
    if (isOwner && !trees) addLegacyVisibilityButton(settingsRow);
    if (isOwner) {
      ButtonComponent contribBtn = root.childById(ButtonComponent.class, "btn-contributors");
      contribBtn.onPress(ignored -> openContributors());
      refreshContributorsButton(root);
    } else {
      settingsRow.sizing(Sizing.fixed(0), Sizing.fixed(0));
    }

    // Bottom buttons
    root.childById(ButtonComponent.class, "btn-save").onPress(ignored -> saveAndView());
    root.childById(ButtonComponent.class, "btn-cancel").onPress(ignored -> cancelEdit());

    // Style formatting panel labels with rendered examples
    LabelComponent fmtBold = root.childById(LabelComponent.class, "fmt-bold");
    if (fmtBold != null)
      fmtBold.text(
          Component.literal("**text**: ")
              .append(Component.literal("text").withStyle(s -> s.withBold(true))));
    LabelComponent fmtItalic = root.childById(LabelComponent.class, "fmt-italic");
    if (fmtItalic != null)
      fmtItalic.text(
          Component.literal("*text*: ")
              .append(Component.literal("text").withStyle(s -> s.withItalic(true))));
    LabelComponent fmtStrike = root.childById(LabelComponent.class, "fmt-strike");
    if (fmtStrike != null)
      fmtStrike.text(
          Component.literal("~~text~~: ")
              .append(Component.literal("text").withStyle(s -> s.withStrikethrough(true))));
    LabelComponent fmtHeading = root.childById(LabelComponent.class, "fmt-heading");
    if (fmtHeading != null)
      fmtHeading.text(
          Component.literal("# ")
              .append(Component.literal("Heading").withStyle(s -> s.withBold(true))));
    LabelComponent fmtCheckbox = root.childById(LabelComponent.class, "fmt-checkbox");
    if (fmtCheckbox != null)
      fmtCheckbox.text(
          Component.literal("- [ ] todo  - [x] ")
              .append(
                  Component.literal("done")
                      .withStyle(s -> s.withStrikethrough(true).withColor(0x55FF55))));
    LabelComponent fmtQuote = root.childById(LabelComponent.class, "fmt-quote");
    if (fmtQuote != null)
      fmtQuote.text(
          Component.literal("> ")
              .append(
                  Component.literal("quote")
                      .withStyle(s -> s.withItalic(true).withColor(0xAAAAAA))));
    LabelComponent fmtLink = root.childById(LabelComponent.class, "fmt-link");
    if (fmtLink != null)
      fmtLink.text(
          Component.literal("[text](url): ")
              .append(
                  Component.literal("link")
                      .withStyle(s -> s.withUnderlined(true).withColor(0x5555FF))));
    LabelComponent fmtWikiLink = root.childById(LabelComponent.class, "fmt-wikilink");
    if (fmtWikiLink != null)
      fmtWikiLink.text(
          Component.literal("[[Quest Name]]: ")
              .append(
                  Component.literal("quest link")
                      .withStyle(s -> s.withUnderlined(true).withColor(0xe8a86d))));
  }

  /**
   * 0.8.0's visibility control, for a server without quest trees: {@code btn-visibility} reads
   * "Visibility: Private" and each press moves to the next setting (Private, Closed, Open). It
   * changes the draft only; Save sends the change, as 0.8.0 did.
   */
  private void addLegacyVisibilityButton(FlowLayout settingsRow) {
    Visibility shown = editDraft.visibility();
    ButtonComponent visBtn =
        UIComponents.button(
            Component.translatable(EditorText.VISIBILITY)
                .append(": ")
                .append(Component.translatable(EditorText.visibility(shown))),
            ignored -> {
              editDraft.visibility(
                  switch (editDraft.visibility()) {
                    case PRIVATE -> Visibility.CLOSED;
                    case CLOSED -> Visibility.OPEN;
                    case OPEN -> Visibility.PRIVATE;
                  });
              persistFieldValues();
              rebuildEditMode();
            });
    visBtn.id("btn-visibility");
    visBtn.tooltip(Component.translatable(EditorText.visibilityHint(shown)));
    visBtn.sizing(Sizing.content(), Sizing.fixed(16));
    settingsRow.child(0, visBtn);
  }

  /**
   * {@code content-row} (the text editor beside the formatting help) takes the height the rest of
   * the form leaves, down to a minimum, so the form fits and Save and Cancel stay on screen.
   */
  private static void installContentRow(FlowLayout root) {
    FlowLayout column = root.childById(FlowLayout.class, "quest-column");
    FlowLayout fromXml = root.childById(FlowLayout.class, "content-row");
    MinHeightFlow row = new MinHeightFlow(Sizing.fill(85), Sizing.expand(100), MIN_EDITOR_HEIGHT);
    row.id("content-row");
    row.gap(4);
    row.margins(Insets.top(2));
    List<UIComponent> panels = List.copyOf(fromXml.children());
    fromXml.clearChildren();
    for (UIComponent panel : panels) row.child(panel);
    int index = column.children().indexOf(fromXml);
    column.removeChild(fromXml);
    column.child(index, row);
  }

  private void buildTagEditor(FlowLayout root) {
    FlowLayout tagEditor = root.childById(FlowLayout.class, "tag-editor");
    if (tagEditor == null) return;

    List<String> tags = quest.getTags();
    for (int i = 0; i < tags.size(); i++) {
      final int idx = i;
      final String tag = tags.get(idx);

      TagChipComponent chip =
          new TagChipComponent(tag, true)
              .onRemove(
                  t -> {
                    persistFieldValues();
                    quest.getTags().remove(idx);
                    rebuildEditMode();
                  });
      chip.id("tag-chip-" + idx);
      tagEditor.child(chip);
    }

    // "+ Tag" button (only if below max)
    if (tags.size() < TagConstraints.MAX_TAGS) {
      ButtonComponent addTagBtn =
          UIComponents.button(
              Component.translatable("gui.disquests.btn.add_tag"),
              b -> {
                persistFieldValues();
                QuestScreen returnScreen =
                    new QuestScreen(
                        this.parent,
                        quest,
                        true,
                        isNewQuest,
                        originals,
                        originalLocation,
                        editDraft);
                navigateToScreen(new TagPickerScreen(returnScreen, quest, returnScreen));
              });
      addTagBtn.sizing(Sizing.content(), Sizing.fixed(12));
      addTagBtn.id("btn-add-tag");
      tagEditor.child(addTagBtn);
    }
  }

  // ===================== MODE SWITCHING =====================

  private void enterEditMode() {
    if (!canEdit) return;
    persistFieldValues();
    navigateToScreen(
        new QuestScreen(
            this.parent,
            quest,
            true,
            isNewQuest,
            EditorOriginals.of(quest, ClientCache::getQuestById),
            LocationCard.LocationSnapshot.of(quest),
            newEditDraft()));
  }

  /** The tree, visibility, prerequisites and auto-join as the editor opened, nothing changed. */
  private QuestEditDraft newEditDraft() {
    return new QuestEditDraft(QuestGraphs.current(), quest.toData());
  }

  private void cancelEdit() {
    confirmLeave(this::exitToViewMode);
  }

  /**
   * Cancel, back, forward and Escape in the editor: with unsaved changes (text, location or any
   * draft) the player confirms first, and the changes are dropped before leaving.
   */
  @Override
  protected void confirmLeave(Runnable leave) {
    if (!editing || !isDirty()) {
      leave.run();
      return;
    }
    showConfirmOverlay(
        Component.translatable("gui.disquests.confirm.discard"),
        () -> {
          restoreOriginals();
          leave.run();
        });
  }

  /** Escape: in the editor, asks before dropping unsaved changes. */
  @Override
  public void onClose() {
    if (editing) {
      confirmLeave(super::onClose);
    } else {
      super.onClose();
    }
  }

  /**
   * Puts back what the editor wrote into the cached quest; the drafts are simply dropped. The cache
   * version moves on, so anything built from the quest meanwhile (a {@code QuestGraphs} graph, a
   * list) is rebuilt without the cancelled text.
   */
  private void restoreOriginals() {
    // Exactly what the server sent, not the editing form ([[Title]] would show literally)
    if (originals != null) originals.restore(quest);
    // The map editor and vertex rows write straight into the cached quest.
    if (originalLocation != null) {
      originalLocation.restore(quest);
    }
    ClientCache.markChanged();
  }

  /** After a tree change made from the form (the hidden-parent Detach): pane and form follow. */
  private void treeChanged() {
    if (editPane != null) editPane.fill();
    if (linksForm != null) linksForm.refresh();
    dropStaleFocus();
  }

  /**
   * Clears the focus when it sits on a component a refresh just removed (a dragged row, a pressed
   * button or chip), so Enter or Space cannot press it again. The quest search fields are kept
   * (they are never removed, and their focus-lost handler queues on the root, which must not happen
   * while the root runs its queue).
   */
  void dropStaleFocus() {
    FlowLayout root = getRootComponent();
    if (root == null || root.focusHandler() == null) return;
    UIComponent focused = root.focusHandler().focused();
    if (focused == null || focused instanceof QuestSearchField) return;
    UIComponent top = focused;
    while (top.parent() != null) top = top.parent();
    if (top != root) root.focusHandler().focus(null, UIComponent.FocusSource.MOUSE_CLICK);
  }

  /** A toast for a change the draft refused, worded like the server's refusal notices. */
  void showRefused(UUID questId, Refusal refusal) {
    var q = editDraft != null ? editDraft.tree().effective().get(questId) : null;
    Notice notice =
        new Notice(Notice.Kind.CHANGE_REFUSED, questId, q != null ? q.title() : null, refusal);
    showRefusedToast(Messages.toComponent(NoticeText.of(notice, ClientSession.getMaxQuestDepth())));
  }

  private void exitToViewMode() {
    Quest archived = archivedMeanwhile(quest.getId());
    if (archived != null) {
      // Completed by a teammate while editing: show it as it is now, not the stale copy.
      navigateToScreen(new QuestScreen(this.parent, archived));
    } else if (isNewQuest) {
      navigateToScreen(this.parent);
    } else {
      navigateToScreen(new QuestScreen(this.parent, quest));
    }
  }

  private void saveAndView() {
    // The server drops a save it refuses without an answer: check the quest as it is now.
    Quest cached = ClientCache.getQuestById(quest.getId());
    switch (SaveGuard.check(cached, ClientSession.getEffectivePlayerUuid(), isNewQuest)) {
      case ARCHIVED -> {
        // A teammate completed the quest while we edited.
        showArchivedInstead(this.parent, cached);
        return;
      }
      case NO_LONGER_EDITABLE -> {
        // Demoted to Read, removed, or the quest locked for us meanwhile: nothing is sent or
        // written to the cache, and the reading view shows the quest as it is now.
        showRefusedToast(Component.translatable(SaveGuard.NO_LONGER_EDITABLE));
        navigateToScreen(new QuestScreen(this.parent, cached));
        return;
      }
      case GONE -> {
        super.onClose();
        return;
      }
      case SEND -> {}
    }
    persistFieldValues();
    LinkChanges draftLinks = editDraft != null ? editDraft.toLinkChanges() : LinkChanges.NONE;
    // No longer an owner (lowered to Write meanwhile): only the text and the tree moves go out
    SaveGuard.Links sendable =
        SaveGuard.linksToSend(cached, ClientSession.getEffectivePlayerUuid(), draftLinks);
    LinkChanges links = sendable.links();
    if (sendable.ownerPartsDropped()) {
      showRefusedToast(Component.translatable(SaveGuard.NO_LONGER_OWNER));
    }
    // Shown at once, as before; the server's answer corrects it if it refuses.
    if (links.visibility() != null) quest.setVisibility(links.visibility());
    ClientCache.addOrUpdateMyQuest(quest);
    PacketSender.saveQuest(
        quest.getId(),
        quest.getTitle(),
        quest.getContent(),
        quest.getCoordinates(),
        quest.isRegion(),
        quest.getCoordinates2(),
        quest.getMap(),
        quest.getTags(),
        quest.getPolygon(),
        links);
    // A server without quest trees takes no links block: visibility goes on its own, as before.
    if (!ClientSession.supportsTrees() && links.visibility() != null) {
      PacketSender.updateVisibility(quest.getId(), links.visibility());
    }

    navigateToScreen(new QuestScreen(this.parent, quest));
  }

  // ===================== VIEW MODE ACTIONS =====================

  /** "Completed on <date> by <player>", without the name when the server does not know it. */
  private Component completedText() {
    String date = formatDate(quest.getCompletedAt());
    String name = quest.getCompletedByName();
    return name != null
        ? Component.translatable("gui.disquests.label.completed_by", date, name)
        : Component.translatable("gui.disquests.label.completed", date);
  }

  /** A date as the "Completed on" label shows it, in the game language. */
  static String formatDate(long epochMillis) {
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(gameLocale())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis));
  }

  /** The game language (e.g. fr_fr) as a Locale, so dates follow it rather than the JVM. */
  private static Locale gameLocale() {
    String code = Minecraft.getInstance().getLanguageManager().getSelected();
    return code != null ? Locale.forLanguageTag(code.replace('_', '-')) : Locale.getDefault();
  }

  /**
   * Completes after a confirm that offers to complete the player's unfinished subquests too, and
   * says how many others stay active. Without subquests it is the plain question.
   */
  private void confirmComplete() {
    QuestPage.Dialog dialog =
        QuestPage.completeDialog(
            Plans.subquestCounts(
                QuestGraphs.current(),
                ClientSession.getEffectivePlayerUuid(),
                quest.getId(),
                true));
    showConfirmOverlay(
        Component.translatable("gui.disquests.confirm.complete").getString(),
        text(dialog.extra()),
        text(dialog.checkbox()),
        alsoMine -> {
          PacketSender.completeQuest(quest.getId(), alsoMine);
          navigateToScreen(this.parent);
        });
  }

  @Nullable
  private static String text(@Nullable NoticeText.Message message) {
    return message == null ? null : Messages.toComponent(message).getString();
  }

  private void confirmReopen() {
    // The server answers with UPDATE_QUEST; the page signature changes and refreshIfPageChanged
    // rebuilds this page with the active quest, so the buttons switch back.
    showConfirmOverlay(
        Component.translatable("gui.disquests.confirm.reopen"),
        () -> PacketSender.reopenQuest(quest.getId()));
  }

  /** Deletes after a confirm that offers to delete the player's subquests too. */
  private void confirmDelete() {
    QuestPage.Dialog dialog =
        QuestPage.deleteDialog(
            Plans.subquestCounts(
                QuestGraphs.current(),
                ClientSession.getEffectivePlayerUuid(),
                quest.getId(),
                false));
    showConfirmOverlay(
        Component.translatable("gui.disquests.confirm.delete", quest.getTitle()).getString(),
        text(dialog.extra()),
        text(dialog.checkbox()),
        alsoMine -> {
          ClientCache.removeQuestById(quest.getId());
          PacketSender.deleteQuest(quest.getId(), alsoMine);
          navigateToScreen(this.parent);
        });
  }

  private void leaveQuest() {
    showConfirmOverlay(
        Component.translatable("gui.disquests.confirm.leave"),
        () -> {
          ClientCache.removeFromMyQuests(quest.getId());
          PacketSender.leaveQuest(quest.getId());
          ClientSession.setPendingToast("Left \"" + quest.getTitle() + "\"");
          navigateToScreen(this.parent);
        });
  }

  private void joinQuest() {
    PacketSender.joinQuest(quest.getId());
    ClientSession.setPendingToast("Joined \"" + quest.getTitle() + "\"");
    navigateToScreen(this.parent);
  }

  private void requestAccess() {
    PacketSender.requestCollaboration(quest.getId());
    ClientSession.markRequested(quest.getId());
    ClientSession.setPendingToast("Request sent to " + quest.getOwnerName());
    navigateToScreen(this.parent);
  }

  // ===================== EDIT MODE ACTIONS =====================

  /** Saves the fields, then opens the full-screen map editor on the current location. */
  void openMapEditor() {
    persistFieldValues();
    List<PointData> initial;
    if (!quest.getPolygon().isEmpty()) {
      initial = quest.getPolygon();
    } else if (quest.getCoordinates() != null) {
      initial = List.of(new PointData(quest.getCoordinates().x(), quest.getCoordinates().z()));
    } else {
      initial = List.of();
    }
    if (minecraft != null) {
      minecraft.gui.setScreen(new MapEditScreen(this, dimensionForEdit(), initial));
    }
  }

  /** Pins the quest at the player, dropping any outline; the map becomes the player's. */
  void setPlayerPosition() {
    if (minecraft != null && minecraft.player != null) {
      persistFieldValues();
      quest.setCoordinates(
          new CoordinatesData(
              minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ()));
      quest.setCoordinates2(null);
      quest.setRegion(false);
      quest.setPolygon(List.of());
      quest.setMap(
          DimensionKeys.normalize(minecraft.player.level().dimension().identifier().getPath()));
      rebuildEditMode();
    }
  }

  /** Removes the pin or outline; the quest's map goes back to null. */
  void clearCoords() {
    persistFieldValues();
    applyMapLocation(List.of());
  }

  /**
   * Cycles overworld, the_nether, the_end, starting from the map shown on the button. Does nothing
   * while the quest is unlocated, so {@code map} stays null exactly then.
   */
  void cycleMap() {
    if (!quest.hasLocation()) return;
    persistFieldValues();
    quest.setMap(
        switch (dimensionForEdit()) {
          case DimensionKeys.OVERWORLD -> DimensionKeys.THE_NETHER;
          case DimensionKeys.THE_NETHER -> DimensionKeys.THE_END;
          default -> DimensionKeys.OVERWORLD;
        });
    rebuildEditMode();
  }

  /**
   * The people button's label: the saved quest's contributor count and the pending requests. Set
   * when the editor is built, whenever it becomes the current screen again (the instance is reused
   * when the people screen returns to it), and from {@link #tick} after any cache change, because
   * the server's answer to a people save arrives after that return. Only the button's message is
   * set: nothing is rebuilt and the editor's draft is untouched.
   */
  private void refreshContributorsButton(FlowLayout root) {
    ButtonComponent contribBtn = root.childById(ButtonComponent.class, "btn-contributors");
    if (contribBtn == null) return;
    contributorsLabelVersion = ClientCache.getVersion();
    Quest saved = ClientCache.getQuestById(quest.getId());
    int contribCount = (saved != null ? saved : quest).getContributors().size();
    int pendingReqCount = ClientCache.getPendingCount(quest.getId());
    Component contribText;
    if (pendingReqCount > 0) {
      contribText =
          Component.translatable("gui.disquests.section.contributors", contribCount)
              .append(Component.literal(" "))
              .append(Component.literal("+ " + pendingReqCount).withColor(Colors.AMBER));
    } else {
      contribText = Component.translatable("gui.disquests.section.contributors", contribCount);
    }
    contribBtn.setMessage(contribText);
  }

  /** Back from the people screen to this editor: its people button follows the saved quest. */
  @Override
  protected void init() {
    super.init();
    FlowLayout root = getRootComponent();
    if (editing && isOwner && root != null) refreshContributorsButton(root);
  }

  private void openContributors() {
    persistFieldValues();
    navigateToScreen(new ContributorScreen(this, quest));
  }

  /**
   * Applies a location drawn on the map: no corners clears it, one is a pin, two or more an
   * outline. A location sets the quest's map to {@link #dimensionForEdit()}; clearing sets it to
   * null. Rebuilds the editor.
   */
  public void applyMapLocation(List<PointData> vertices) {
    setLocation(vertices, false);
    rebuildEditMode();
  }

  /**
   * Applies corners dragged in the location card's preview with the rules of {@link
   * #applyMapLocation}, but keeps the screen (the drag must not tear it down) and a pin's height.
   */
  void moveMapLocation(List<PointData> vertices) {
    setLocation(vertices, true);
  }

  private void setLocation(List<PointData> vertices, boolean keepPinY) {
    String dimension = dimensionForEdit();
    if (vertices.isEmpty()) {
      quest.setCoordinates(null);
      quest.setCoordinates2(null);
      quest.setRegion(false);
      quest.setPolygon(List.of());
      dimension = null;
    } else if (vertices.size() == 1) {
      PointData p = vertices.get(0);
      double y = LocationCard.DEFAULT_Y;
      if (keepPinY && quest.getCoordinates() != null && !quest.isPolygon()) {
        y = quest.getCoordinates().y();
      } else if (minecraft != null
          && minecraft.player != null
          && minecraft.player.level().dimension().identifier().getPath().equals(dimension)) {
        y = minecraft.player.getY();
      }
      quest.setCoordinates(new CoordinatesData(p.x(), y, p.z()));
      quest.setCoordinates2(null);
      quest.setRegion(false);
      quest.setPolygon(List.of());
    } else {
      CoordinatesData[] box = QuestLocation.boundingBox(vertices, LocationCard.DEFAULT_Y);
      quest.setPolygon(List.copyOf(vertices));
      quest.setCoordinates(box[0]);
      quest.setCoordinates2(box[1]);
      quest.setRegion(true);
    }
    quest.setMap(dimension);
  }

  /** The quest's map, or the player's dimension when the quest has none. */
  String dimensionForEdit() {
    if (quest.getMap() != null) {
      return quest.getMap();
    }
    if (minecraft != null && minecraft.level != null) {
      return minecraft.level.dimension().identifier().getPath();
    }
    return DimensionKeys.OVERWORLD;
  }

  private void rebuildEditMode() {
    navigateToScreen(
        new QuestScreen(
            this.parent, quest, true, isNewQuest, originals, originalLocation, editDraft));
  }

  // ===================== HELPERS =====================

  private void replaceSlot(FlowLayout parent, String slotId, UIComponent replacement) {
    UIComponent slot = parent.childById(UIComponent.class, slotId);
    if (slot != null) {
      int idx = parent.children().indexOf(slot);
      parent.removeChild(slot);
      if (idx >= 0) parent.child(idx, replacement);
      else parent.child(replacement);
    } else {
      parent.child(replacement);
    }
  }

  private void persistFieldValues() {
    if (titleFieldComponent != null) {
      quest.setTitle(titleFieldComponent.getText());
    }
    if (contentFieldComponent != null) {
      quest.setContent(contentFieldComponent.getText());
    }
    LocationCard.persistVertexFields(quest, vertexRows);
  }

  private boolean isDirty() {
    String currentTitle =
        titleFieldComponent != null ? titleFieldComponent.getText() : quest.getTitle();
    String currentContent =
        contentFieldComponent != null ? contentFieldComponent.getText() : quest.getContent();
    if (currentTitle == null) currentTitle = "";
    if (currentContent == null) currentContent = "";
    return (originals != null && originals.textChanged(currentTitle, currentContent))
        || (originalLocation != null && !originalLocation.sameLocation(quest))
        || LocationCard.vertexFieldsEdited(quest, vertexRows)
        || (editDraft != null && editDraft.isDirty());
  }

  private String toggleCheckbox(String content, int index, boolean nowChecked) {
    int count = 0;
    int i = 0;
    while (i < content.length() - 2) {
      int idx = content.indexOf("[ ]", i);
      int idx2 = content.indexOf("[x]", i);
      if (idx < 0 && idx2 < 0) break;
      int found;
      if (idx < 0) found = idx2;
      else if (idx2 < 0) found = idx;
      else found = Math.min(idx, idx2);
      if (found >= 2 && content.substring(found - 2, found).equals("- ")) {
        if (count == index) {
          String replacement = nowChecked ? "[x]" : "[ ]";
          return content.substring(0, found) + replacement + content.substring(found + 3);
        }
        count++;
      }
      i = found + 3;
    }
    return content;
  }

  // ===================== AUTO-CLOSE =====================

  @Override
  public void tick() {
    super.tick();
    if (editPane != null) editPane.tick();
    if (isNewQuest) return;
    Quest cached = ClientCache.getQuestById(quest.getId());
    if (cached == null) {
      // Gone (deleted, or no longer visible): close without asking about unsaved changes.
      super.onClose();
      return;
    }
    // The carried place wins over the pane's automatic reveal of the current row
    if (carriedPlace != null && applyCarriedPlace(carriedPlace)) carriedPlace = null;
    if (treePane != null) treePane.tick();
    refreshIfPageChanged(cached);
    // The editor is never rebuilt, but its people button follows the cache (a people save's echo)
    FlowLayout root = getRootComponent();
    if (editing
        && isOwner
        && root != null
        && ClientCache.getVersion() != contributorsLabelVersion) {
      refreshContributorsButton(root);
    }
  }

  /**
   * Rebuilds the reading view, in place and without a history entry, when the cache changed what it
   * draws: the quest itself (role, people, visibility, completion, lock, banners), the quests in
   * the pane or the prerequisites. While a confirm dialog is open the check waits until it closes,
   * so the player's answer is never thrown away. The editor is never rebuilt.
   */
  private void refreshIfPageChanged(Quest cached) {
    if (editing || shownPage == null || minecraft == null) return;
    long version = ClientCache.getVersion();
    if (version == seenCacheVersion) return;
    FlowLayout root = getRootComponent();
    if (root != null && root.childById(OverlayContainer.class, CONFIRM_OVERLAY_ID) != null) return;
    seenCacheVersion = version;
    if (!pageSignature(cached).equals(shownPage)) {
      QuestScreen rebuilt = new QuestScreen(this.parent, cached);
      // Same page, new state: keep the reader where they were
      rebuilt.carriedPlace =
          new ReaderPlace(
              contentScroll != null ? contentScroll.offset() : 0,
              treePane != null ? treePane.offset() : null);
      minecraft.gui.setScreen(rebuilt);
    }
  }

  /**
   * Applies a carried place once the page is laid out; false while it is not mounted yet. A locked
   * page has no content scroll and a page without a pane ignores the pane offset.
   */
  private boolean applyCarriedPlace(ReaderPlace place) {
    if (contentScroll != null && contentScroll.hasParent()) {
      contentScroll.jumpTo(place.content());
    } else if (contentScroll != null && !quest.isLocked()) {
      return false;
    }
    if (treePane != null && place.pane() != null) {
      return treePane.restore(place.pane());
    }
    return true;
  }

  /**
   * Wiki-link suggestions, as before: my quests then the board's, titles starting with the query.
   */
  private static List<String> wikiLinkTitles(String query) {
    // Locked quests are not offered: the link would show "Private Quest" to its own author
    return WikiLinks.suggestions(
        Stream.concat(ClientCache.getMyQuests().stream(), ClientCache.getServerQuests().stream())
            .toList(),
        query,
        WIKI_LINK_SUGGESTIONS);
  }
}
