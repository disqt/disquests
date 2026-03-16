package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.ScreenLayouts;
import com.disqt.disquests.client.gui.helper.UIHelper;
import com.disqt.disquests.client.gui.widget.DarkButtonWidget;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.Visibility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EditQuestScreen extends BaseScreen {

    private final Quest quest;

    private MultiLineTextFieldWidget titleField;
    private MultiLineTextFieldWidget contentField;

    // Track dirty state
    private String originalTitle;
    private String originalContent;

    // Optional fields state
    private boolean regionEnabled;

    public EditQuestScreen(Screen parent, Quest quest) {
        super(Text.literal("Edit Quest"), parent);
        this.quest = quest;
        this.regionEnabled = quest.isRegion();
    }

    public Quest getQuest() {
        return quest;
    }

    public MultiLineTextFieldWidget getTitleField() {
        return titleField;
    }

    public MultiLineTextFieldWidget getContentField() {
        return contentField;
    }

    @Override
    protected void init() {
        super.init();

        UUID myUuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
        boolean isOwner = quest.getOwnerUuid().equals(myUuid);

        int contentWidth = (int) (this.width * ScreenLayouts.CONTENT_WIDTH_RATIO);
        int contentX = (this.width - contentWidth) / 2;

        // --- LAYOUT: optional fields panel + settings row (owner only) ---
        // Calculate how much vertical space the optional fields take
        int optionalFieldsHeight = 30; // coords row height
        if (regionEnabled) {
            optionalFieldsHeight += 18; // corner 2 row
        }
        optionalFieldsHeight += 18; // map row
        optionalFieldsHeight += 8; // padding

        int settingsRowHeight = isOwner ? 24 : 0;

        int bottomMargin = ScreenLayouts.getBottomMarginSingleRow();
        int buttonsY = UIHelper.getBottomButtonY(this);

        // --- BOTTOM BUTTONS: Save, Close ---
        List<Text> bottomButtonTexts = List.of(
                Text.literal("Save"),
                Text.literal("Close")
        );
        UIHelper.createButtonRow(this, buttonsY, bottomButtonTexts, (index, x, width) -> {
            switch (index) {
                case 0 -> this.addDrawableChild(new DarkButtonWidget(
                        x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                        bottomButtonTexts.get(0), b -> saveAndView()));
                case 1 -> this.addDrawableChild(new DarkButtonWidget(
                        x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                        bottomButtonTexts.get(1), b -> tryClose()));
            }
        });

        // --- TITLE FIELD ---
        int titleY = ScreenLayouts.TOP_MARGIN + 5;
        this.titleField = new MultiLineTextFieldWidget(
                this.textRenderer, contentX, titleY,
                contentWidth, ScreenLayouts.TITLE_PANEL_HEIGHT,
                quest.getTitle(), "Quest title...", 1, false
        );
        this.addSelectableChild(this.titleField);

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

        this.contentField = new MultiLineTextFieldWidget(
                this.textRenderer, contentX, contentPanelY,
                contentWidth, contentPanelHeight,
                quest.getContent() != null ? quest.getContent() : "",
                "Quest content...", Integer.MAX_VALUE, true
        );
        this.addSelectableChild(this.contentField);

        // --- OPTIONAL FIELDS PANEL ---
        int optPanelY = contentPanelY + contentPanelHeight + ScreenLayouts.PANEL_SPACING;
        int rowY = optPanelY + 4;

        // Coords row
        buildCoordsRow(contentX, rowY, contentWidth);
        rowY += 18;

        // Region corner 2 row (if region enabled)
        if (regionEnabled) {
            buildCorner2Row(contentX, rowY, contentWidth);
            rowY += 18;
        }

        // Map row
        buildMapRow(contentX, rowY, contentWidth);
        rowY += 18;

        // --- SETTINGS ROW (owner only) ---
        if (isOwner) {
            int settingsY = optPanelY + optionalFieldsHeight + ScreenLayouts.PANEL_SPACING;
            buildSettingsRow(contentX, settingsY, contentWidth);
        }

        this.setInitialFocus(this.titleField);
    }

    // --- COORDS ROW ---

    private void buildCoordsRow(int panelX, int rowY, int panelWidth) {
        int labelWidth = this.textRenderer.getWidth("Coords: ");
        int btnWidth = 50;
        int btnHeight = 14;
        int spacing = 4;

        // "Set Pos" button
        int setPosX = panelX + labelWidth + getDisplayedCoordsWidth() + spacing;
        this.addDrawableChild(new DarkButtonWidget(
                setPosX, rowY, btnWidth, btnHeight,
                Text.literal("Set Pos"), b -> setPlayerPosition()));

        // "Region" toggle button
        String regionText = regionEnabled ? "[x] Region" : "[ ] Region";
        int regionBtnWidth = this.textRenderer.getWidth(regionText) + UIHelper.BUTTON_TEXT_PADDING;
        int regionBtnX = setPosX + btnWidth + spacing;
        this.addDrawableChild(new DarkButtonWidget(
                regionBtnX, rowY, regionBtnWidth, btnHeight,
                Text.literal(regionText), b -> toggleRegion()));

        // "Clear" button for coords
        int clearX = regionBtnX + regionBtnWidth + spacing;
        this.addDrawableChild(new DarkButtonWidget(
                clearX, rowY, btnWidth, btnHeight,
                Text.literal("Clear"), b -> clearCoords()));
    }

    private int getDisplayedCoordsWidth() {
        String coordsText = getCoordsDisplayText();
        return this.textRenderer.getWidth(coordsText);
    }

    private String getCoordsDisplayText() {
        CoordinatesData c = quest.getCoordinates();
        if (c == null) return "Not set";
        return String.format("X:%.0f Y:%.0f Z:%.0f", c.x(), c.y(), c.z());
    }

    // --- CORNER 2 ROW ---

    private void buildCorner2Row(int panelX, int rowY, int panelWidth) {
        int labelWidth = this.textRenderer.getWidth("Corner 2: ");
        int btnWidth = 50;
        int btnHeight = 14;
        int spacing = 4;

        String corner2Text;
        CoordinatesData c2 = quest.getCoordinates2();
        if (c2 != null) {
            corner2Text = String.format("X:%.0f Y:%.0f Z:%.0f", c2.x(), c2.y(), c2.z());
        } else {
            corner2Text = "Not set";
        }
        int corner2TextWidth = this.textRenderer.getWidth(corner2Text);

        int setBtnX = panelX + labelWidth + corner2TextWidth + spacing;
        this.addDrawableChild(new DarkButtonWidget(
                setBtnX, rowY, btnWidth, btnHeight,
                Text.literal("Set"), b -> setCorner2Position()));
    }

    // --- MAP ROW ---

    private void buildMapRow(int panelX, int rowY, int panelWidth) {
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

    // --- SETTINGS ROW (owner only) ---

    private void buildSettingsRow(int panelX, int settingsY, int panelWidth) {
        int btnHeight = 16;
        int spacing = 8;

        // Visibility cycle button
        String visText = "Visibility: " + quest.getVisibility().name();
        int visBtnWidth = this.textRenderer.getWidth(visText) + UIHelper.BUTTON_TEXT_PADDING * 2;
        visBtnWidth = Math.max(visBtnWidth, UIHelper.MIN_BUTTON_WIDTH);
        this.addDrawableChild(new DarkButtonWidget(
                panelX, settingsY, visBtnWidth, btnHeight,
                Text.literal(visText), b -> cycleVisibility()));

        // Contributors button
        int contribCount = quest.getContributors() != null ? quest.getContributors().size() : 0;
        String contribText = "Contributors (" + contribCount + ")";
        int contribBtnWidth = this.textRenderer.getWidth(contribText) + UIHelper.BUTTON_TEXT_PADDING * 2;
        contribBtnWidth = Math.max(contribBtnWidth, UIHelper.MIN_BUTTON_WIDTH);
        this.addDrawableChild(new DarkButtonWidget(
                panelX + visBtnWidth + spacing, settingsY, contribBtnWidth, btnHeight,
                Text.literal(contribText), b -> openContributors()));
    }

    // --- ACTIONS ---

    private void setPlayerPosition() {
        if (client != null && client.player != null) {
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
            quest.setCoordinates2(new CoordinatesData(
                    client.player.getX(), client.player.getY(), client.player.getZ()));
            this.clearAndInit();
        }
    }

    private void toggleRegion() {
        // Persist current field values before reinit
        persistFieldValues();
        this.regionEnabled = !this.regionEnabled;
        quest.setRegion(this.regionEnabled);
        if (!this.regionEnabled) {
            quest.setCoordinates2(null);
        }
        this.clearAndInit();
    }

    private void clearCoords() {
        quest.setCoordinates(null);
        quest.setCoordinates2(null);
        this.regionEnabled = false;
        quest.setRegion(false);
        persistFieldValues();
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

    private void saveAndView() {
        persistFieldValues();
        // Optimistically add to cache so ViewQuestScreen.tick() won't auto-close
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
        UUID myUuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
        if (quest.getOwnerUuid().equals(myUuid)) {
            PacketSender.updateVisibility(quest.getId(), quest.getVisibility());
        }
        open(new ViewQuestScreen(this.parent, quest));
    }

    private void persistFieldValues() {
        if (this.titleField != null) {
            quest.setTitle(this.titleField.getText());
        }
        if (this.contentField != null) {
            quest.setContent(this.contentField.getText());
        }
    }

    private boolean isDirty() {
        String currentTitle = this.titleField != null ? this.titleField.getText() : quest.getTitle();
        String currentContent = this.contentField != null ? this.contentField.getText() : quest.getContent();
        if (currentTitle == null) currentTitle = "";
        if (currentContent == null) currentContent = "";
        return !currentTitle.equals(originalTitle) || !currentContent.equals(originalContent);
    }

    private void tryClose() {
        if (isDirty()) {
            showConfirm(Text.literal("Discard unsaved changes?"), () -> {
                if (this.client != null) {
                    open(this.parent);
                }
            });
        } else {
            this.close();
        }
    }

    // --- AUTO-CLOSE ---

    @Override
    public void tick() {
        super.tick();
        // Only auto-close for existing quests (ones that were synced from the server)
        // New quests won't be in cache yet
        if (originalTitle != null && !originalTitle.equals("New Quest")) {
            if (ClientCache.getQuestById(quest.getId()) == null) {
                this.close();
            }
        }
    }

    // --- RENDERING ---

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        UUID myUuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
        boolean isOwner = quest.getOwnerUuid().equals(myUuid);

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
        this.titleField.render(context, mouseX, mouseY, delta);

        // Content Panel
        int contentPanelY = ScreenLayouts.TOP_MARGIN + ScreenLayouts.TITLE_PANEL_HEIGHT + ScreenLayouts.PANEL_SPACING;
        int contentPanelBottom = this.height - bottomMargin
                - optionalFieldsHeight - ScreenLayouts.PANEL_SPACING
                - settingsRowHeight - (settingsRowHeight > 0 ? ScreenLayouts.PANEL_SPACING : 0);
        int contentPanelHeight = Math.max(30, contentPanelBottom - contentPanelY);

        UIHelper.drawPanel(context, contentX, contentPanelY, contentWidth, contentPanelHeight);
        this.contentField.render(context, mouseX, mouseY, delta);

        // Optional fields panel
        int optPanelY = contentPanelY + contentPanelHeight + ScreenLayouts.PANEL_SPACING;
        UIHelper.drawPanel(context, contentX, optPanelY, contentWidth, optionalFieldsHeight);

        int rowY = optPanelY + 4;

        // Coords label + value
        context.drawText(this.textRenderer, "Coords: ", contentX + 5, rowY + 3, Colors.TEXT_MUTED, false);
        int labelWidth = this.textRenderer.getWidth("Coords: ");
        String coordsText = getCoordsDisplayText();
        int coordsColor = quest.getCoordinates() != null ? Colors.TEXT_PRIMARY : Colors.TEXT_DISABLED;
        context.drawText(this.textRenderer, coordsText, contentX + 5 + labelWidth, rowY + 3, coordsColor, false);
        rowY += 18;

        // Corner 2 row (if region)
        if (regionEnabled) {
            context.drawText(this.textRenderer, "Corner 2: ", contentX + 5, rowY + 3, Colors.TEXT_MUTED, false);
            int corner2LabelWidth = this.textRenderer.getWidth("Corner 2: ");
            CoordinatesData c2 = quest.getCoordinates2();
            String corner2Text = c2 != null ? String.format("X:%.0f Y:%.0f Z:%.0f", c2.x(), c2.y(), c2.z()) : "Not set";
            int c2Color = c2 != null ? Colors.TEXT_PRIMARY : Colors.TEXT_DISABLED;
            context.drawText(this.textRenderer, corner2Text, contentX + 5 + corner2LabelWidth, rowY + 3, c2Color, false);
            rowY += 18;
        }

        // Map label + value
        context.drawText(this.textRenderer, "Map: ", contentX + 5, rowY + 3, Colors.TEXT_MUTED, false);
        int mapLabelWidth = this.textRenderer.getWidth("Map: ");
        String mapText = quest.getMap() != null ? quest.getMap() : "Any";
        int mapColor = quest.getMap() != null ? Colors.TEXT_PRIMARY : Colors.TEXT_DISABLED;
        context.drawText(this.textRenderer, mapText, contentX + 5 + mapLabelWidth, rowY + 3, mapColor, false);

        // Settings row (owner only)
        if (isOwner) {
            int settingsY = optPanelY + optionalFieldsHeight + ScreenLayouts.PANEL_SPACING;
            // Just a subtle background for settings row is not needed since the buttons render themselves
        }
    }

    // --- INPUT DELEGATION ---

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.contentField.isMouseOver(mouseX, mouseY)) {
            return this.contentField.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (this.titleField.isMouseOver(mouseX, mouseY)) {
            return this.titleField.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void setFocused(Element focused) {
        super.setFocused(focused);
    }
}
