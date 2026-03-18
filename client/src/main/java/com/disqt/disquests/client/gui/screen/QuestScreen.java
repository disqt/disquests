package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.BlueMapHelper;
import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.ScreenLayouts;
import com.disqt.disquests.client.gui.helper.UIHelper;
import com.disqt.disquests.client.gui.widget.DarkButtonWidget;
import com.disqt.disquests.client.gui.widget.MarkdownWidget;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import com.disqt.disquests.client.gui.widget.ReadOnlyMultiLineTextFieldWidget;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import com.disqt.disquests.client.markdown.RenderedLine;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.Visibility;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Unified quest screen that toggles between a formatted view mode and a raw editing mode.
 * Replaces the old ViewQuestScreen and EditQuestScreen.
 */
public class QuestScreen extends BaseScreen {

    private final Quest quest;
    private boolean editing;
    private final boolean isNewQuest;

    // --- View mode widgets ---
    private ReadOnlyMultiLineTextFieldWidget viewTitleArea;
    private MarkdownWidget viewContentArea;
    private DarkButtonWidget editButton;
    private DarkButtonWidget deleteButton;

    // --- Edit mode widgets ---
    private MultiLineTextFieldWidget editTitleField;
    private MultiLineTextFieldWidget editContentField;
    private MultiLineTextFieldWidget coordXField;
    private MultiLineTextFieldWidget coordYField;
    private MultiLineTextFieldWidget coordZField;
    private MultiLineTextFieldWidget coord2XField;
    private MultiLineTextFieldWidget coord2YField;
    private MultiLineTextFieldWidget coord2ZField;
    private DarkButtonWidget visibilityButton;
    private DarkButtonWidget contributorsButton;
    private DarkButtonWidget regionButton;
    private DarkButtonWidget helpButton;
    private boolean regionEnabled;
    private boolean showFormattingHelp = false;
    private static final int FORMATTING_PANEL_WIDTH = 120;

    // Dirty tracking (edit mode)
    private String originalTitle;
    private String originalContent;

    // Permission cache (recomputed each init)
    private boolean canEdit;
    private boolean isOwner;

    // --- Content area bounds (for click-to-edit in view mode) ---
    private int contentAreaX;
    private int contentAreaY;
    private int contentAreaWidth;
    private int contentAreaHeight;

    // --- Pre-computed view mode strings (set in initViewMode, used in renderViewMode) ---
    private String viewOwnerInfo;
    private String viewCoordsText;
    private String viewMapText;

    /**
     * Open in view mode for an existing quest.
     */
    public QuestScreen(Screen parent, Quest quest) {
        this(parent, quest, false);
    }

    /**
     * Open in edit mode when {@code startInEditMode} is true (e.g. new quest).
     */
    public QuestScreen(Screen parent, Quest quest, boolean startInEditMode) {
        super(Text.literal(startInEditMode ? "Edit Quest" : "View Quest"), parent);
        this.quest = quest;
        this.editing = startInEditMode;
        this.isNewQuest = startInEditMode;
        this.regionEnabled = quest.isRegion();
    }

    // --- Accessors for ContributorScreen ---

    public Quest getQuest() {
        return quest;
    }

    public MultiLineTextFieldWidget getTitleField() {
        return editTitleField;
    }

    public MultiLineTextFieldWidget getContentField() {
        return editContentField;
    }

    // --- Lifecycle ---

    @Override
    protected void init() {
        super.init();

        UUID myUuid = ClientSession.getEffectivePlayerUuid();
        this.isOwner = quest.getOwnerUuid().equals(myUuid);
        this.canEdit = isOwner || quest.getContributors().stream()
                .anyMatch(c -> c.getUuid().equals(myUuid) && c.canEdit());

        if (editing) {
            initEditMode();
        } else {
            initViewMode();
        }
    }

    // ===================== VIEW MODE =====================

