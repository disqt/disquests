package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.ScreenLayouts;
import com.disqt.disquests.client.gui.helper.UIHelper;
import com.disqt.disquests.client.gui.widget.DarkButtonWidget;
import com.disqt.disquests.client.gui.widget.ReadOnlyMultiLineTextFieldWidget;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.CoordinatesData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ViewQuestScreen extends BaseScreen {

    private final Quest quest;

    private ReadOnlyMultiLineTextFieldWidget titleArea;
    private ReadOnlyMultiLineTextFieldWidget contentArea;

    // Buttons that need state tracking
    private DarkButtonWidget editButton;
    private DarkButtonWidget deleteButton;
    private DarkButtonWidget pinButton;

    public ViewQuestScreen(Screen parent, Quest quest) {
        super(Text.literal("View Quest"), parent);
        this.quest = quest;
    }

    @Override
    protected void init() {
        super.init();

        UUID myUuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
        boolean isOwner = quest.getOwnerUuid().equals(myUuid);
        boolean canEdit = isOwner || quest.getContributors().stream()
                .anyMatch(c -> c.getUuid().equals(myUuid) && c.canEdit());

        // --- LAYOUT ---
        int contentWidth = (int) (this.width * ScreenLayouts.CONTENT_WIDTH_RATIO);
        int contentX = (this.width - contentWidth) / 2;

        // Determine if we need metadata bar
        boolean hasCoords = quest.getCoordinates() != null;
        int metadataHeight = hasCoords ? 24 : 0;

        // Check if BlueMap link is available
        String bluemapUrl = buildBlueMapUrl();
        boolean hasBluemap = bluemapUrl != null;

        // Bottom buttons
        int buttonsY = UIHelper.getBottomButtonY(this);
        int bottomMargin = ScreenLayouts.getBottomMarginSingleRow();

        // --- BUTTONS ---
        List<Text> buttonTexts = new ArrayList<>();
        buttonTexts.add(Text.literal("Edit"));
        buttonTexts.add(Text.literal("Delete"));
        boolean isPinned = ClientSession.getPinnedQuestId() != null
                && ClientSession.getPinnedQuestId().equals(quest.getId());
        buttonTexts.add(Text.literal(isPinned ? "Unpin" : "Pin to HUD"));
        buttonTexts.add(Text.literal("Close"));

        UIHelper.createButtonRow(this, buttonsY, buttonTexts, (index, x, width) -> {
            switch (index) {
                case 0 -> {
                    this.editButton = this.addDrawableChild(new DarkButtonWidget(
                            x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                            buttonTexts.get(0),
                            b -> open(new EditQuestScreen(this.parent, quest))));
                    this.editButton.active = canEdit;
                }
                case 1 -> {
                    this.deleteButton = this.addDrawableChild(new DarkButtonWidget(
                            x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                            buttonTexts.get(1),
                            b -> confirmDelete()));
                    this.deleteButton.active = isOwner;
                }
                case 2 -> {
                    this.pinButton = this.addDrawableChild(new DarkButtonWidget(
                            x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                            buttonTexts.get(2),
                            b -> togglePin()));
                }
                case 3 -> this.addDrawableChild(new DarkButtonWidget(
                        x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                        buttonTexts.get(3),
                        b -> this.close()));
            }
        });

        // --- TITLE AREA ---
        int titleY = ScreenLayouts.TOP_MARGIN + 5;
        this.titleArea = new ReadOnlyMultiLineTextFieldWidget(
                this.textRenderer, contentX, titleY,
                contentWidth, ScreenLayouts.TITLE_PANEL_HEIGHT,
                quest.getTitle(), 1, false
        );
        this.addSelectableChild(this.titleArea);

        // --- CONTENT AREA ---
        int contentPanelY = ScreenLayouts.TOP_MARGIN + ScreenLayouts.TITLE_PANEL_HEIGHT + ScreenLayouts.PANEL_SPACING;
        int contentPanelBottom = this.height - bottomMargin - metadataHeight;
        if (metadataHeight > 0) {
            contentPanelBottom -= ScreenLayouts.PANEL_SPACING;
        }
        int contentPanelHeight = contentPanelBottom - contentPanelY;

        this.contentArea = new ReadOnlyMultiLineTextFieldWidget(
                this.textRenderer, contentX, contentPanelY,
                contentWidth, contentPanelHeight,
                quest.getContent() != null ? quest.getContent() : "",
                Integer.MAX_VALUE, true
        );
        this.addSelectableChild(this.contentArea);

        // --- BLUEMAP BUTTON (if applicable) ---
        if (hasCoords && hasBluemap) {
            int metaY = contentPanelBottom + ScreenLayouts.PANEL_SPACING;
            // Place BlueMap button at the right side of the metadata bar
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
    }

    // --- ACTIONS ---

    private void confirmDelete() {
        showConfirm(Text.literal("Delete quest \"" + quest.getTitle() + "\"?"), () -> {
            PacketSender.deleteQuest(quest.getId());
            this.close();
        });
    }

    private void togglePin() {
        PacketSender.pinQuest(quest.getId());
        UUID currentPinned = ClientSession.getPinnedQuestId();
        if (currentPinned != null && currentPinned.equals(quest.getId())) {
            ClientSession.setPinnedQuestId(null);
        } else {
            ClientSession.setPinnedQuestId(quest.getId());
        }
        this.clearAndInit();
    }

    // --- AUTO-CLOSE ---

    @Override
    public void tick() {
        super.tick();
        if (ClientCache.getQuestById(quest.getId()) == null) {
            this.close();
        }
    }

    // --- BLUEMAP URL ---

    private String buildBlueMapUrl() {
        if (!ClientSession.hasBluemap() || quest.getCoordinates() == null) return null;
        String base = ClientSession.getBluemapUrl();
        String map = quest.getMap() != null ? quest.getMap() : "world";
        CoordinatesData c = quest.getCoordinates();
        if (quest.isRegion() && quest.getCoordinates2() != null) {
            CoordinatesData c2 = quest.getCoordinates2();
            return String.format("%s/#%s:%.0f:%.0f:%.0f:50:0:0:0:0:flat",
                    base, map, (c.x() + c2.x()) / 2, (c.y() + c2.y()) / 2, (c.z() + c2.z()) / 2);
        }
        return String.format("%s/#%s:%.0f:%.0f:%.0f:50:0:0:0:0:flat",
                base, map, c.x(), c.y(), c.z());
    }

    // --- RENDERING ---

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int contentWidth = (int) (this.width * ScreenLayouts.CONTENT_WIDTH_RATIO);
        int contentX = (this.width - contentWidth) / 2;
        int bottomMargin = ScreenLayouts.getBottomMarginSingleRow();

        boolean hasCoords = quest.getCoordinates() != null;
        int metadataHeight = hasCoords ? 24 : 0;

        // --- Screen title ---
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, Colors.TEXT_PRIMARY);

        // --- Owner & visibility info ---
        String ownerInfo = "by " + quest.getOwnerName();
        if (quest.getVisibility() != null) {
            ownerInfo += "  [" + quest.getVisibility().name() + "]";
        }
        int ownerInfoWidth = this.textRenderer.getWidth(ownerInfo);
        context.drawText(this.textRenderer, ownerInfo,
                contentX + contentWidth - ownerInfoWidth,
                ScreenLayouts.TOP_MARGIN - 2,
                Colors.TEXT_MUTED, false);

        // --- Title Panel ---
        UIHelper.drawPanel(context, contentX, ScreenLayouts.TOP_MARGIN,
                contentWidth, ScreenLayouts.TITLE_PANEL_HEIGHT);
        this.titleArea.render(context, mouseX, mouseY, delta);

        // --- Content Panel ---
        int contentPanelY = ScreenLayouts.TOP_MARGIN + ScreenLayouts.TITLE_PANEL_HEIGHT + ScreenLayouts.PANEL_SPACING;
        int contentPanelBottom = this.height - bottomMargin - metadataHeight;
        if (metadataHeight > 0) {
            contentPanelBottom -= ScreenLayouts.PANEL_SPACING;
        }
        UIHelper.drawPanel(context, contentX, contentPanelY,
                contentWidth, contentPanelBottom - contentPanelY);
        this.contentArea.render(context, mouseX, mouseY, delta);

        // --- Metadata bar ---
        if (hasCoords) {
            int metaY = contentPanelBottom + ScreenLayouts.PANEL_SPACING;
            UIHelper.drawPanel(context, contentX, metaY, contentWidth, metadataHeight);

            String coordsText = buildCoordsText();
            int textY = metaY + (metadataHeight - 8) / 2;
            int textX = contentX + 5;

            context.drawText(this.textRenderer, coordsText, textX, textY, Colors.TEXT_MUTED, false);

            // Map name
            if (quest.getMap() != null) {
                String mapText = "Map: " + quest.getMap();
                int coordsWidth = this.textRenderer.getWidth(coordsText);
                context.drawText(this.textRenderer, mapText,
                        textX + coordsWidth + 12, textY, Colors.TEXT_MUTED, false);
            }
        }
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

    // --- INPUT DELEGATION ---

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (this.titleArea.keyPressed(keyInput)) return true;
        if (this.contentArea.keyPressed(keyInput)) return true;
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.titleArea.isMouseOver(mouseX, mouseY)) {
            return this.titleArea.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (this.contentArea.isMouseOver(mouseX, mouseY)) {
            return this.contentArea.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return false;
    }
}
