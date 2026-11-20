package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.Messages;
import com.disqt.disquests.client.network.PacketSender;
import com.disqt.disquests.common.model.Privatized;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * The owners' banners under the header of the reading view: "Set to Private on ..." with its two
 * buttons ({@code privatized-banner}, {@code btn-restore-visibility}, {@code btn-keep-private}),
 * and the line for a deleted prerequisite ({@code deleted-prerequisite-banner}). The texts come
 * from {@link QuestPage}.
 */
final class QuestBanners {

  static final String PRIVATIZED_ID = "privatized-banner";
  static final String RESTORE_ID = "btn-restore-visibility";
  static final String KEEP_PRIVATE_ID = "btn-keep-private";
  static final String DELETED_PREREQUISITE_ID = "deleted-prerequisite-banner";

  private QuestBanners() {}

  /** Adds the banners {@code actions} allows right after {@code header-row}. */
  static void attach(FlowLayout root, Quest quest, QuestPage.Actions actions) {
    FlowLayout column = root.childById(FlowLayout.class, "quest-column");
    int at = column.children().indexOf(root.childById(FlowLayout.class, "header-row")) + 1;
    if (actions.deletedPrerequisiteBanner()) {
      FlowLayout banner = banner(DELETED_PREREQUISITE_ID);
      banner.child(text(Messages.toComponent(QuestPage.deletedPrerequisiteBanner())));
      column.child(at, banner);
    }
    Privatized privatized = quest.getPrivatized();
    if (actions.privatizedBanner() && privatized != null) {
      column.child(at, privatizedBanner(quest, privatized));
    }
  }

  private static FlowLayout privatizedBanner(Quest quest, Privatized p) {
    FlowLayout banner = banner(PRIVATIZED_ID);
    String date = QuestScreen.formatDate(p.at());
    banner.child(text(Messages.toComponent(QuestPage.privatizedBanner(p, date))));

    ButtonComponent restore =
        UIComponents.button(
            Messages.toComponent(QuestPage.restoreVisibility(p.previous())), b -> {});
    restore.id(RESTORE_ID);
    ButtonComponent keep =
        UIComponents.button(Component.translatable(QuestPage.KEEP_PRIVATE), b -> {});
    keep.id(KEEP_PRIVATE_ID);
    // Both grey out after a press. The server answers with UPDATE_QUEST and the page is rebuilt,
    // without the banner, or with fresh enabled buttons if the banner is still due.
    for (ButtonComponent button : List.of(restore, keep)) {
      boolean restoring = button == restore;
      button.sizing(Sizing.content(), Sizing.fixed(20));
      button.onPress(
          ignored -> {
            restore.active(false);
            keep.active(false);
            PacketSender.resolvePrivatized(quest.getId(), restoring);
          });
    }
    // A text flow: in a narrow column the second button goes onto its own row
    FlowLayout buttons = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content());
    buttons.gap(4);
    buttons.child(restore);
    buttons.child(keep);
    banner.child(buttons);
    return banner;
  }

  private static FlowLayout banner(String id) {
    FlowLayout banner = UIContainers.verticalFlow(Sizing.fill(85), Sizing.content());
    banner.id(id);
    banner.padding(Insets.of(4));
    banner.gap(4);
    banner.margins(Insets.bottom(4));
    banner.surface(Surface.flat(0x60000000).and(Surface.outline(Colors.AMBER)));
    return banner;
  }

  private static LabelComponent text(Component text) {
    LabelComponent label = UIComponents.label(text);
    label.horizontalSizing(Sizing.fill(100));
    label.color(Color.ofArgb(Colors.AMBER));
    label.shadow(true);
    return label;
  }
}