    private void initViewMode() {
        int contentWidth = (int) (this.width * ScreenLayouts.CONTENT_WIDTH_RATIO);
        int contentX = (this.width - contentWidth) / 2;

        // Determine if we need metadata bar
        boolean hasCoords = quest.getCoordinates() != null;
        int metadataHeight = hasCoords ? 24 : 0;

        // Check if BlueMap link is available
        String bluemapUrl = BlueMapHelper.buildUrl(quest);
        boolean hasBluemap = bluemapUrl != null;

        // Bottom buttons
        int buttonsY = UIHelper.getBottomButtonY(this);
        int bottomMargin = ScreenLayouts.getBottomMarginSingleRow();

        // --- BUTTONS ---
        List<Text> buttonTexts = new ArrayList<>();
        buttonTexts.add(Text.literal("Edit"));
        buttonTexts.add(Text.literal("Delete"));
        buttonTexts.add(Text.literal("Close"));

        UIHelper.createButtonRow(this, buttonsY, buttonTexts, (index, x, width) -> {
            switch (index) {
                case 0 -> {
                    this.editButton = this.addDrawableChild(new DarkButtonWidget(
                            x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                            buttonTexts.get(0),
                            b -> enterEditMode()));
                    this.editButton.active = canEdit;
                }
                case 1 -> {
                    this.deleteButton = this.addDrawableChild(new DarkButtonWidget(
                            x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                            buttonTexts.get(1),
                            b -> confirmDelete()));
                    this.deleteButton.active = isOwner;
                }
                case 2 -> this.addDrawableChild(new DarkButtonWidget(
                        x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                        buttonTexts.get(2),
                        b -> this.close()));
            }
        });

        this.editButton.setTooltip(Tooltip.of(Text.literal("Edit this quest")));
        this.deleteButton.setTooltip(Tooltip.of(Text.literal("Permanently delete this quest")));

        // --- TITLE AREA ---
        int titleY = ScreenLayouts.TOP_MARGIN + 5;
        this.viewTitleArea = new ReadOnlyMultiLineTextFieldWidget(
                this.textRenderer, contentX, titleY,
                contentWidth, ScreenLayouts.TITLE_PANEL_HEIGHT,
                quest.getTitle(), 1, false
        );
        this.addSelectableChild(this.viewTitleArea);

        // --- CONTENT AREA ---
        int contentPanelY = ScreenLayouts.TOP_MARGIN + ScreenLayouts.TITLE_PANEL_HEIGHT + ScreenLayouts.PANEL_SPACING;
        int contentPanelBottom = this.height - bottomMargin - metadataHeight;
        if (metadataHeight > 0) {
            contentPanelBottom -= ScreenLayouts.PANEL_SPACING;
        }
        int contentPanelHeight = contentPanelBottom - contentPanelY;

        // Save bounds for click-to-edit detection
        this.contentAreaX = contentX;
        this.contentAreaY = contentPanelY;
        this.contentAreaWidth = contentWidth;
        this.contentAreaHeight = contentPanelHeight;

        List<RenderedLine> rendered = MarkdownRenderer.render(
                Objects.requireNonNullElse(quest.getContent(), ""));
        this.viewContentArea = new MarkdownWidget(
                this.textRenderer, contentX, contentPanelY,
                contentWidth, contentPanelHeight, rendered
        );
        this.viewContentArea.setCheckboxToggleListener((checkboxIndex, nowChecked) -> {
            String content = quest.getContent();
            if (content == null) return;
            String updated = toggleCheckbox(content, checkboxIndex, nowChecked);
            quest.setContent(updated);
            List<RenderedLine> rerendered = MarkdownRenderer.render(updated);
            this.viewContentArea.setContent(rerendered);
            PacketSender.saveQuest(quest.getId(), quest.getTitle(), updated,
                    quest.getCoordinates(), quest.isRegion(), quest.getCoordinates2(), quest.getMap());
            ClientCache.addOrUpdateMyQuest(quest);
        });
        this.addSelectableChild(this.viewContentArea);

        // --- BLUEMAP BUTTON (if applicable) ---
        if (hasCoords && hasBluemap) {
            int metaY = contentPanelBottom + ScreenLayouts.PANEL_SPACING;
            int bmBtnWidth = this.textRenderer.getWidth("View on BlueMap") + UIHelper.BUTTON_TEXT_PADDING * 2;
            bmBtnWidth = Math.max(bmBtnWidth, UIHelper.MIN_BUTTON_WIDTH);
            int bmBtnX = contentX + contentWidth - bmBtnWidth;

            final String url = bluemapUrl;
            this.addDrawableChild(new DarkButtonWidget(
                    bmBtnX, metaY, bmBtnWidth, 20,
                    Text.literal("View on BlueMap"),
                    b -> {
                        try {
                            Util.getOperatingSystem().open(URI.create(url));
                        } catch (Exception ignored) {
                        }
                    }
            ));
        }

        // --- Pre-compute view mode render strings ---
        String ownerInfo = "by " + quest.getOwnerName();
        if (quest.getVisibility() != null) {
            ownerInfo += "  [" + quest.getVisibility().name() + "]";
        }
        this.viewOwnerInfo = ownerInfo;
        this.viewCoordsText = hasCoords ? buildCoordsText() : null;
        this.viewMapText = (hasCoords && quest.getMap() != null) ? "Map: " + quest.getMap() : null;
    }

