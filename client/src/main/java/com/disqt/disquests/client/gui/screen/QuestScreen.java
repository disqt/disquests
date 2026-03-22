package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.BlueMapHelper;
import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.TextFieldComponent;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.widget.MarkdownWidget;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import com.disqt.disquests.client.markdown.RenderedLine;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.Visibility;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Unified quest screen with view and edit modes, using owo-ui.
 * Switches between two XML models by re-opening with different state.
 */
public class QuestScreen extends DisquestsBaseScreen {

    private final Quest quest;
    private final boolean editing;
    private final boolean isNewQuest;

    // Edit mode state preserved across rebuilds
    private boolean regionEnabled;
    private boolean showFormattingHelp;
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

    /**
     * Open in view mode for an existing quest.
     */
    public QuestScreen(@Nullable Screen parent, Quest quest) {
        this(parent, quest, false, false, true, null, null);
    }

    /**
     * Open in edit mode when startInEditMode is true (e.g. new quest).
     */
    public QuestScreen(@Nullable Screen parent, Quest quest, boolean startInEditMode) {
        this(parent, quest, startInEditMode, startInEditMode, true, null, null);
    }

    /**
     * Internal constructor preserving edit state across mode switches.
     */
    private QuestScreen(@Nullable Screen parent, Quest quest, boolean editing, boolean isNewQuest,
                        boolean showFormattingHelp, @Nullable String originalTitle, @Nullable String originalContent) {
        super(DataSource.asset(Identifier.of("disquests",
                editing ? "quest_screen_edit" : "quest_screen_view")), parent);
        this.quest = quest;
        this.editing = editing;
        this.isNewQuest = isNewQuest;
        this.regionEnabled = quest.isRegion();
        this.showFormattingHelp = showFormattingHelp;
        this.originalTitle = originalTitle;
        this.originalContent = originalContent;
    }

    // --- Accessors ---

    public Quest getQuest() { return quest; }
    public boolean isEditing() { return editing; }
    public boolean hasLeaveButton() { return isContributor && !isOwner && !editing; }

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
        this.isOwner = quest.getOwnerUuid().equals(myUuid);
        this.canEdit = isOwner || quest.getContributors().stream()
                .anyMatch(c -> c.getUuid().equals(myUuid) && c.canEdit());
        this.isContributor = quest.getContributors().stream()
                .anyMatch(c -> c.getUuid().equals(myUuid));
        this.hideContent = quest.getVisibility() == Visibility.CLOSED && !isOwner && !isContributor;

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

        // Content area -- add MarkdownWidget
        String contentToRender = hideContent
                ? "Request access to view this quest"
                : Objects.requireNonNullElse(quest.getContent(), "");
        List<RenderedLine> rendered = MarkdownRenderer.render(contentToRender);
        MarkdownWidget markdownWidget = new MarkdownWidget(rendered);
        markdownWidget.sizing(Sizing.fill(100), Sizing.fill(100));

