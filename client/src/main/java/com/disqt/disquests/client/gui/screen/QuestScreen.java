package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.BlueMapHelper;
import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.UrlOpener;
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
import com.disqt.disquests.common.TagConstraints;
import com.disqt.disquests.common.model.CoordinatesData;
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
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Unified quest screen with view and edit modes, using owo-ui. Switches between two XML models by
 * re-opening with different state.
 */
public class QuestScreen extends DisquestsBaseScreen {

  private static final String MAP_OVERWORLD = "overworld";
  private static final String MAP_NETHER = "the_nether";
  private static final String MAP_END = "the_end";

  private final Quest quest;
  private final boolean editing;
  private final boolean isNewQuest;

  // Edit mode state preserved across rebuilds
  private boolean regionEnabled;
  private String originalTitle;
  private String originalContent;

  // Permission cache
  private boolean canEdit;
  private boolean isOwner;
  private boolean isContributor;
  private boolean hideContent;

  // Widget references for edit mode
  private TextFieldComponent titleFieldComponent;
  private TextFieldComponent contentFieldComponent;
  private TextFieldComponent coordXComponent;
  private TextFieldComponent coordYComponent;
  private TextFieldComponent coordZComponent;
  private TextFieldComponent coord2XComponent;
  private TextFieldComponent coord2YComponent;
  private TextFieldComponent coord2ZComponent;

  /** Open in view mode for an existing quest. */
  public QuestScreen(@Nullable Screen parent, Quest quest) {
    this(parent, quest, false, false, null, null);
  }

  /** Open in edit mode when startInEditMode is true (e.g. new quest). */
  public QuestScreen(@Nullable Screen parent, Quest quest, boolean startInEditMode) {
    this(parent, quest, startInEditMode, startInEditMode, null, null);
  }

  /** Internal constructor preserving edit state across mode switches. */
  private QuestScreen(
      @Nullable Screen parent,
      Quest quest,
      boolean editing,
      boolean isNewQuest,
      @Nullable String originalTitle,
      @Nullable String originalContent) {
    super(
        DataSource.asset(
            Identifier.of("disquests", editing ? "quest_screen_edit" : "quest_screen_view")),
        parent);
    this.quest = quest;
    this.editing = editing;
    this.isNewQuest = isNewQuest;
    this.regionEnabled = quest.isRegion();
    this.originalTitle = originalTitle;
    this.originalContent = originalContent;
  }

  // --- Accessors ---

  public Quest getQuest() {
    return quest;
  }