    private void renderViewMode(DrawContext context, int mouseX, int mouseY, float delta) {
        int contentWidth = (int) (this.width * ScreenLayouts.CONTENT_WIDTH_RATIO);
        int contentX = (this.width - contentWidth) / 2;
        int bottomMargin = ScreenLayouts.getBottomMarginSingleRow();

        boolean hasCoords = quest.getCoordinates() != null;
        int metadataHeight = hasCoords ? 24 : 0;

        // --- Screen title ---
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, Colors.TEXT_PRIMARY);

        // --- Owner & visibility info (pre-computed in initViewMode) ---
        int ownerInfoWidth = this.textRenderer.getWidth(viewOwnerInfo);
        context.drawText(this.textRenderer, viewOwnerInfo,
                contentX + contentWidth - ownerInfoWidth,
                ScreenLayouts.TOP_MARGIN - 2,
                Colors.TEXT_MUTED, false);

        // --- Title Panel ---
        UIHelper.drawPanel(context, contentX, ScreenLayouts.TOP_MARGIN,
                contentWidth, ScreenLayouts.TITLE_PANEL_HEIGHT);
        this.viewTitleArea.render(context, mouseX, mouseY, delta);

        // --- Content Panel ---
        int contentPanelY = ScreenLayouts.TOP_MARGIN + ScreenLayouts.TITLE_PANEL_HEIGHT + ScreenLayouts.PANEL_SPACING;
        int contentPanelBottom = this.height - bottomMargin - metadataHeight;
        if (metadataHeight > 0) {
            contentPanelBottom -= ScreenLayouts.PANEL_SPACING;
        }
        UIHelper.drawPanel(context, contentX, contentPanelY,
                contentWidth, contentPanelBottom - contentPanelY);
        this.viewContentArea.render(context, mouseX, mouseY, delta);