        if (!hideContent) {
            markdownWidget.setCheckboxToggleListener((checkboxIndex, nowChecked) -> {
                if (!canEdit) return;
                String content = quest.getContent();
                if (content == null) return;
                String updated = toggleCheckbox(content, checkboxIndex, nowChecked);
                quest.setContent(updated);
                List<RenderedLine> rerendered = MarkdownRenderer.render(updated);
                markdownWidget.setContent(rerendered);
                PacketSender.saveQuest(quest.getId(), quest.getTitle(), updated,
                        quest.getCoordinates(), quest.isRegion(), quest.getCoordinates2(), quest.getMap());
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
                ButtonComponent bmBtn = UIComponents.button(Text.literal("View on BlueMap"), b -> {
                    try {
                        net.minecraft.util.Util.getOperatingSystem().open(URI.create(url));
                    } catch (Exception ignored) {}
                });
                bmBtn.sizing(Sizing.content(), Sizing.fixed(14));
                metadataRow.child(bmBtn);
            }
        } else {
            metadataRow.sizing(Sizing.fixed(0), Sizing.fixed(0));
        }

        // Contributors row
        FlowLayout contributorsRow = root.childById(FlowLayout.class, "contributors-row");
        if (!quest.getContributors().isEmpty()) {
            String contribText = "Contributors: " + quest.getContributors().stream()
                    .map(c -> c.getName())
                    .collect(Collectors.joining(", "));
            root.childById(LabelComponent.class, "contributors-label")
                    .text(Text.literal(contribText).withColor(Colors.TEXT_MUTED));
        } else {
            contributorsRow.sizing(Sizing.fixed(0), Sizing.fixed(0));
        }

        // Buttons
        FlowLayout buttonRow = root.childById(FlowLayout.class, "button-row");
        boolean canJoinOrRequest = !isOwner && !isContributor;

        // Join button (OPEN quests, not yet a member)
        ButtonComponent joinBtn = root.childById(ButtonComponent.class, "btn-join");
        if (canJoinOrRequest && quest.getVisibility() == Visibility.OPEN) {
            joinBtn.onPress(b -> joinQuest());
        } else {
            buttonRow.removeChild(joinBtn);
        }

        // Request button (CLOSED quests, not yet a member)
        ButtonComponent requestBtn = root.childById(ButtonComponent.class, "btn-request");
        if (canJoinOrRequest && quest.getVisibility() == Visibility.CLOSED) {
            if (ClientSession.isRequested(quest.getId())) {
                requestBtn.active = false;
                requestBtn.setMessage(Text.literal("Requested"));
            } else {
                requestBtn.onPress(b -> requestAccess());
            }
        } else {
            buttonRow.removeChild(requestBtn);
        }

        ButtonComponent editBtn = root.childById(ButtonComponent.class, "btn-edit");
        editBtn.onPress(b -> enterEditMode());
        editBtn.active = canEdit;

        ButtonComponent leaveBtn = root.childById(ButtonComponent.class, "btn-leave");
        if (isContributor && !isOwner) {
            leaveBtn.onPress(b -> leaveQuest());
        } else {
            buttonRow.removeChild(leaveBtn);
        }

        ButtonComponent deleteBtn = root.childById(ButtonComponent.class, "btn-delete");
        deleteBtn.onPress(b -> confirmDelete());
        deleteBtn.active = isOwner;

        root.childById(ButtonComponent.class, "btn-close")
                .onPress(b -> this.close());
    }

    // ===================== EDIT MODE =====================

    private void buildEditMode(FlowLayout root) {
        // Snapshot originals for dirty tracking
        if (originalTitle == null) {
            originalTitle = quest.getTitle() != null ? quest.getTitle() : "";
            originalContent = quest.getContent() != null ? quest.getContent() : "";
        }

        // Title field
        FlowLayout titleRow = root.childById(FlowLayout.class, "title-row");
        MultiLineTextFieldWidget titleField = new MultiLineTextFieldWidget(
                client.textRenderer, 0, 0, 300, 16,
                quest.getTitle(), "Quest title...", 1, false);
        titleFieldComponent = new TextFieldComponent(titleField);
        titleFieldComponent.sizing(Sizing.fill(90), Sizing.fixed(16));
        titleFieldComponent.id("title-field");
        titleRow.child(titleFieldComponent);

        // Content editor
        FlowLayout editorPanel = root.childById(FlowLayout.class, "editor-panel");
        MultiLineTextFieldWidget contentField = new MultiLineTextFieldWidget(
                client.textRenderer, 0, 0, 400, 200,
                quest.getContent() != null ? quest.getContent() : "",
                "Quest content...", Integer.MAX_VALUE, true, true);
        contentFieldComponent = new TextFieldComponent(contentField);
        contentFieldComponent.sizing(Sizing.fill(100), Sizing.fill(100));
        contentFieldComponent.id("content-field");
        editorPanel.child(contentFieldComponent);

        // Coords section
        buildCoordsSection(root);

        // Settings row (owner only)
        FlowLayout settingsRow = root.childById(FlowLayout.class, "settings-row");
        if (isOwner) {
            String visText = "Visibility: " + quest.getVisibility().name();
            ButtonComponent visBtn = root.childById(ButtonComponent.class, "btn-visibility");
            visBtn.setMessage(Text.literal(visText));
            visBtn.onPress(b -> cycleVisibility());

            int contribCount = quest.getContributors() != null ? quest.getContributors().size() : 0;
            int pendingReqCount = ClientCache.getPendingCount(quest.getId());
            Text contribText;
            if (pendingReqCount > 0) {
                contribText = Text.literal("Contributors (" + contribCount + " ")
                        .append(Text.literal("+ " + pendingReqCount).withColor(Colors.AMBER))
                        .append(Text.literal(")"));
            } else {
                contribText = Text.literal("Contributors (" + contribCount + ")");
            }
            ButtonComponent contribBtn = root.childById(ButtonComponent.class, "btn-contributors");
            contribBtn.setMessage(contribText);
            contribBtn.onPress(b -> openContributors());
        } else {
            settingsRow.sizing(Sizing.fixed(0), Sizing.fixed(0));
        }

        // Bottom buttons
        root.childById(ButtonComponent.class, "btn-save")
                .onPress(b -> saveAndView());
        root.childById(ButtonComponent.class, "btn-cancel")
                .onPress(b -> cancelEdit());
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
        ButtonComponent setPosBtn = UIComponents.button(Text.literal("Set Pos"), b -> setPlayerPosition());
        setPosBtn.sizing(Sizing.fixed(50), Sizing.fixed(14));
        setPosBtn.id("btn-set-pos");
        coordsRow.child(setPosBtn);

        // Region toggle
        String regionText = regionEnabled ? "[x] Region" : "[ ] Region";
        ButtonComponent regionBtn = UIComponents.button(Text.literal(regionText), b -> toggleRegion());
        regionBtn.sizing(Sizing.content(), Sizing.fixed(14));
        regionBtn.id("btn-region");
        coordsRow.child(regionBtn);

        // Clear button
        ButtonComponent clearBtn = UIComponents.button(Text.literal("Clear"), b -> clearCoords());
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

            ButtonComponent setPos2Btn = UIComponents.button(Text.literal("Set Pos"), b -> setCorner2Position());
            setPos2Btn.sizing(Sizing.fixed(50), Sizing.fixed(14));
            corner2Row.child(setPos2Btn);
        } else {
            corner2Row.sizing(Sizing.fixed(0), Sizing.fixed(0));
        }

