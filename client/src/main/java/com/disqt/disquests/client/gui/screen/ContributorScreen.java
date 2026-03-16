package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.data.Contributor;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.ScreenLayouts;
import com.disqt.disquests.client.gui.helper.UIHelper;
import com.disqt.disquests.client.gui.widget.DarkButtonWidget;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.model.ContributorOp;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

public class ContributorScreen extends BaseScreen {

    private final Quest quest;
    private MultiLineTextFieldWidget inviteField;

    private static final int ROW_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 14;
    private static final int SMALL_BUTTON_WIDTH = 60;

    public ContributorScreen(Screen parent, Quest quest) {
        super(Text.literal("Contributors"), parent);
        this.quest = quest;
    }

    @Override
    protected void init() {
        super.init();

        int contentWidth = (int) (this.width * ScreenLayouts.CONTENT_WIDTH_RATIO);
        int contentX = (this.width - contentWidth) / 2;

        // --- CLOSE BUTTON ---
        int buttonsY = UIHelper.getBottomButtonY(this);
        List<Text> buttonTexts = List.of(Text.literal("Close"));
        UIHelper.createButtonRow(this, buttonsY, buttonTexts, (index, x, width) -> {
            this.addDrawableChild(new DarkButtonWidget(
                    x, buttonsY, width, UIHelper.BUTTON_HEIGHT,
                    buttonTexts.get(0), b -> this.close()));
        });

        // --- INVITE SECTION (bottom, above close button) ---
        int inviteRowY = buttonsY - UIHelper.OUTER_PADDING - 20;
        int inviteFieldWidth = contentWidth - SMALL_BUTTON_WIDTH - 8;
        this.inviteField = new MultiLineTextFieldWidget(
                this.textRenderer, contentX, inviteRowY,
                inviteFieldWidth, 18,
                "", "Player name...", 1, false
        );
        this.addSelectableChild(this.inviteField);

        this.addDrawableChild(new DarkButtonWidget(
                contentX + inviteFieldWidth + 4, inviteRowY,
                SMALL_BUTTON_WIDTH, 18,
                Text.literal("Invite"), b -> invitePlayer()));

        // --- CONTRIBUTOR LIST ---
        List<Contributor> contributors = quest.getContributors();
        int listStartY = ScreenLayouts.TOP_MARGIN + 10;
        int maxListBottom = inviteRowY - 10;

        for (int i = 0; i < contributors.size(); i++) {
            int rowY = listStartY + (i * ROW_HEIGHT);
            if (rowY + ROW_HEIGHT > maxListBottom) break; // don't overflow

            Contributor contrib = contributors.get(i);
            int btnY = rowY + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;

            // "Can Edit" / "View Only" toggle
            String permText = contrib.canEdit() ? "Can Edit" : "View Only";
            int permBtnWidth = Math.max(this.textRenderer.getWidth(permText) + UIHelper.BUTTON_TEXT_PADDING, SMALL_BUTTON_WIDTH);
            int permBtnX = contentX + contentWidth - SMALL_BUTTON_WIDTH - 4 - permBtnWidth - 4;

            final int contributorIndex = i;
            this.addDrawableChild(new DarkButtonWidget(
                    permBtnX, btnY, permBtnWidth, BUTTON_HEIGHT,
                    Text.literal(permText), b -> togglePermission(contributorIndex)));

            // "Remove" button
            int removeBtnX = contentX + contentWidth - SMALL_BUTTON_WIDTH;
            this.addDrawableChild(new DarkButtonWidget(
                    removeBtnX, btnY, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT,
                    Text.literal("Remove"), b -> removeContributor(contributorIndex)));
        }
    }

    // --- ACTIONS ---

    private void invitePlayer() {
        String playerName = this.inviteField.getText().trim();
        if (playerName.isEmpty()) return;

        PacketSender.updateContributors(quest.getId(), List.of(
                new PacketCodec.ContributorOpEntry(ContributorOp.ADD, null, playerName, false)
        ));

        // Clear field and refresh
        this.inviteField.setText("");
    }

    private void togglePermission(int index) {
        if (index < 0 || index >= quest.getContributors().size()) return;
        Contributor contrib = quest.getContributors().get(index);
        boolean newCanEdit = !contrib.canEdit();

        PacketSender.updateContributors(quest.getId(), List.of(
                new PacketCodec.ContributorOpEntry(ContributorOp.UPDATE, contrib.getUuid(), contrib.getName(), newCanEdit)
        ));

        // Refresh UI after a brief delay to allow server response
        // For now, just reinit the screen optimistically
        this.clearAndInit();
    }

    private void removeContributor(int index) {
        if (index < 0 || index >= quest.getContributors().size()) return;
        Contributor contrib = quest.getContributors().get(index);

        showConfirm(Text.literal("Remove " + contrib.getName() + " from contributors?"), () -> {
            PacketSender.updateContributors(quest.getId(), List.of(
                    new PacketCodec.ContributorOpEntry(ContributorOp.REMOVE, contrib.getUuid(), contrib.getName(), false)
            ));
            this.clearAndInit();
        });
    }

    // --- RENDERING ---

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int contentWidth = (int) (this.width * ScreenLayouts.CONTENT_WIDTH_RATIO);
        int contentX = (this.width - contentWidth) / 2;

        // Screen title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, Colors.TEXT_PRIMARY);

        List<Contributor> contributors = quest.getContributors();
        int listStartY = ScreenLayouts.TOP_MARGIN + 10;

        int buttonsY = UIHelper.getBottomButtonY(this);
        int inviteRowY = buttonsY - UIHelper.OUTER_PADDING - 20;
        int maxListBottom = inviteRowY - 10;

        if (contributors.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("No contributors yet"),
                    this.width / 2, listStartY + 10, Colors.TEXT_DISABLED);
        } else {
            // Draw contributor list panel
            int listHeight = Math.min(contributors.size() * ROW_HEIGHT, maxListBottom - listStartY);
            UIHelper.drawPanel(context, contentX, listStartY, contentWidth, listHeight);

            // Draw each contributor name
            for (int i = 0; i < contributors.size(); i++) {
                int rowY = listStartY + (i * ROW_HEIGHT);
                if (rowY + ROW_HEIGHT > maxListBottom) break;

                Contributor contrib = contributors.get(i);
                int textY = rowY + (ROW_HEIGHT - 8) / 2;
                context.drawText(this.textRenderer, contrib.getName(),
                        contentX + 5, textY, Colors.TEXT_PRIMARY, false);
            }
        }

        // Invite section label
        context.drawText(this.textRenderer, "Invite:", contentX,
                inviteRowY - 10, Colors.TEXT_MUTED, false);

        // Invite field panel
        UIHelper.drawPanel(context, this.inviteField.x - 2, this.inviteField.y,
                this.inviteField.width + 4, this.inviteField.height);
        this.inviteField.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.inviteField.isMouseOver(mouseX, mouseY)) {
            return this.inviteField.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