        // --- Metadata bar (pre-computed in initViewMode) ---
        if (hasCoords) {
            int metaY = contentPanelBottom + ScreenLayouts.PANEL_SPACING;
            UIHelper.drawPanel(context, contentX, metaY, contentWidth, metadataHeight);

            int textY = metaY + (metadataHeight - 8) / 2;
            int textX = contentX + 5;

            context.drawText(this.textRenderer, viewCoordsText, textX, textY, Colors.TEXT_MUTED, false);

            if (viewMapText != null) {
                int coordsWidth = this.textRenderer.getWidth(viewCoordsText);
                context.drawText(this.textRenderer, viewMapText,
                        textX + coordsWidth + 12, textY, Colors.TEXT_MUTED, false);
            }
        }
    }

    // ===================== EDIT MODE =====================

    private void initEditMode() {
        int contentWidth = (int) (this.width * ScreenLayouts.CONTENT_WIDTH_RATIO);
        int contentX = (this.width - contentWidth) / 2;

        // --- LAYOUT: optional fields panel + settings row (owner only) ---
        int optionalFieldsHeight = 30; // coords row height
        if (regionEnabled) {
            optionalFieldsHeight += 18; // corner 2 row
        }
        optionalFieldsHeight += 18; // map row
        optionalFieldsHeight += 8;  // padding

        int settingsRowHeight = isOwner ? 24 : 0;

        int bottomMargin = ScreenLayouts.getBottomMarginSingleRow();
        int buttonsY = UIHelper.getBottomButtonY(this);

        // --- BOTTOM BUTTONS: Save, Cancel ---
        List<Text> bottomButtonTexts = List.of(
                Text.literal("Save"),
                Text.literal("Cancel")
        );
        UIHelper.createButtonRow(this, buttonsY, bottomButtonTexts, (index, x, width) -> {
            switch (index) {
                case 0 -> this.addDrawableChild(new DarkButtonWidget(
                        x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                        bottomButtonTexts.get(0), b -> saveAndView()));
                case 1 -> this.addDrawableChild(new DarkButtonWidget(
                        x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                        bottomButtonTexts.get(1), b -> cancelEdit()));
            }
        });

        // --- TITLE FIELD ---
        int titleY = ScreenLayouts.TOP_MARGIN + 5;
        this.editTitleField = new MultiLineTextFieldWidget(
                this.textRenderer, contentX, titleY,
                contentWidth, ScreenLayouts.TITLE_PANEL_HEIGHT,
                quest.getTitle(), "Quest title...", 1, false
        );
        this.addSelectableChild(this.editTitleField);

        // Save original values for dirty tracking (only on first init)
        if (originalTitle == null) {
            originalTitle = quest.getTitle() != null ? quest.getTitle() : "";
            originalContent = quest.getContent() != null ? quest.getContent() : "";
        }

        // --- CONTENT FIELD ---
        int contentPanelY = ScreenLayouts.TOP_MARGIN + ScreenLayouts.TITLE_PANEL_HEIGHT + ScreenLayouts.PANEL_SPACING;
        int contentPanelBottom = this.height - bottomMargin
                - optionalFieldsHeight - ScreenLayouts.PANEL_SPACING
                - settingsRowHeight - (settingsRowHeight > 0 ? ScreenLayouts.PANEL_SPACING : 0);
        int contentPanelHeight = Math.max(30, contentPanelBottom - contentPanelY);

        int editorWidth = contentWidth;
        if (showFormattingHelp) {
            editorWidth -= FORMATTING_PANEL_WIDTH + ScreenLayouts.PANEL_SPACING;
        }

        this.editContentField = new MultiLineTextFieldWidget(
                this.textRenderer, contentX, contentPanelY,
                editorWidth, contentPanelHeight,
                quest.getContent() != null ? quest.getContent() : "",
                "Quest content...", Integer.MAX_VALUE, true
        );
        this.addSelectableChild(this.editContentField);

        // --- FORMATTING HELP TOGGLE BUTTON ---
        int helpBtnSize = 14;
        this.helpButton = this.addDrawableChild(new DarkButtonWidget(
                contentX + contentWidth - helpBtnSize - 2,
                titleY + (ScreenLayouts.TITLE_PANEL_HEIGHT - helpBtnSize) / 2,
                helpBtnSize, helpBtnSize,
                Text.literal("?"), b -> {
                    persistFieldValues();
                    showFormattingHelp = !showFormattingHelp;
                    this.clearAndInit();
                }
        ));
        this.helpButton.setTooltip(Tooltip.of(Text.literal("Toggle formatting reference")));

        // --- OPTIONAL FIELDS PANEL ---
        int optPanelY = contentPanelY + contentPanelHeight + ScreenLayouts.PANEL_SPACING;
        int rowY = optPanelY + 4;

        // Coords row
        buildCoordsRow(contentX, rowY);
        rowY += 18;

        // Region corner 2 row (if region enabled)
        if (regionEnabled) {
            buildCorner2Row(contentX, rowY);
            rowY += 18;
        }

        // Map row
        buildMapRow(contentX, rowY);

        // --- SETTINGS ROW (owner only) ---
        if (isOwner) {
            int settingsY = optPanelY + optionalFieldsHeight + ScreenLayouts.PANEL_SPACING;
            buildSettingsRow(contentX, settingsY);
        }

        this.setInitialFocus(this.editTitleField);
    }

    private void renderEditMode(DrawContext context, int mouseX, int mouseY, float delta) {
        int contentWidth = (int) (this.width * ScreenLayouts.CONTENT_WIDTH_RATIO);
        int contentX = (this.width - contentWidth) / 2;

        int optionalFieldsHeight = 30;
        if (regionEnabled) {
            optionalFieldsHeight += 18;
        }
        optionalFieldsHeight += 18;
        optionalFieldsHeight += 8;

        int settingsRowHeight = isOwner ? 24 : 0;
        int bottomMargin = ScreenLayouts.getBottomMarginSingleRow();

        // Screen title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, Colors.TEXT_PRIMARY);

        // Title Panel
        UIHelper.drawPanel(context, contentX, ScreenLayouts.TOP_MARGIN,
                contentWidth, ScreenLayouts.TITLE_PANEL_HEIGHT);
        this.editTitleField.render(context, mouseX, mouseY, delta);

        // Content Panel
        int contentPanelY = ScreenLayouts.TOP_MARGIN + ScreenLayouts.TITLE_PANEL_HEIGHT + ScreenLayouts.PANEL_SPACING;
        int contentPanelBottom = this.height - bottomMargin
                - optionalFieldsHeight - ScreenLayouts.PANEL_SPACING
                - settingsRowHeight - (settingsRowHeight > 0 ? ScreenLayouts.PANEL_SPACING : 0);
        int contentPanelHeight = Math.max(30, contentPanelBottom - contentPanelY);

        int editorWidth = contentWidth;
        if (showFormattingHelp) {
            editorWidth -= FORMATTING_PANEL_WIDTH + ScreenLayouts.PANEL_SPACING;
        }

        UIHelper.drawPanel(context, contentX, contentPanelY, editorWidth, contentPanelHeight);
        this.editContentField.render(context, mouseX, mouseY, delta);

        // Formatting help side panel
        if (showFormattingHelp) {
            int panelX = contentX + contentWidth - FORMATTING_PANEL_WIDTH;
            UIHelper.drawPanel(context, panelX, contentPanelY, FORMATTING_PANEL_WIDTH, contentPanelHeight);

            int textX = panelX + 5;
            int textY = contentPanelY + 5;
            int lineH = this.textRenderer.fontHeight + 2;

            context.drawText(this.textRenderer, "Formatting", textX, textY, Colors.TEXT_PRIMARY, false);
            textY += lineH + 2;

            String[][] help = {
                    {"**text**", "bold"},
                    {"*text*", "italic"},
                    {"~~text~~", "strikethrough"},
                    {"# Heading", "heading"},
                    {"- [ ] task", "checkbox"},
                    {"- [x] done", "checked"},
                    {"> text", "quote"},
                    {"[text](url)", "link"},
            };
            for (String[] entry : help) {
                context.drawText(this.textRenderer, entry[0], textX, textY, Colors.TEXT_MUTED, false);
                textY += lineH;
            }
        }

        // Optional fields panel
        int optPanelY = contentPanelY + contentPanelHeight + ScreenLayouts.PANEL_SPACING;
        UIHelper.drawPanel(context, contentX, optPanelY, contentWidth, optionalFieldsHeight);

        int rowY = optPanelY + 4;

        // Coords: editable X/Y/Z fields with labels
        int curX = contentX + 5;
        int coordFieldWidth = 50;
        int coordSpacing = 4;

        int xLabelWidth = this.textRenderer.getWidth("X:");
        context.drawText(this.textRenderer, "X:", curX, rowY + 3, Colors.TEXT_MUTED, false);
        curX += xLabelWidth + 2;
        if (this.coordXField != null) this.coordXField.render(context, mouseX, mouseY, delta);
        curX += coordFieldWidth + coordSpacing;

        int yLabelWidth = this.textRenderer.getWidth("Y:");
        context.drawText(this.textRenderer, "Y:", curX, rowY + 3, Colors.TEXT_MUTED, false);
        curX += yLabelWidth + 2;
        if (this.coordYField != null) this.coordYField.render(context, mouseX, mouseY, delta);
        curX += coordFieldWidth + coordSpacing;

        int zLabelWidth = this.textRenderer.getWidth("Z:");
        context.drawText(this.textRenderer, "Z:", curX, rowY + 3, Colors.TEXT_MUTED, false);
        if (this.coordZField != null) this.coordZField.render(context, mouseX, mouseY, delta);
        rowY += 18;

        // Corner 2 row (if region)
        if (regionEnabled) {
            int c2CurX = contentX + 5;
            context.drawText(this.textRenderer, "C2 ", c2CurX, rowY + 3, Colors.TEXT_MUTED, false);
            c2CurX += this.textRenderer.getWidth("C2 ");

            context.drawText(this.textRenderer, "X:", c2CurX, rowY + 3, Colors.TEXT_MUTED, false);
            c2CurX += this.textRenderer.getWidth("X:") + 2;
            if (this.coord2XField != null) this.coord2XField.render(context, mouseX, mouseY, delta);
            c2CurX += 50 + 4;

            context.drawText(this.textRenderer, "Y:", c2CurX, rowY + 3, Colors.TEXT_MUTED, false);
            c2CurX += this.textRenderer.getWidth("Y:") + 2;
            if (this.coord2YField != null) this.coord2YField.render(context, mouseX, mouseY, delta);
            c2CurX += 50 + 4;

            context.drawText(this.textRenderer, "Z:", c2CurX, rowY + 3, Colors.TEXT_MUTED, false);
            if (this.coord2ZField != null) this.coord2ZField.render(context, mouseX, mouseY, delta);
            rowY += 18;
        }

        // Map label + value
        context.drawText(this.textRenderer, "Map: ", contentX + 5, rowY + 3, Colors.TEXT_MUTED, false);
        int mapLabelWidth = this.textRenderer.getWidth("Map: ");
        String mapText = quest.getMap() != null ? quest.getMap() : "Any";
        int mapColor = quest.getMap() != null ? Colors.TEXT_PRIMARY : Colors.TEXT_DISABLED;
        context.drawText(this.textRenderer, mapText, contentX + 5 + mapLabelWidth, rowY + 3, mapColor, false);
    }

    // ===================== EDIT MODE ROWS =====================

    private void buildCoordsRow(int panelX, int rowY) {
        int btnWidth = 50;
        int btnHeight = 14;
        int fieldWidth = 50;
        int fieldHeight = 14;
        int spacing = 4;

        int curX = panelX + 5;

        // X label + field
        int xLabelWidth = this.textRenderer.getWidth("X:");
        curX += xLabelWidth + 2;
        CoordinatesData c = quest.getCoordinates();
        String xText = c != null ? String.valueOf((int) c.x()) : "";
        this.coordXField = new MultiLineTextFieldWidget(
                this.textRenderer, curX, rowY, fieldWidth, fieldHeight,
                xText, "X", 1, false);
        this.addSelectableChild(this.coordXField);
        curX += fieldWidth + spacing;

        // Y label + field
        int yLabelWidth = this.textRenderer.getWidth("Y:");
        curX += yLabelWidth + 2;
        String yText = c != null ? String.valueOf((int) c.y()) : "";
        this.coordYField = new MultiLineTextFieldWidget(
                this.textRenderer, curX, rowY, fieldWidth, fieldHeight,
                yText, "Y", 1, false);
        this.addSelectableChild(this.coordYField);
        curX += fieldWidth + spacing;

        // Z label + field
        int zLabelWidth = this.textRenderer.getWidth("Z:");
        curX += zLabelWidth + 2;
        String zText = c != null ? String.valueOf((int) c.z()) : "";
        this.coordZField = new MultiLineTextFieldWidget(
                this.textRenderer, curX, rowY, fieldWidth, fieldHeight,
                zText, "Z", 1, false);
        this.addSelectableChild(this.coordZField);
        curX += fieldWidth + spacing;

        // Set Pos button
        this.addDrawableChild(new DarkButtonWidget(
                curX, rowY, btnWidth, btnHeight,
                Text.literal("Set Pos"), b -> setPlayerPosition()));
        curX += btnWidth + spacing;

        // Region toggle button
        String regionText = regionEnabled ? "[x] Region" : "[ ] Region";
        int regionBtnWidth = this.textRenderer.getWidth(regionText) + UIHelper.BUTTON_TEXT_PADDING;
        this.regionButton = this.addDrawableChild(new DarkButtonWidget(
                curX, rowY, regionBtnWidth, btnHeight,
                Text.literal(regionText), b -> toggleRegion()));
        this.regionButton.setTooltip(Tooltip.of(Text.literal("Define a rectangular area with two corners")));
        curX += regionBtnWidth + spacing;

        // Clear button
        this.addDrawableChild(new DarkButtonWidget(
                curX, rowY, btnWidth, btnHeight,
                Text.literal("Clear"), b -> clearCoords()));
    }

    private void buildCorner2Row(int panelX, int rowY) {
        int fieldWidth = 50;
        int fieldHeight = 14;
        int spacing = 4;
        int btnWidth = 50;
        int btnHeight = 14;

        int curX = panelX + 5;

        // "C2" label
        int labelWidth = this.textRenderer.getWidth("C2 ");
        curX += labelWidth;

        CoordinatesData c2 = quest.getCoordinates2();

        // X field
        int xLabelWidth = this.textRenderer.getWidth("X:");
        curX += xLabelWidth + 2;
        String xText = c2 != null ? String.valueOf((int) c2.x()) : "";
        this.coord2XField = new MultiLineTextFieldWidget(
                this.textRenderer, curX, rowY, fieldWidth, fieldHeight,
                xText, "X", 1, false);
        this.addSelectableChild(this.coord2XField);
        curX += fieldWidth + spacing;

        // Y field
        int yLabelWidth = this.textRenderer.getWidth("Y:");
        curX += yLabelWidth + 2;
        String yText = c2 != null ? String.valueOf((int) c2.y()) : "";
        this.coord2YField = new MultiLineTextFieldWidget(
                this.textRenderer, curX, rowY, fieldWidth, fieldHeight,
                yText, "Y", 1, false);
        this.addSelectableChild(this.coord2YField);
        curX += fieldWidth + spacing;

        // Z field
        int zLabelWidth = this.textRenderer.getWidth("Z:");
        curX += zLabelWidth + 2;
        String zText = c2 != null ? String.valueOf((int) c2.z()) : "";
        this.coord2ZField = new MultiLineTextFieldWidget(
                this.textRenderer, curX, rowY, fieldWidth, fieldHeight,
                zText, "Z", 1, false);
        this.addSelectableChild(this.coord2ZField);
        curX += fieldWidth + spacing;

        // Set Pos button
        this.addDrawableChild(new DarkButtonWidget(
                curX, rowY, btnWidth, btnHeight,
                Text.literal("Set Pos"), b -> setCorner2Position()));
    }

    private void buildMapRow(int panelX, int rowY) {
        int labelWidth = this.textRenderer.getWidth("Map: ");
        String mapText = quest.getMap() != null ? quest.getMap() : "Any";
        int mapTextWidth = this.textRenderer.getWidth(mapText);
        int btnWidth = 50;
        int btnHeight = 14;
        int spacing = 4;

        int autoBtnX = panelX + labelWidth + mapTextWidth + spacing;
        this.addDrawableChild(new DarkButtonWidget(
                autoBtnX, rowY, btnWidth, btnHeight,
                Text.literal("Auto"), b -> autoMap()));

        int clearBtnX = autoBtnX + btnWidth + spacing;
        this.addDrawableChild(new DarkButtonWidget(
                clearBtnX, rowY, btnWidth, btnHeight,
                Text.literal("Clear"), b -> clearMap()));
    }

    private void buildSettingsRow(int panelX, int settingsY) {
        int btnHeight = 16;
        int spacing = 8;

        String visText = "Visibility: " + quest.getVisibility().name();
        int visBtnWidth = this.textRenderer.getWidth(visText) + UIHelper.BUTTON_TEXT_PADDING * 2;
        visBtnWidth = Math.max(visBtnWidth, UIHelper.MIN_BUTTON_WIDTH);
        this.visibilityButton = this.addDrawableChild(new DarkButtonWidget(
                panelX, settingsY, visBtnWidth, btnHeight,
                Text.literal(visText), b -> cycleVisibility()));

        String visTooltip = switch (quest.getVisibility()) {
            case PRIVATE -> "Only you can see this quest";
            case CLOSED -> "Visible to all, join by request";
            case OPEN -> "Visible to all, anyone can join";
        };
        this.visibilityButton.setTooltip(Tooltip.of(Text.literal(visTooltip)));

        int contribCount = quest.getContributors() != null ? quest.getContributors().size() : 0;
        String contribText = "Contributors (" + contribCount + ")";
        int contribBtnWidth = this.textRenderer.getWidth(contribText) + UIHelper.BUTTON_TEXT_PADDING * 2;
        contribBtnWidth = Math.max(contribBtnWidth, UIHelper.MIN_BUTTON_WIDTH);
        this.contributorsButton = this.addDrawableChild(new DarkButtonWidget(
                panelX + visBtnWidth + spacing, settingsY, contribBtnWidth, btnHeight,
                Text.literal(contribText), b -> openContributors()));
        this.contributorsButton.setTooltip(Tooltip.of(Text.literal("Manage who can view/edit this quest")));
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
            this.clearAndInit();
        }
    }

    private void setCorner2Position() {
        if (client != null && client.player != null) {
            persistFieldValues();
            quest.setCoordinates2(new CoordinatesData(
                    client.player.getX(), client.player.getY(), client.player.getZ()));
            this.clearAndInit();
        }
    }

    private void toggleRegion() {
        persistFieldValues();
        this.regionEnabled = !this.regionEnabled;
        quest.setRegion(this.regionEnabled);
        if (!this.regionEnabled) {
            quest.setCoordinates2(null);
        }
        this.clearAndInit();
    }

    private void clearCoords() {
        persistFieldValues();
        quest.setCoordinates(null);
        quest.setCoordinates2(null);
        this.regionEnabled = false;
        quest.setRegion(false);
        this.clearAndInit();
    }

    private void autoMap() {
        if (client != null && client.world != null) {
            quest.setMap(client.world.getRegistryKey().getValue().getPath());
            persistFieldValues();
            this.clearAndInit();
        }
    }

    private void clearMap() {
        quest.setMap(null);
        persistFieldValues();
        this.clearAndInit();
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
        this.clearAndInit();
    }

    private void openContributors() {
        persistFieldValues();
        open(new ContributorScreen(this, quest));
    }

    // ===================== MODE SWITCHING =====================

    private void enterEditMode() {
        if (!canEdit) return;
        this.editing = true;
        // Snapshot originals for dirty tracking when entering edit mode
        this.originalTitle = quest.getTitle() != null ? quest.getTitle() : "";
        this.originalContent = quest.getContent() != null ? quest.getContent() : "";
        this.clearAndInit();
    }

    private void cancelEdit() {
        if (isDirty()) {
            showConfirm(Text.literal("Discard unsaved changes?"), () -> {
                // Restore original values
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
            // For new quests, cancel means go back to parent
            if (this.client != null) {
                open(this.parent);
            }
        } else {
            this.editing = false;
            this.originalTitle = null;
            this.originalContent = null;
            this.clearAndInit();
        }
    }

    private void saveAndView() {
        persistFieldValues();
        // Optimistically add to cache so tick() won't auto-close
        // before the server's UPDATE_QUEST response arrives.
        ClientCache.addOrUpdateMyQuest(quest);
        PacketSender.saveQuest(
                quest.getId(),
                quest.getTitle(),
                quest.getContent(),
                quest.getCoordinates(),
                quest.isRegion(),
                quest.getCoordinates2(),
                quest.getMap()
        );
        // Also send visibility update if owner
        UUID myUuid = ClientSession.getEffectivePlayerUuid();
        if (quest.getOwnerUuid().equals(myUuid)) {
            PacketSender.updateVisibility(quest.getId(), quest.getVisibility());
        }
        // Switch to view mode
        this.editing = false;
        this.originalTitle = null;
        this.originalContent = null;
        this.clearAndInit();
    }

    // ===================== VIEW MODE ACTIONS =====================

    private void confirmDelete() {
        showConfirm(Text.literal("Delete quest \"" + quest.getTitle() + "\"?"), () -> {
            ClientCache.removeQuestById(quest.getId());
            PacketSender.deleteQuest(quest.getId());
            this.close();
        });
    }

    // ===================== SHARED HELPERS =====================


    private void persistFieldValues() {
        if (this.editTitleField != null) {
            quest.setTitle(this.editTitleField.getText());
        }
        if (this.editContentField != null) {
            quest.setContent(this.editContentField.getText());
        }
        if (this.coordXField != null) {
            try {
                double x = Double.parseDouble(coordXField.getText().trim());
                double y = Double.parseDouble(coordYField.getText().trim());
                double z = Double.parseDouble(coordZField.getText().trim());
                quest.setCoordinates(new CoordinatesData(x, y, z));
            } catch (NumberFormatException e) {
                if (coordXField.getText().trim().isEmpty()
                        && coordYField.getText().trim().isEmpty()
                        && coordZField.getText().trim().isEmpty()) {
                    quest.setCoordinates(null);
                }
            }
        }
        if (this.coord2XField != null && this.coord2YField != null && this.coord2ZField != null) {
            try {
                double x2 = Double.parseDouble(coord2XField.getText().trim());
                double y2 = Double.parseDouble(coord2YField.getText().trim());
                double z2 = Double.parseDouble(coord2ZField.getText().trim());
                quest.setCoordinates2(new CoordinatesData(x2, y2, z2));
            } catch (NumberFormatException e) {
                if (coord2XField.getText().trim().isEmpty()
                        && coord2YField.getText().trim().isEmpty()
                        && coord2ZField.getText().trim().isEmpty()) {
                    quest.setCoordinates2(null);
                }
            }
        }
    }

    private boolean isDirty() {
        String currentTitle = this.editTitleField != null ? this.editTitleField.getText() : quest.getTitle();
        String currentContent = this.editContentField != null ? this.editContentField.getText() : quest.getContent();
        if (currentTitle == null) currentTitle = "";
        if (currentContent == null) currentContent = "";
        return !currentTitle.equals(originalTitle) || !currentContent.equals(originalContent);
    }

    /**
     * Toggle the Nth checkbox in the raw markdown content.
     */
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
            // Verify it's part of a task list: preceded by "- "
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
        if (isNewQuest) {
            // New quests won't be in cache yet; skip auto-close
            return;
        }
        if (ClientCache.getQuestById(quest.getId()) == null) {
            this.close();
        }
    }

    // ===================== RENDERING =====================

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (editing) {
            renderEditMode(context, mouseX, mouseY, delta);
        } else {
            renderViewMode(context, mouseX, mouseY, delta);
        }
    }

    // ===================== INPUT DELEGATION =====================

    @Override
    public boolean mouseClicked(Click click, boolean simulated) {
        // Intercept help button clicks before title field can swallow them
        if (editing && helpButton != null && click.button() == 0) {
            double mx = click.x();
            double my = click.y();
            if (mx >= helpButton.getX() && mx < helpButton.getX() + helpButton.getWidth()
                    && my >= helpButton.getY() && my < helpButton.getY() + helpButton.getHeight()) {
                persistFieldValues();
                showFormattingHelp = !showFormattingHelp;
                this.clearAndInit();
                return true;
            }
        }
        // Let MarkdownWidget handle checkbox clicks first
        if (!editing && this.viewContentArea != null) {
            if (this.viewContentArea.mouseClicked(click, simulated)) {
                return true;
            }
        }
        // Click-to-edit for content area (only if not a checkbox click)
        if (!editing && canEdit) {
            double mx = click.x();
            double my = click.y();
            if (mx >= contentAreaX && mx < contentAreaX + contentAreaWidth
                    && my >= contentAreaY && my < contentAreaY + contentAreaHeight) {
                enterEditMode();
                return true;
            }
        }
        return super.mouseClicked(click, simulated);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (!editing && this.viewTitleArea != null && this.viewTitleArea.keyPressed(keyInput)) {
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (editing) {
            if (this.editContentField != null && this.editContentField.isMouseOver(mouseX, mouseY)) {
                return this.editContentField.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
            if (this.editTitleField != null && this.editTitleField.isMouseOver(mouseX, mouseY)) {
                return this.editTitleField.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
        } else {
            if (this.viewTitleArea != null && this.viewTitleArea.isMouseOver(mouseX, mouseY)) {
                return this.viewTitleArea.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
            if (this.viewContentArea != null && this.viewContentArea.isMouseOver(mouseX, mouseY)) {
                return this.viewContentArea.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
        }
        return false;
    }

}