        // Map row
        FlowLayout mapRow = root.childById(FlowLayout.class, "map-row");
        String mapDisplay = quest.getMap() != null ? quest.getMap() : "any";
        ButtonComponent mapBtn = UIComponents.button(Text.literal("Map: " + mapDisplay), b -> cycleMap());
        mapBtn.sizing(Sizing.content(), Sizing.fixed(14));
        mapBtn.id("btn-map");
        mapRow.child(mapBtn);
    }

    private TextFieldComponent createCoordField(String value, String placeholder) {
        MultiLineTextFieldWidget field = new MultiLineTextFieldWidget(
                client.textRenderer, 0, 0, 50, 14,
                value, placeholder, 1, false);
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
        String origContent = quest.getContent() != null ? quest.getContent() : "";
        if (this.client != null) {
            this.client.setScreen(new QuestScreen(this.parent, quest, true, isNewQuest, true, origTitle, origContent));
        }
    }

    private void cancelEdit() {
        if (isDirty()) {
            if (this.client != null) {
                this.client.setScreen(new ConfirmScreen(this,
                        Text.literal("Discard unsaved changes?"),
                        () -> {
                            quest.setTitle(originalTitle);
                            quest.setContent(originalContent);
                            exitToViewMode();
                        },
                        () -> {
                            if (this.client != null) this.client.setScreen(this);
                        }));
            }
        } else {
            exitToViewMode();
        }
    }

    private void exitToViewMode() {
        if (isNewQuest) {
            if (this.client != null) this.client.setScreen(this.parent);
        } else {
            if (this.client != null) {
                this.client.setScreen(new QuestScreen(this.parent, quest));
            }
        }
    }

    private void saveAndView() {
        persistFieldValues();
        ClientCache.addOrUpdateMyQuest(quest);
        PacketSender.saveQuest(quest.getId(), quest.getTitle(), quest.getContent(),
                quest.getCoordinates(), quest.isRegion(), quest.getCoordinates2(), quest.getMap());

        UUID myUuid = ClientSession.getEffectivePlayerUuid();
        if (quest.getOwnerUuid().equals(myUuid)) {
            PacketSender.updateVisibility(quest.getId(), quest.getVisibility());
        }

        if (this.client != null) {
            this.client.setScreen(new QuestScreen(this.parent, quest));
        }
    }

    // ===================== VIEW MODE ACTIONS =====================

    private void confirmDelete() {
        if (this.client != null) {
            this.client.setScreen(new ConfirmScreen(this,
                    Text.literal("Delete quest \"" + quest.getTitle() + "\"?"),
                    () -> {
                        ClientCache.removeQuestById(quest.getId());
                        PacketSender.deleteQuest(quest.getId());
                        if (this.client != null) this.client.setScreen(this.parent);
                    },
                    () -> {
                        if (this.client != null) this.client.setScreen(this);
                    }));
        }
    }

    private void leaveQuest() {
        if (this.client != null) {
            this.client.setScreen(new ConfirmScreen(this,
                    Text.literal("Leave this quest? You'll lose contributor access."),
                    () -> {
                        ClientCache.removeFromMyQuests(quest.getId());
                        PacketSender.leaveQuest(quest.getId());
                        ClientSession.setPendingToast("Left \"" + quest.getTitle() + "\"");
                        if (this.client != null) this.client.setScreen(this.parent);
                    },
                    () -> {
                        if (this.client != null) this.client.setScreen(this);
                    }));
        }
    }

    private void joinQuest() {
        PacketSender.joinQuest(quest.getId());
        ClientSession.setPendingToast("Joined \"" + quest.getTitle() + "\"");
        if (this.client != null) this.client.setScreen(this.parent);
    }

    private void requestAccess() {
        PacketSender.requestCollaboration(quest.getId());
        ClientSession.markRequested(quest.getId());
        ClientSession.setPendingToast("Request sent to " + quest.getOwnerName());
        if (this.client != null) this.client.setScreen(this.parent);
    }

    // ===================== EDIT MODE ACTIONS =====================

    private void setPlayerPosition() {
        if (client != null && client.player != null) {
            persistFieldValues();
            quest.setCoordinates(new CoordinatesData(
                    client.player.getX(), client.player.getY(), client.player.getZ()));
            if (quest.getMap() == null && client.world != null) {
                quest.setMap(client.world.getRegistryKey().getValue().getPath());
            }
            rebuildEditMode();
        }
    }

    private void setCorner2Position() {
        if (client != null && client.player != null) {
            persistFieldValues();
            quest.setCoordinates2(new CoordinatesData(
                    client.player.getX(), client.player.getY(), client.player.getZ()));
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
            quest.setMap("overworld");
        } else {
            quest.setMap(switch (current) {
                case "overworld" -> "the_nether";
                case "the_nether" -> "the_end";
                case "the_end" -> null; // cycles back to "any"
                default -> "overworld";
            });
        }
        rebuildEditMode();
    }

    private void cycleVisibility() {
        Visibility current = quest.getVisibility();
        Visibility next = switch (current) {
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
        if (this.client != null) {
            this.client.setScreen(new ContributorScreen(this, quest));
        }
    }

    private void rebuildEditMode() {
        if (this.client != null) {
            this.client.setScreen(new QuestScreen(this.parent, quest, true, isNewQuest,
                    showFormattingHelp, originalTitle, originalContent));
        }
    }

    // ===================== HELPERS =====================

    private void persistFieldValues() {
        if (titleFieldComponent != null) {
            quest.setTitle(titleFieldComponent.getText());
        }
        if (contentFieldComponent != null) {
            quest.setContent(contentFieldComponent.getText());
        }
        if (coordXComponent != null && coordYComponent != null && coordZComponent != null) {
            try {
                double x = Double.parseDouble(coordXComponent.getText().trim());
                double y = Double.parseDouble(coordYComponent.getText().trim());
                double z = Double.parseDouble(coordZComponent.getText().trim());
                quest.setCoordinates(new CoordinatesData(x, y, z));
            } catch (NumberFormatException e) {
                if (coordXComponent.getText().trim().isEmpty()
                        && coordYComponent.getText().trim().isEmpty()
                        && coordZComponent.getText().trim().isEmpty()) {
                    quest.setCoordinates(null);
                }
            }
        }
        if (coord2XComponent != null && coord2YComponent != null && coord2ZComponent != null) {
            try {
                double x2 = Double.parseDouble(coord2XComponent.getText().trim());
                double y2 = Double.parseDouble(coord2YComponent.getText().trim());
                double z2 = Double.parseDouble(coord2ZComponent.getText().trim());
                quest.setCoordinates2(new CoordinatesData(x2, y2, z2));
            } catch (NumberFormatException e) {
                if (coord2XComponent.getText().trim().isEmpty()
                        && coord2YComponent.getText().trim().isEmpty()
                        && coord2ZComponent.getText().trim().isEmpty()) {
                    quest.setCoordinates2(null);
                }
            }
        }
    }

    private boolean isDirty() {
        String currentTitle = titleFieldComponent != null ? titleFieldComponent.getText() : quest.getTitle();
        String currentContent = contentFieldComponent != null ? contentFieldComponent.getText() : quest.getContent();
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
            return String.format("Region: X:%.0f-%.0f Y:%.0f-%.0f Z:%.0f-%.0f",
                    Math.min(c.x(), c2.x()), Math.max(c.x(), c2.x()),
                    Math.min(c.y(), c2.y()), Math.max(c.y(), c2.y()),
                    Math.min(c.z(), c2.z()), Math.max(c.z(), c2.z()));
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

    @Override
    public boolean shouldPause() {
        return false;
    }
}
