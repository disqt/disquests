package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.AutocompleteDropdown;
import com.disqt.disquests.client.gui.component.TagChipComponent;
import com.disqt.disquests.client.gui.component.TextFieldComponent;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.widget.MarkdownWidget;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import com.disqt.disquests.client.markdown.RenderedLine;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.DimensionKeys;
import com.disqt.disquests.common.QuestLocation;
import com.disqt.disquests.common.TagConstraints;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.PointData;
import com.disqt.disquests.common.model.Visibility;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.CollapsibleContainer;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
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

  private final Quest quest;
  private final boolean editing;
  private final boolean isNewQuest;

  // Edit mode state preserved across rebuilds
  private String originalTitle;
  private String originalContent;
  private LocationCard.LocationSnapshot originalLocation;

  // Permission cache
  private boolean canEdit;
  private boolean isOwner;
  private boolean isContributor;
  private boolean hideContent;

  // Widget references for edit mode
  private TextFieldComponent titleFieldComponent;
  private TextFieldComponent contentFieldComponent;
  private List<LocationCard.VertexRow> vertexRows = List.of();

  /** Open in view mode for an existing quest. */
  public QuestScreen(@Nullable Screen parent, Quest quest) {
    this(parent, quest, false, false, null, null, null);
  }

  /** Open in edit mode when startInEditMode is true (e.g. new quest). */
  public QuestScreen(@Nullable Screen parent, Quest quest, boolean startInEditMode) {
    this(parent, quest, startInEditMode, startInEditMode, null, null, null);
  }

  /** Internal constructor preserving edit state across mode switches. */
  private QuestScreen(
      @Nullable Screen parent,
      Quest quest,
      boolean editing,
      boolean isNewQuest,
      @Nullable String originalTitle,
      @Nullable String originalContent,
      @Nullable LocationCard.LocationSnapshot originalLocation) {
    super(
        DataSource.asset(
            Identifier.fromNamespaceAndPath(
                "disquests", editing ? "quest_screen_edit" : "quest_screen_view")),
        parent);
    this.quest = quest;
    this.editing = editing;
    this.isNewQuest = isNewQuest;
    this.originalTitle = originalTitle;
    this.originalContent = originalContent;
    this.originalLocation = originalLocation;
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
    return isContributor && !isOwner && !editing;
  }

  @Override
  protected void build(FlowLayout root) {
    applyThemeRoot(root);

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

    UUID myUuid = ClientSession.getEffectivePlayerUuid();
    this.isOwner = quest.isOwner(myUuid);
    this.canEdit = quest.canEdit(myUuid);
    this.isContributor = quest.isContributor(myUuid);
    this.hideContent = quest.isContentHidden(myUuid);

    if (editing) {
      buildEditMode(root);
    } else {
      buildViewMode(root);
    }
  }

  // ===================== VIEW MODE =====================

  private void buildViewMode(FlowLayout root) {
    // Title
    root.childById(LabelComponent.class, "title-label")
        .text(Component.literal(quest.getTitle() != null ? quest.getTitle() : "Untitled"));

    // Owner + visibility info (built programmatically so content sizing measures real text)
    MutableComponent ownerText =
        Component.literal("by " + quest.getOwnerName()).withColor(Colors.TEXT_MUTED);
    if (quest.getVisibility() != null) {
      int visColor =
          switch (quest.getVisibility()) {
            case PRIVATE -> 0xFF5555; // red
            case CLOSED -> 0xFFAA00; // amber
            case OPEN -> 0x55FF55; // green
          };
      ownerText.append(
          Component.literal("  [" + quest.getVisibility().name() + "]").withColor(visColor));
    }
    LabelComponent ownerLabel = UIComponents.label(ownerText);
    ownerLabel.shadow(true);
    ownerLabel.sizing(Sizing.content(), Sizing.content());
    root.childById(FlowLayout.class, "header-row").child(ownerLabel);

    // Tag display -- inline chips (no collapsible)
    List<String> viewTags = quest.getTags();

    FlowLayout tagDisplay = UIContainers.ltrTextFlow(Sizing.fill(85), Sizing.content());
    tagDisplay.id("tag-display");
    tagDisplay.gap(4);
    tagDisplay.margins(Insets.bottom(4));
    for (String tag : viewTags) {
      tagDisplay.child(new TagChipComponent(tag));
    }

    replaceSlot(root, "tag-display-slot", tagDisplay);

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
            if (!canEdit) return;
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
          });
    }

    FlowLayout contentArea = root.childById(FlowLayout.class, "content-area");
    contentArea.child(markdownWidget);

    // Location card (mini map, coords, map, BlueMap button)
    LocationCard.buildView(this, root, quest);

    // Contributors row -- wrapped in collapsible
    int contribCount = quest.getContributors().size();
    boolean contribExpanded = contribCount <= 3;
    CollapsibleContainer contributorsCollapse =
        UIContainers.collapsible(
            Sizing.fill(85),
            Sizing.content(),
            Component.translatable("gui.disquests.section.contributors", contribCount),
            contribExpanded && contribCount > 0);
    contributorsCollapse.id("contributors-collapse");
    contributorsCollapse.margins(Insets.top(2));

    if (contribCount > 0) {
      String contribNames =
          quest.getContributors().stream().map(c -> c.getName()).collect(Collectors.joining(", "));
      LabelComponent contribLabel =
          UIComponents.label(Component.literal(contribNames).withColor(Colors.TEXT_MUTED));
      contribLabel.id("contributors-label");
      contribLabel.shadow(true);
      FlowLayout contributorsRow = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
      contributorsRow.id("contributors-row");
      contributorsRow.padding(Insets.left(4));
      contributorsRow.child(contribLabel);
      contributorsCollapse.child(contributorsRow);
    }

    replaceSlot(root, "contributors-row-slot", contributorsCollapse);

    // Buttons
    FlowLayout buttonRow = root.childById(FlowLayout.class, "button-row");
    boolean canJoinOrRequest = !isOwner && !isContributor;

    // Interact button: Join (OPEN), Request (CLOSED), or hidden (member/owner)
    ButtonComponent interactBtn = root.childById(ButtonComponent.class, "btn-interact");
    if (canJoinOrRequest) {
      if (quest.getVisibility() == Visibility.OPEN) {
        interactBtn.setMessage(Component.translatable("gui.disquests.btn.join"));
        interactBtn.onPress(ignored -> joinQuest());
      } else if (quest.getVisibility() == Visibility.CLOSED) {
        if (ClientSession.isRequested(quest.getId())) {
          interactBtn.setMessage(Component.translatable("gui.disquests.btn.requested"));
          interactBtn.active(false);
          interactBtn.tooltip(Component.translatable("gui.disquests.tooltip.already_requested"));
        } else {
          interactBtn.setMessage(Component.translatable("gui.disquests.btn.request"));
          interactBtn.onPress(ignored -> requestAccess());
        }
      } else {
        // PRIVATE quest viewed by non-member (shouldn't normally happen)
        buttonRow.removeChild(interactBtn);
      }
    } else {
      buttonRow.removeChild(interactBtn);
    }

    // Edit button: hidden for non-members, greyed with tooltip for view-only contributors
    ButtonComponent editBtn = root.childById(ButtonComponent.class, "btn-edit");
    if (canJoinOrRequest) {
      // Non-member: hide Edit entirely
      buttonRow.removeChild(editBtn);
    } else {
      editBtn.onPress(ignored -> enterEditMode());
      if (canEdit) {
        editBtn.active(true);
      } else {
        editBtn.active(false);
        editBtn.tooltip(Component.translatable("gui.disquests.tooltip.view_only"));
      }
    }

    ButtonComponent leaveBtn = root.childById(ButtonComponent.class, "btn-leave");
    if (isContributor && !isOwner) {
      leaveBtn.onPress(ignored -> leaveQuest());
    } else {
      buttonRow.removeChild(leaveBtn);
    }

    // Delete button: only visible for owner
    ButtonComponent deleteBtn = root.childById(ButtonComponent.class, "btn-delete");
    if (isOwner) {
      deleteBtn.onPress(ignored -> confirmDelete());
    } else {
      buttonRow.removeChild(deleteBtn);
    }

    wireBackButton(root);
  }

  // ===================== EDIT MODE =====================

  private void buildEditMode(FlowLayout root) {
    String editableContent =
        quest.getContent() != null
            ? MarkdownRenderer.reverseResolveWikiLinks(quest.getContent())
            : "";

    // Snapshot originals for dirty tracking
    if (originalTitle == null) {
      originalTitle = quest.getTitle() != null ? quest.getTitle() : "";
      originalContent = editableContent;
    }
    if (originalLocation == null) {
      originalLocation = LocationCard.LocationSnapshot.of(quest);
    }

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
    AutocompleteDropdown autocomplete = new AutocompleteDropdown();
    autocomplete.setOnSelect(
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
    contentFieldComponent.setAutocomplete(autocomplete);
    autocomplete.setRootComponent(root);

    // Location card
    vertexRows = LocationCard.buildEdit(this, root, quest);

    // Settings row (owner only)
    FlowLayout settingsRow = root.childById(FlowLayout.class, "settings-row");
    if (isOwner) {
      ButtonComponent visBtn = root.childById(ButtonComponent.class, "btn-visibility");
      visBtn.setMessage(
          Component.literal("Visibility: ")
              .append(
                  Component.translatable(
                      switch (quest.getVisibility()) {
                        case PRIVATE -> "gui.disquests.visibility.private";
                        case CLOSED -> "gui.disquests.visibility.closed";
                        case OPEN -> "gui.disquests.visibility.open";
                      })));
      visBtn.tooltip(
          Component.translatable(
              switch (quest.getVisibility()) {
                case PRIVATE -> "gui.disquests.tooltip.private";
                case CLOSED -> "gui.disquests.tooltip.closed";
                case OPEN -> "gui.disquests.tooltip.open";
              }));
      visBtn.onPress(ignored -> cycleVisibility());

      int contribCount = quest.getContributors().size();
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
      ButtonComponent contribBtn = root.childById(ButtonComponent.class, "btn-contributors");
      contribBtn.setMessage(contribText);
      contribBtn.onPress(ignored -> openContributors());
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
                        originalTitle,
                        originalContent,
                        originalLocation);
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
    String origTitle = quest.getTitle() != null ? quest.getTitle() : "";
    String origContent =
        quest.getContent() != null
            ? MarkdownRenderer.reverseResolveWikiLinks(quest.getContent())
            : "";
    navigateToScreen(
        new QuestScreen(
            this.parent,
            quest,
            true,
            isNewQuest,
            origTitle,
            origContent,
            LocationCard.LocationSnapshot.of(quest)));
  }

  private void cancelEdit() {
    if (isDirty()) {
      showConfirmOverlay(
          Component.translatable("gui.disquests.confirm.discard"),
          () -> {
            quest.setTitle(originalTitle);
            quest.setContent(originalContent);
            // The map editor and vertex rows write straight into the cached quest.
            if (originalLocation != null) {
              originalLocation.restore(quest);
            }
            exitToViewMode();
          });
    } else {
      exitToViewMode();
    }
  }

  private void exitToViewMode() {
    if (isNewQuest) {
      navigateToScreen(this.parent);
    } else {
      navigateToScreen(new QuestScreen(this.parent, quest));
    }
  }

  private void saveAndView() {
    persistFieldValues();
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
        quest.getPolygon());

    UUID myUuid = ClientSession.getEffectivePlayerUuid();
    if (quest.getOwnerUuid().equals(myUuid)) {
      PacketSender.updateVisibility(quest.getId(), quest.getVisibility());
    }

    navigateToScreen(new QuestScreen(this.parent, quest));
  }

  // ===================== VIEW MODE ACTIONS =====================

  private void confirmDelete() {
    showConfirmOverlay(
        Component.translatable("gui.disquests.confirm.delete", quest.getTitle()),
        () -> {
          ClientCache.removeQuestById(quest.getId());
          PacketSender.deleteQuest(quest.getId());
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

  private void cycleVisibility() {
    Visibility current = quest.getVisibility();
    Visibility next =
        switch (current) {
          case PRIVATE -> Visibility.CLOSED;
          case CLOSED -> Visibility.OPEN;
          case OPEN -> Visibility.PRIVATE;
        };
    quest.setVisibility(next);
    persistFieldValues();
    rebuildEditMode();
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
            this.parent,
            quest,
            true,
            isNewQuest,
            originalTitle,
            originalContent,
            originalLocation));
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
    return !currentTitle.equals(originalTitle)
        || !currentContent.equals(originalContent)
        || (originalLocation != null && !originalLocation.sameLocation(quest))
        || LocationCard.vertexFieldsEdited(quest, vertexRows);
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
    if (isNewQuest) return;
    if (ClientCache.getQuestById(quest.getId()) == null) {
      this.onClose();
    }
  }
}
