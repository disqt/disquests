package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.data.Contributor;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.Messages;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.PacketCodec;
import com.disqt.disquests.common.model.ContributorData;
import com.disqt.disquests.common.model.ContributorOp;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * The people screen against a server older than quest trees: the old contributor list, where "can
 * edit / view only" and Remove apply at once, and the screen is reopened after each change.
 */
final class LegacyContributorList {

  private final ContributorScreen screen;
  private final FlowLayout root;
  private final FlowLayout list;
  private final Quest quest;
  private final boolean manages;

  LegacyContributorList(
      ContributorScreen screen, FlowLayout root, FlowLayout list, Quest quest, boolean manages) {
    this.screen = screen;
    this.root = root;
    this.list = list;
    this.quest = quest;
    this.manages = manages;
  }

  /** Removes the people screen's parts and lists the contributors (the owner is not listed). */
  void build() {
    root.removeChild(root.childById(FlowLayout.class, "add-person-row"));
    root.removeChild(root.childById(FlowLayout.class, ContributorScreen.HINTS_ID));
    root.removeChild(root.childById(FlowLayout.class, "people-buttons"));

    List<Contributor> contributors = quest.getContributors();
    LabelComponent emptyLabel = root.childById(LabelComponent.class, "empty-label");
    if (contributors.isEmpty() && ClientCache.getPendingRequestsForQuest(quest.getId()).isEmpty()) {
      // Show empty label, hide contributor scroll
      root.removeChild(root.childById(ScrollContainer.class, "contributor-scroll"));
      return;
    }
    root.removeChild(emptyLabel);

    for (int i = 0; i < contributors.size(); i++) {
      Contributor contrib = contributors.get(i);
      final int idx = i;

      FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
      row.verticalAlignment(VerticalAlignment.CENTER);
      row.gap(4);

      LabelComponent nameLabel = UIComponents.label(Component.literal(contrib.getName()));
      nameLabel.shadow(true);
      nameLabel.sizing(Sizing.fill(40), Sizing.content());
      row.child(nameLabel);

      // Spacer
      FlowLayout spacer = UIContainers.horizontalFlow(Sizing.fill(20), Sizing.fixed(1));
      row.child(spacer);

      // Permission toggle, only for the owner (the old server lets only the owner manage)
      if (manages) {
        Component permText =
            contrib.canEdit()
                ? Component.translatable("gui.disquests.contributor.can_edit")
                : Component.translatable("gui.disquests.contributor.view_only");
        ButtonComponent permBtn = UIComponents.button(permText, b -> togglePermission(idx));
        permBtn.sizing(Sizing.fixed(60), Sizing.fixed(14));
        row.child(permBtn);

        ButtonComponent removeBtn =
            UIComponents.button(
                Component.translatable("gui.disquests.btn.remove"), b -> removeContributor(idx));
        removeBtn.sizing(Sizing.fixed(60), Sizing.fixed(14));
        row.child(removeBtn);
      } else {
        LabelComponent role =
            UIComponents.label(Messages.toComponent(QuestPage.role(contrib.getRole())));
        role.color(Color.ofArgb(Colors.TEXT_MUTED));
        role.shadow(true);
        row.child(role);
      }

      list.child(row);
    }
  }

  private void togglePermission(int index) {
    if (index < 0 || index >= quest.getContributors().size()) return;
    if (screen.archivedSinceOpened()) return;
    Contributor contrib = quest.getContributors().get(index);
    boolean newCanEdit = !contrib.canEdit();

    PacketSender.updateContributors(
        quest.getId(),
        List.of(
            new PacketCodec.ContributorOpEntry(
                ContributorOp.UPDATE, contrib.getUuid(), contrib.getName(), newCanEdit)));

    quest
        .getContributors()
        .set(
            index,
            new Contributor(new ContributorData(contrib.getUuid(), contrib.getName(), newCanEdit)));
    rebuild();
  }

  private void removeContributor(int index) {
    if (index < 0 || index >= quest.getContributors().size()) return;
    Contributor contrib = quest.getContributors().get(index);

    screen.showConfirmOverlay(
        Component.translatable("gui.disquests.confirm.remove_contributor", contrib.getName()),
        () -> {
          if (screen.archivedSinceOpened()) return;
          PacketSender.updateContributors(
              quest.getId(),
              List.of(
                  new PacketCodec.ContributorOpEntry(
                      ContributorOp.REMOVE, contrib.getUuid(), contrib.getName(), false)));
          quest.getContributors().remove(index);
          rebuild();
        });
  }

  private void rebuild() {
    // Re-open the screen to rebuild the component tree (no draft on these servers)
    screen.navigateToScreen(new ContributorScreen(screen.getParentScreen(), quest));
  }
}