  public boolean isEditing() {
    return editing;
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
    ParentUIComponent metadataRow = root.childById(ParentUIComponent.class, "metadata-row");
    if (metadataRow != null) applyThemePanel(metadataRow);

    // Edit mode panels
    ParentUIComponent titleRow = root.childById(ParentUIComponent.class, "title-row");
    if (titleRow != null) applyThemePanel(titleRow);
    ParentUIComponent editorPanel = root.childById(ParentUIComponent.class, "editor-panel");
    if (editorPanel != null) applyThemePanel(editorPanel);
    ParentUIComponent formattingPanel = root.childById(ParentUIComponent.class, "formatting-panel");
    if (formattingPanel != null) applyThemePanel(formattingPanel);
    ParentUIComponent coordsSection = root.childById(ParentUIComponent.class, "coords-section");
    if (coordsSection != null) applyThemePanel(coordsSection);

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
        .text(Text.literal(quest.getTitle() != null ? quest.getTitle() : "Untitled"));

    // Owner + visibility info
    String ownerInfo = "by " + quest.getOwnerName();
    if (quest.getVisibility() != null) {
      ownerInfo += "  [" + quest.getVisibility().name() + "]";
    }
    root.childById(LabelComponent.class, "owner-label")
        .text(Text.literal(ownerInfo).withColor(Colors.TEXT_MUTED));

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
                quest.getTags());
            ClientCache.addOrUpdateMyQuest(quest);
          });
    }

    FlowLayout contentArea = root.childById(FlowLayout.class, "content-area");
    contentArea.child(markdownWidget);

    // Metadata row (coords + BlueMap button)
    boolean hasCoords = quest.getCoordinates() != null;
    FlowLayout metadataRow = root.childById(FlowLayout.class, "metadata-row");
    if (hasCoords) {
      root.childById(LabelComponent.class, "coords-label")
          .text(Text.literal(buildCoordsText()).withColor(Colors.TEXT_MUTED));

      if (quest.getMap() != null) {
        root.childById(LabelComponent.class, "map-label")
            .text(Text.literal("Map: " + quest.getMap()).withColor(Colors.TEXT_MUTED));
      }

      // BlueMap button
      String bluemapUrl = BlueMapHelper.buildUrl(quest);
      if (bluemapUrl != null) {
        final String url = bluemapUrl;
        ButtonComponent bmBtn =
            UIComponents.button(
                Text.translatable("gui.disquests.btn.view_bluemap"),
                ignored -> UrlOpener.open(url));
        bmBtn.sizing(Sizing.content(), Sizing.fixed(14));
        metadataRow.child(bmBtn);
      }
    } else {
      metadataRow.sizing(Sizing.fixed(0), Sizing.fixed(0));
    }

    // Contributors row -- wrapped in collapsible
    int contribCount = quest.getContributors().size();
    boolean contribExpanded = contribCount <= 3;
    CollapsibleContainer contributorsCollapse =
        UIContainers.collapsible(
            Sizing.fill(85),
            Sizing.content(),
            Text.translatable("gui.disquests.section.contributors", contribCount),
            contribExpanded && contribCount > 0);
    contributorsCollapse.id("contributors-collapse");
    contributorsCollapse.margins(Insets.top(2));

    if (contribCount > 0) {
      String contribNames =
          quest.getContributors().stream().map(c -> c.getName()).collect(Collectors.joining(", "));
      LabelComponent contribLabel =
          UIComponents.label(Text.literal(contribNames).withColor(Colors.TEXT_MUTED));
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
        interactBtn.setMessage(Text.translatable("gui.disquests.btn.join"));
        interactBtn.onPress(ignored -> joinQuest());
      } else if (quest.getVisibility() == Visibility.CLOSED) {
        if (ClientSession.isRequested(quest.getId())) {
          interactBtn.setMessage(Text.translatable("gui.disquests.btn.requested"));
          interactBtn.active(false);
          interactBtn.tooltip(Text.translatable("gui.disquests.tooltip.already_requested"));
        } else {
          interactBtn.setMessage(Text.translatable("gui.disquests.btn.request"));
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
        editBtn.tooltip(Text.translatable("gui.disquests.tooltip.view_only"));
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

    // Title field
    FlowLayout titleRow = root.childById(FlowLayout.class, "title-row");
    MultiLineTextFieldWidget titleField =
        new MultiLineTextFieldWidget(
            client.textRenderer,
            0,
            0,
            300,
            16,
            quest.getTitle(),
            Text.translatable("gui.disquests.placeholder.title").getString(),
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
            client.textRenderer,
            0,
            0,
            400,
            200,
            editableContent,
            Text.translatable("gui.disquests.placeholder.content").getString(),
            Integer.MAX_VALUE,
            true,
            true);
    contentFieldComponent = new TextFieldComponent(contentField);
    contentFieldComponent.sizing(Sizing.fill(100), Sizing.fill(100));
    contentFieldComponent.id("content-field");
    editorPanel.child(contentFieldComponent);

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

    // Coords section
    buildCoordsSection(root);

    // Settings row (owner only)
    FlowLayout settingsRow = root.childById(FlowLayout.class, "settings-row");
    if (isOwner) {
      ButtonComponent visBtn = root.childById(ButtonComponent.class, "btn-visibility");
      visBtn.setMessage(
          Text.literal("Visibility: ")
              .append(
                  Text.translatable(
                      switch (quest.getVisibility()) {
                        case PRIVATE -> "gui.disquests.visibility.private";
                        case CLOSED -> "gui.disquests.visibility.closed";
                        case OPEN -> "gui.disquests.visibility.open";
                      })));
      visBtn.tooltip(
          Text.translatable(
              switch (quest.getVisibility()) {
                case PRIVATE -> "gui.disquests.tooltip.private";
                case CLOSED -> "gui.disquests.tooltip.closed";
                case OPEN -> "gui.disquests.tooltip.open";
              }));
      visBtn.onPress(ignored -> cycleVisibility());

      int contribCount = quest.getContributors().size();
      int pendingReqCount = ClientCache.getPendingCount(quest.getId());
      Text contribText;
      if (pendingReqCount > 0) {
        contribText =
            Text.translatable("gui.disquests.section.contributors", contribCount)
                .append(Text.literal(" "))
                .append(Text.literal("+ " + pendingReqCount).withColor(Colors.AMBER));
      } else {
        contribText = Text.translatable("gui.disquests.section.contributors", contribCount);
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
          Text.literal("**text**: ").append(Text.literal("text").styled(s -> s.withBold(true))));
    LabelComponent fmtItalic = root.childById(LabelComponent.class, "fmt-italic");
    if (fmtItalic != null)
      fmtItalic.text(
          Text.literal("*text*: ").append(Text.literal("text").styled(s -> s.withItalic(true))));
    LabelComponent fmtStrike = root.childById(LabelComponent.class, "fmt-strike");
    if (fmtStrike != null)
      fmtStrike.text(
          Text.literal("~~text~~: ")
              .append(Text.literal("text").styled(s -> s.withStrikethrough(true))));
    LabelComponent fmtHeading = root.childById(LabelComponent.class, "fmt-heading");
    if (fmtHeading != null)
      fmtHeading.text(
          Text.literal("# ").append(Text.literal("Heading").styled(s -> s.withBold(true))));
    LabelComponent fmtCheckbox = root.childById(LabelComponent.class, "fmt-checkbox");
    if (fmtCheckbox != null)
      fmtCheckbox.text(
          Text.literal("- [ ] todo  - [x] ")
              .append(
                  Text.literal("done").styled(s -> s.withStrikethrough(true).withColor(0x55FF55))));
    LabelComponent fmtQuote = root.childById(LabelComponent.class, "fmt-quote");
    if (fmtQuote != null)
      fmtQuote.text(
          Text.literal("> ")
              .append(Text.literal("quote").styled(s -> s.withItalic(true).withColor(0xAAAAAA))));
    LabelComponent fmtLink = root.childById(LabelComponent.class, "fmt-link");
    if (fmtLink != null)
      fmtLink.text(
          Text.literal("[text](url): ")
              .append(Text.literal("link").styled(s -> s.withUnderline(true).withColor(0x5555FF))));
    LabelComponent fmtWikiLink = root.childById(LabelComponent.class, "fmt-wikilink");
    if (fmtWikiLink != null)
      fmtWikiLink.text(
          Text.literal("[[Quest Name]]: ")
              .append(
                  Text.literal("quest link")
                      .styled(s -> s.withUnderline(true).withColor(0xe8a86d))));
  }

  private void buildCoordsSection(FlowLayout root) {
    CoordinatesData c = quest.getCoordinates();
    FlowLayout coordsRow = root.childById(FlowLayout.class, "coords-row");

    // X/Y/Z fields
    coordXComponent = createCoordField(c != null ? String.valueOf((int) c.x()) : "", "X");
    coordYComponent = createCoordField(c != null ? String.valueOf((int) c.y()) : "", "Y");
    coordZComponent = createCoordField(c != null ? String.valueOf((int) c.z()) : "", "Z");
    coordXComponent.id("coord-x1");
    coordYComponent.id("coord-y1");
    coordZComponent.id("coord-z1");

    coordsRow.child(labelFor("1:"));
    coordsRow.child(labelFor("X:"));
    coordsRow.child(coordXComponent);
    coordsRow.child(labelFor("Y:"));
    coordsRow.child(coordYComponent);
    coordsRow.child(labelFor("Z:"));
    coordsRow.child(coordZComponent);

    // Set Pos button
    ButtonComponent setPosBtn =
        UIComponents.button(
            Text.translatable("gui.disquests.btn.set_pos"), b -> setPlayerPosition());
    setPosBtn.sizing(Sizing.fixed(50), Sizing.fixed(14));
    setPosBtn.id("btn-set-pos");
    coordsRow.child(setPosBtn);

    // Region toggle
    Text regionText =
        regionEnabled
            ? Text.translatable("gui.disquests.btn.region_on")
            : Text.translatable("gui.disquests.btn.region_off");
    ButtonComponent regionBtn = UIComponents.button(regionText, b -> toggleRegion());
    regionBtn.sizing(Sizing.content(), Sizing.fixed(14));
    regionBtn.id("btn-region");
    coordsRow.child(regionBtn);

    // Clear button
    ButtonComponent clearBtn =
        UIComponents.button(Text.translatable("gui.disquests.btn.clear"), b -> clearCoords());
    clearBtn.sizing(Sizing.fixed(50), Sizing.fixed(14));
    clearBtn.id("btn-clear");
    coordsRow.child(clearBtn);

    // Corner 2 row
    FlowLayout corner2Row = root.childById(FlowLayout.class, "corner2-row");
    if (regionEnabled) {
      CoordinatesData c2 = quest.getCoordinates2();
      coord2XComponent = createCoordField(c2 != null ? String.valueOf((int) c2.x()) : "", "X");
      coord2YComponent = createCoordField(c2 != null ? String.valueOf((int) c2.y()) : "", "Y");
      coord2ZComponent = createCoordField(c2 != null ? String.valueOf((int) c2.z()) : "", "Z");
      coord2XComponent.id("coord-x2");
      coord2YComponent.id("coord-y2");
      coord2ZComponent.id("coord-z2");

      corner2Row.child(labelFor("2:"));
      corner2Row.child(labelFor("X:"));
      corner2Row.child(coord2XComponent);
      corner2Row.child(labelFor("Y:"));
      corner2Row.child(coord2YComponent);
      corner2Row.child(labelFor("Z:"));
      corner2Row.child(coord2ZComponent);

      ButtonComponent setPos2Btn =
          UIComponents.button(
              Text.translatable("gui.disquests.btn.set_pos"), b -> setCorner2Position());
      setPos2Btn.sizing(Sizing.fixed(50), Sizing.fixed(14));
      corner2Row.child(setPos2Btn);
    } else {
      corner2Row.sizing(Sizing.fixed(0), Sizing.fixed(0));
    }

    // Map row
    FlowLayout mapRow = root.childById(FlowLayout.class, "map-row");
    String mapDisplay = quest.getMap() != null ? quest.getMap() : "any";
    ButtonComponent mapBtn =
        UIComponents.button(Text.literal("Map: " + mapDisplay), b -> cycleMap());
    mapBtn.sizing(Sizing.content(), Sizing.fixed(14));
    mapBtn.id("btn-map");
    mapRow.child(mapBtn);
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
              Text.translatable("gui.disquests.btn.add_tag"),
              b -> {
                persistFieldValues();
                QuestScreen returnScreen =
                    new QuestScreen(
                        this.parent, quest, true, isNewQuest, originalTitle, originalContent);
                navigateToScreen(new TagPickerScreen(returnScreen, quest, returnScreen));
              });
      addTagBtn.sizing(Sizing.content(), Sizing.fixed(12));
      addTagBtn.id("btn-add-tag");
      tagEditor.child(addTagBtn);
    }
  }

  private TextFieldComponent createCoordField(String value, String placeholder) {
    MultiLineTextFieldWidget field =
        new MultiLineTextFieldWidget(
            client.textRenderer, 0, 0, 50, 14, value, placeholder, 1, false);
    TextFieldComponent component = new TextFieldComponent(field);
    component.sizing(Sizing.fixed(50), Sizing.fixed(14));
    return component;
  }

  private LabelComponent labelFor(String text) {
    LabelComponent label = UIComponents.label(Text.literal(text).withColor(Colors.TEXT_MUTED));
    label.shadow(true);
    return label;
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
    navigateToScreen(new QuestScreen(this.parent, quest, true, isNewQuest, origTitle, origContent));
  }

  private void cancelEdit() {
    if (isDirty()) {
      showConfirmOverlay(
          Text.translatable("gui.disquests.confirm.discard"),
          () -> {
            quest.setTitle(originalTitle);
            quest.setContent(originalContent);
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
        quest.getTags());

    UUID myUuid = ClientSession.getEffectivePlayerUuid();
    if (quest.getOwnerUuid().equals(myUuid)) {
      PacketSender.updateVisibility(quest.getId(), quest.getVisibility());
    }

    navigateToScreen(new QuestScreen(this.parent, quest));
  }

  // ===================== VIEW MODE ACTIONS =====================

  private void confirmDelete() {
    showConfirmOverlay(
        Text.translatable("gui.disquests.confirm.delete", quest.getTitle()),
        () -> {
          ClientCache.removeQuestById(quest.getId());
          PacketSender.deleteQuest(quest.getId());
          navigateToScreen(this.parent);
        });
  }

  private void leaveQuest() {
    showConfirmOverlay(
        Text.translatable("gui.disquests.confirm.leave"),
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

  private void setPlayerPosition() {
    if (client != null && client.player != null) {
      persistFieldValues();
      quest.setCoordinates(
          new CoordinatesData(client.player.getX(), client.player.getY(), client.player.getZ()));
      if (quest.getMap() == null && client.world != null) {
        quest.setMap(client.world.getRegistryKey().getValue().getPath());
      }
      rebuildEditMode();
    }
  }

  private void setCorner2Position() {
    if (client != null && client.player != null) {
      persistFieldValues();
      quest.setCoordinates2(
          new CoordinatesData(client.player.getX(), client.player.getY(), client.player.getZ()));
      rebuildEditMode();
    }
  }

  private void toggleRegion() {
    persistFieldValues();
    this.regionEnabled = !this.regionEnabled;
    quest.setRegion(this.regionEnabled);
    if (!this.regionEnabled) {
      quest.setCoordinates2(null);
    }
    rebuildEditMode();
  }

  private void clearCoords() {
    persistFieldValues();
    quest.setCoordinates(null);
    quest.setCoordinates2(null);
    this.regionEnabled = false;
    quest.setRegion(false);
    rebuildEditMode();
  }

  private void cycleMap() {
    persistFieldValues();
    String current = quest.getMap();
    if (current == null) {
      quest.setMap(MAP_OVERWORLD);
    } else {
      quest.setMap(
          switch (current) {
            case MAP_OVERWORLD -> MAP_NETHER;
            case MAP_NETHER -> MAP_END;
            case MAP_END -> null; // cycles back to "any"
            default -> MAP_OVERWORLD;
          });
    }
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

  private void rebuildEditMode() {
    navigateToScreen(
        new QuestScreen(this.parent, quest, true, isNewQuest, originalTitle, originalContent));
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
    if (coordXComponent != null) {
      quest.setCoordinates(parseCoordinates(coordXComponent, coordYComponent, coordZComponent));
    }
    if (coord2XComponent != null) {
      quest.setCoordinates2(parseCoordinates(coord2XComponent, coord2YComponent, coord2ZComponent));
    }
  }

  private CoordinatesData parseCoordinates(
      TextFieldComponent xComp, TextFieldComponent yComp, TextFieldComponent zComp) {
    if (xComp == null || yComp == null || zComp == null) return null;
    String xText = xComp.getText().trim();
    String yText = yComp.getText().trim();
    String zText = zComp.getText().trim();
    if (xText.isEmpty() && yText.isEmpty() && zText.isEmpty()) {
      return null;
    }
    try {
      return new CoordinatesData(
          Double.parseDouble(xText), Double.parseDouble(yText), Double.parseDouble(zText));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private boolean isDirty() {
    String currentTitle =
        titleFieldComponent != null ? titleFieldComponent.getText() : quest.getTitle();
    String currentContent =
        contentFieldComponent != null ? contentFieldComponent.getText() : quest.getContent();
    if (currentTitle == null) currentTitle = "";
    if (currentContent == null) currentContent = "";
    return !currentTitle.equals(originalTitle) || !currentContent.equals(originalContent);
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

  private String buildCoordsText() {
    CoordinatesData c = quest.getCoordinates();
    if (quest.isRegion() && quest.getCoordinates2() != null) {
      CoordinatesData c2 = quest.getCoordinates2();
      return String.format(
          "Region: X:%.0f-%.0f Y:%.0f-%.0f Z:%.0f-%.0f",
          Math.min(c.x(), c2.x()),
          Math.max(c.x(), c2.x()),
          Math.min(c.y(), c2.y()),
          Math.max(c.y(), c2.y()),
          Math.min(c.z(), c2.z()),
          Math.max(c.z(), c2.z()));
    }
    return String.format("X:%.0f Y:%.0f Z:%.0f", c.x(), c.y(), c.z());
  }

  // ===================== AUTO-CLOSE =====================

  @Override
  public void tick() {
    super.tick();
    if (isNewQuest) return;
    if (ClientCache.getQuestById(quest.getId()) == null) {
      this.close();
    }
  }
}
