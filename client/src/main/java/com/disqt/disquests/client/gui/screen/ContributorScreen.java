package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Contributor;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.ScreenLayouts;
import com.disqt.disquests.client.gui.helper.UIHelper;
import com.disqt.disquests.client.gui.widget.DarkButtonWidget;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.model.CollaborationRequestData;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.ContributorOp;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

public class ContributorScreen extends BaseScreen {

    private final Quest quest;

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

        int listStartY = ScreenLayouts.TOP_MARGIN + 10;
        int maxListBottom = buttonsY - 10;

        // --- PENDING REQUESTS SECTION ---
        List<CollaborationRequestData> pendingRequests = ClientCache.getPendingRequestsForQuest(quest.getId());
        int pendingHeight = pendingRequests.isEmpty() ? 0 : (14 + pendingRequests.size() * ROW_HEIGHT);

        if (!pendingRequests.isEmpty()) {
            int pendingListY = listStartY + 14; // after "Pending Requests" header
            for (int i = 0; i < pendingRequests.size(); i++) {
                int rowY = pendingListY + (i * ROW_HEIGHT);
                if (rowY + ROW_HEIGHT > maxListBottom) break;

                CollaborationRequestData req = pendingRequests.get(i);
                int btnY = rowY + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;

                // "Deny" button (rightmost)
                int denyBtnX = contentX + contentWidth - SMALL_BUTTON_WIDTH;
                final UUID requestId = req.id();
                final UUID questId = req.questId();

                this.addDrawableChild(new DarkButtonWidget(
                        denyBtnX, btnY, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT,
                        Text.literal("Deny").withColor(0xFFCC5555),
                        b -> respondToRequest(questId, requestId, false)));

                // "Accept" button (left of Deny)
                int acceptBtnWidth = Math.max(this.textRenderer.getWidth("Accept") + UIHelper.BUTTON_TEXT_PADDING, SMALL_BUTTON_WIDTH);
                int acceptBtnX = denyBtnX - 4 - acceptBtnWidth;

                this.addDrawableChild(new DarkButtonWidget(
                        acceptBtnX, btnY, acceptBtnWidth, BUTTON_HEIGHT,
                        Text.literal("Accept").withColor(0xFF55CC55),
                        b -> respondToRequest(questId, requestId, true)));
            }
        }

        // --- CONTRIBUTOR LIST --- (offset by pendingHeight)
        List<Contributor> contributors = quest.getContributors();
        int contributorListStartY = listStartY + pendingHeight;

        for (int i = 0; i < contributors.size(); i++) {
            int rowY = contributorListStartY + (i * ROW_HEIGHT);
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

    private void respondToRequest(UUID questId, UUID requestId, boolean accept) {
        PacketSender.respondCollaboration(requestId, accept);
        ClientCache.removePendingRequest(questId, requestId);
        ClientSession.setPendingRequestCount(
                Math.max(0, ClientSession.getPendingRequestCount() - 1));
        this.clearAndInit();
    }

    private void togglePermission(int index) {
        if (index < 0 || index >= quest.getContributors().size()) return;
        Contributor contrib = quest.getContributors().get(index);
        boolean newCanEdit = !contrib.canEdit();

        PacketSender.updateContributors(quest.getId(), List.of(
                new PacketCodec.ContributorOpEntry(ContributorOp.UPDATE, contrib.getUuid(), contrib.getName(), newCanEdit)
        ));

        // Optimistically replace contributor with updated canEdit
        quest.getContributors().set(index, new Contributor(
                new ContributorData(contrib.getUuid(), contrib.getName(), newCanEdit)));
        this.clearAndInit();
    }

    private void removeContributor(int index) {
        if (index < 0 || index >= quest.getContributors().size()) return;
        Contributor contrib = quest.getContributors().get(index);

        showConfirm(Text.literal("Remove " + contrib.getName() + " from contributors?"), () -> {
            PacketSender.updateContributors(quest.getId(), List.of(
                    new PacketCodec.ContributorOpEntry(ContributorOp.REMOVE, contrib.getUuid(), contrib.getName(), false)
            ));
            quest.getContributors().remove(index);
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

        int listStartY = ScreenLayouts.TOP_MARGIN + 10;
        int buttonsY = UIHelper.getBottomButtonY(this);
        int maxListBottom = buttonsY - 10;

        // --- PENDING REQUESTS SECTION ---
        List<CollaborationRequestData> pendingRequests = ClientCache.getPendingRequestsForQuest(quest.getId());
        int pendingHeight = pendingRequests.isEmpty() ? 0 : (14 + pendingRequests.size() * ROW_HEIGHT);

        if (!pendingRequests.isEmpty()) {
            // Section header
            context.drawText(this.textRenderer, Text.literal("Pending Requests").withColor(Colors.AMBER),
                    contentX + 5, listStartY + 2, Colors.AMBER, false);

            // Separator line
            context.fill(contentX, listStartY + 12, contentX + contentWidth, listStartY + 13, 0x44FFAA33);

            // Pending panel background
            int pendingListY = listStartY + 14;
            int pendingListHeight = Math.min(pendingRequests.size() * ROW_HEIGHT, maxListBottom - pendingListY);
            UIHelper.drawPanel(context, contentX, pendingListY, contentWidth, pendingListHeight);

            // Requester names
            for (int i = 0; i < pendingRequests.size(); i++) {
                int rowY = pendingListY + (i * ROW_HEIGHT);
                if (rowY + ROW_HEIGHT > maxListBottom) break;
                int textY = rowY + (ROW_HEIGHT - 8) / 2;
                String name = pendingRequests.get(i).requesterName();
                if (name == null) name = "Unknown";
                context.drawText(this.textRenderer, name,
                        contentX + 5, textY, Colors.TEXT_PRIMARY, false);
            }
        }

        // --- CONTRIBUTOR LIST --- (offset by pendingHeight)
        List<Contributor> contributors = quest.getContributors();
        int contributorListStartY = listStartY + pendingHeight;

        if (contributors.isEmpty() && pendingRequests.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("No contributors yet"),
                    this.width / 2, listStartY + 10, Colors.TEXT_DISABLED);
        } else if (!contributors.isEmpty()) {
            // Draw contributor list panel
            int listHeight = Math.min(contributors.size() * ROW_HEIGHT, maxListBottom - contributorListStartY);
            UIHelper.drawPanel(context, contentX, contributorListStartY, contentWidth, listHeight);

            // Draw each contributor name
            for (int i = 0; i < contributors.size(); i++) {
                int rowY = contributorListStartY + (i * ROW_HEIGHT);
                if (rowY + ROW_HEIGHT > maxListBottom) break;

                Contributor contrib = contributors.get(i);
                int textY = rowY + (ROW_HEIGHT - 8) / 2;
                context.drawText(this.textRenderer, contrib.getName(),
                        contentX + 5, textY, Colors.TEXT_PRIMARY, false);
            }
        }
    }
}
