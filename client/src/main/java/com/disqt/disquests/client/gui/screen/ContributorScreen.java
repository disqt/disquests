package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Contributor;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.model.CollaborationRequestData;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.ContributorOp;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class ContributorScreen extends DisquestsBaseScreen {

    private final Quest quest;

    public ContributorScreen(@Nullable Screen parent, Quest quest) {
        super(DataSource.asset(Identifier.of("disquests", "contributor_screen")), parent);
        this.quest = quest;
    }

    @Override
    protected void build(FlowLayout root) {
        List<CollaborationRequestData> pendingRequests = ClientCache.getPendingRequestsForQuest(quest.getId());
        List<Contributor> contributors = quest.getContributors();

        // --- Pending requests section ---
        FlowLayout pendingSection = root.childById(FlowLayout.class, "pending-section");
        FlowLayout pendingList = root.childById(FlowLayout.class, "pending-list");

        if (pendingRequests.isEmpty()) {
            // Hide pending section via zero-sizing
            pendingSection.sizing(Sizing.fixed(0), Sizing.fixed(0));
        } else {
            for (CollaborationRequestData req : pendingRequests) {
                String name = req.requesterName() != null ? req.requesterName() : "Unknown";
                final UUID requestId = req.id();
                final UUID questId = req.questId();

                FlowLayout row = io.wispforest.owo.ui.container.UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
                row.verticalAlignment(VerticalAlignment.CENTER);
                row.gap(4);

                LabelComponent nameLabel = UIComponents.label(Text.literal(name));
                nameLabel.shadow(true);
                nameLabel.sizing(Sizing.fill(40), Sizing.content());
                row.child(nameLabel);

                // Spacer to push buttons right
                FlowLayout spacer = io.wispforest.owo.ui.container.UIContainers.horizontalFlow(Sizing.fill(20), Sizing.fixed(1));
                row.child(spacer);

                ButtonComponent acceptBtn = UIComponents.button(
                        Text.literal("Accept").withColor(0xFF55CC55),
                        b -> respondToRequest(questId, requestId, true));
                acceptBtn.sizing(Sizing.fixed(60), Sizing.fixed(14));
                row.child(acceptBtn);

                ButtonComponent denyBtn = UIComponents.button(
                        Text.literal("Deny").withColor(0xFFCC5555),
                        b -> respondToRequest(questId, requestId, false));
                denyBtn.sizing(Sizing.fixed(60), Sizing.fixed(14));
                row.child(denyBtn);

                pendingList.child(row);
            }
        }

        // --- Contributor list ---
        FlowLayout contributorList = root.childById(FlowLayout.class, "contributor-list");
        LabelComponent emptyLabel = root.childById(LabelComponent.class, "empty-label");

        if (contributors.isEmpty() && pendingRequests.isEmpty()) {
            // Show empty label, hide contributor scroll
            root.childById(io.wispforest.owo.ui.container.ScrollContainer.class, "contributor-scroll")
                    .sizing(Sizing.fixed(0), Sizing.fixed(0));
        } else {
            // Hide empty label
            emptyLabel.sizing(Sizing.fixed(0), Sizing.fixed(0));

            for (int i = 0; i < contributors.size(); i++) {
                Contributor contrib = contributors.get(i);
                final int idx = i;

                FlowLayout row = io.wispforest.owo.ui.container.UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
                row.verticalAlignment(VerticalAlignment.CENTER);
                row.gap(4);

                LabelComponent nameLabel = UIComponents.label(Text.literal(contrib.getName()));
                nameLabel.shadow(true);
                nameLabel.sizing(Sizing.fill(40), Sizing.content());
                row.child(nameLabel);

                // Spacer
                FlowLayout spacer = io.wispforest.owo.ui.container.UIContainers.horizontalFlow(Sizing.fill(20), Sizing.fixed(1));
                row.child(spacer);

                // Permission toggle
                String permText = contrib.canEdit() ? "Can Edit" : "View Only";
                ButtonComponent permBtn = UIComponents.button(
                        Text.literal(permText),
                        b -> togglePermission(idx));
                permBtn.sizing(Sizing.fixed(60), Sizing.fixed(14));
                row.child(permBtn);

                // Remove button
                ButtonComponent removeBtn = UIComponents.button(
                        Text.literal("Remove"),
                        b -> removeContributor(idx));
                removeBtn.sizing(Sizing.fixed(60), Sizing.fixed(14));
                row.child(removeBtn);

                contributorList.child(row);
            }
        }

        // --- Close button ---
        root.childById(ButtonComponent.class, "btn-close")
                .onPress(b -> this.close());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // --- Actions ---

    private void respondToRequest(UUID questId, UUID requestId, boolean accept) {
        PacketSender.respondCollaboration(requestId, accept);
        ClientCache.removePendingRequest(questId, requestId);
        ClientSession.setPendingRequestCount(
                Math.max(0, ClientSession.getPendingRequestCount() - 1));
        rebuildUi();
    }

    private void togglePermission(int index) {
        if (index < 0 || index >= quest.getContributors().size()) return;
        Contributor contrib = quest.getContributors().get(index);
        boolean newCanEdit = !contrib.canEdit();

        PacketSender.updateContributors(quest.getId(), List.of(
                new PacketCodec.ContributorOpEntry(ContributorOp.UPDATE, contrib.getUuid(), contrib.getName(), newCanEdit)
        ));

        quest.getContributors().set(index, new Contributor(
                new ContributorData(contrib.getUuid(), contrib.getName(), newCanEdit)));
        rebuildUi();
    }

    private void removeContributor(int index) {
        if (index < 0 || index >= quest.getContributors().size()) return;
        Contributor contrib = quest.getContributors().get(index);

        if (this.client != null) {
            this.client.setScreen(new ConfirmScreen(this,
                    Text.literal("Remove " + contrib.getName() + " from contributors?"),
                    () -> {
                        PacketSender.updateContributors(quest.getId(), List.of(
                                new PacketCodec.ContributorOpEntry(ContributorOp.REMOVE, contrib.getUuid(), contrib.getName(), false)
                        ));
                        quest.getContributors().remove(index);
                        if (this.client != null) {
                            this.client.setScreen(this);
                        }
                    },
                    () -> {
                        if (this.client != null) {
                            this.client.setScreen(this);
                        }
                    }));
        }
    }

    private void rebuildUi() {
        // Re-open this screen to rebuild the component tree
        if (this.client != null) {
            this.client.setScreen(new ContributorScreen(this.parent, this.quest));
        }
    }
}
