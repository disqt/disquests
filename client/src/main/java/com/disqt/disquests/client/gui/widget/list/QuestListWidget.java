package com.disqt.disquests.client.gui.widget.list;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.screen.MainScreen;
import com.disqt.disquests.client.hud.HudPinManager;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.Visibility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class QuestListWidget extends AbstractListWidget<QuestListWidget.QuestEntry> {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd MM yyyy");
    private static final Identifier PIN_ICON = Identifier.of("disquests", "icon/pin");
    private static final Identifier PIN_ACTIVE_ICON = Identifier.of("disquests", "icon/pin_active");

    private QuestSelectionListener selectionListener;

    public interface QuestSelectionListener {
        void onQuestSelected();
    }

    public QuestListWidget(Screen parent, MinecraftClient client, int top, int bottom, int itemHeight) {
        super(parent, client, top, bottom, itemHeight);
    }

    public void setSelectionListener(QuestSelectionListener listener) {
        this.selectionListener = listener;
    }

    public void setQuests(List<Quest> quests) {
        this.clearEntries();
        quests.forEach(quest -> this.addEntry(new QuestEntry(quest)));
    }

    public Quest getSelectedQuest() {
        QuestEntry entry = getSelectedOrNull();
        return entry != null ? entry.getQuest() : null;
    }

    @Override
    public void setSelected(QuestEntry entry) {
        super.setSelected(entry);
        if (selectionListener != null) {
            selectionListener.onQuestSelected();
        }
    }

    public class QuestEntry extends AbstractListWidget.Entry<QuestEntry> {
        private final Quest quest;
        private final String firstLine;
        private final String formattedDateTime;
        private final boolean isPinned;
        private final boolean isOwnedByPlayer;
        private final boolean hideContent;

        public QuestEntry(Quest quest) {
            this.quest = quest;

            // Check ownership and contributor status
            UUID playerUuid = ClientSession.getEffectivePlayerUuid();
            this.isOwnedByPlayer = playerUuid != null && playerUuid.equals(quest.getOwnerUuid());
            boolean isContributor = quest.getContributors().stream()
                    .anyMatch(c -> c.getUuid().equals(playerUuid));

            // Hide content for closed quests the player hasn't joined
            this.hideContent = quest.getVisibility() == Visibility.CLOSED
                    && !isOwnedByPlayer && !isContributor;

            // Content preview: first line of content
            if (hideContent) {
                this.firstLine = "";
            } else {
                String content = quest.getContent();
                if (content == null || content.isEmpty()) {
                    this.firstLine = "";
                } else {
                    String plain = MarkdownRenderer.stripToPlainText(content);
                    String[] lines = plain.split("\n");
                    this.firstLine = lines.length > 0 ? lines[0] : "";
                }
            }

            // Format last modified timestamp
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(quest.getLastModified()), ZoneId.systemDefault()
            );
            this.formattedDateTime = dateTime.format(DATE_TIME_FORMATTER);

            // Check if this quest is pinned
            this.isPinned = ClientSession.isPinned(quest.getId());
        }

        public Quest getQuest() {
            return this.quest;
        }

        @Override
        public void render(DrawContext context, int index, int mouseY, boolean hovered, float deltaTicks) {
            int entryX = this.getX();
            int entryY = this.getY();
            int entryWidth = this.getWidth();

            // --- Row 1: Title + Visibility badge + owner ---

            // Prepare visibility badge
            Text visibilityText = null;
            int visibilityColor = Colors.TEXT_PRIMARY;
            if (quest.getVisibility() != null) {
                switch (quest.getVisibility()) {
                    case PRIVATE -> {
                        visibilityText = Text.literal("Private").formatted(Formatting.LIGHT_PURPLE);
                    }
                    case CLOSED -> {
                        visibilityText = Text.literal("Closed").formatted(Formatting.YELLOW);
                    }
                    case OPEN -> {
                        visibilityText = Text.literal("Open").formatted(Formatting.GREEN);
                    }
                }
            }

            // Prepare "by [owner]" text (only for quests not owned by the player)
            Text ownerText = null;
            int ownerWidth = 0;
            if (!isOwnedByPlayer && quest.getOwnerName() != null) {
                ownerText = Text.literal(" by " + quest.getOwnerName()).formatted(Formatting.GRAY);
                ownerWidth = client.textRenderer.getWidth(ownerText);
            }

            // Calculate visibility badge width
            int visibilityWidth = 0;
            if (visibilityText != null) {
                visibilityWidth = client.textRenderer.getWidth(visibilityText);
            }

            // Calculate right-side content width (visibility + owner)
            int rightSideWidth = 0;
            if (visibilityText != null) rightSideWidth += visibilityWidth;
            if (ownerText != null) rightSideWidth += ownerWidth;
            if (rightSideWidth > 0) rightSideWidth += 7; // padding from edge

            // Calculate available title width
            int availableTitleWidth = entryWidth - 8; // base padding
            if (rightSideWidth > 0) {
                availableTitleWidth -= rightSideWidth;
            }

            // Draw title
            String truncatedTitle = client.textRenderer.trimToWidth(quest.getTitle(), availableTitleWidth);
            context.drawText(client.textRenderer, truncatedTitle,
                    entryX + 4, entryY + 4, Colors.TEXT_PRIMARY, false);

            // Draw visibility badge + owner on the right
            int rightX = entryX + entryWidth - 4;
            if (ownerText != null) {
                rightX -= ownerWidth;
                context.drawText(client.textRenderer, ownerText, rightX, entryY + 4, Colors.TEXT_MUTED, false);
            }
            if (visibilityText != null) {
                rightX -= visibilityWidth;
                context.drawText(client.textRenderer, visibilityText, rightX, entryY + 4, Colors.TEXT_PRIMARY, false);
            }

            // --- Row 2: Content preview ---
            if (hideContent) {
                context.drawText(client.textRenderer,
                        Text.literal("Request access to view").formatted(Formatting.ITALIC),
                        entryX + 4, entryY + 14, Colors.TEXT_MUTED, false);
            } else {
                String truncatedContent = client.textRenderer.trimToWidth(firstLine, entryWidth - 22);
                context.drawText(client.textRenderer, Text.literal(truncatedContent).formatted(Formatting.GRAY),
                        entryX + 4, entryY + 14, Colors.TEXT_MUTED, false);
            }

            // Pin icon (right side of row 2) -- GUI sprite
            int pinIconSize = 10;
            int pinIconX = entryX + entryWidth - pinIconSize - 4;
            int pinIconY = entryY + 14;
            Identifier pinIcon = isPinned ? PIN_ACTIVE_ICON : PIN_ICON;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, pinIcon, pinIconX, pinIconY, pinIconSize, pinIconSize);

            // --- Row 3: Last modified + map/coords ---
            context.drawText(client.textRenderer, "Last Modified: " + this.formattedDateTime,
                    entryX + 4, entryY + 24, Colors.TEXT_MUTED, false);

            // Map + coordinates (right-aligned on row 3)
            String locationStr = buildLocationString();
            if (!locationStr.isEmpty()) {
                int locationWidth = client.textRenderer.getWidth(locationStr);
                context.drawText(client.textRenderer, locationStr,
                        entryX + entryWidth - locationWidth - 4, entryY + 24, Colors.TEXT_MUTED, false);
            }

            // "Requested" indicator (right-aligned on row 3)
            if (ClientSession.isRequested(quest.getId())) {
                String requestedText = "Requested";
                int requestedWidth = client.textRenderer.getWidth(requestedText);
                context.drawText(client.textRenderer, requestedText,
                        entryX + entryWidth - requestedWidth - 4, entryY + 24, 0xFFCCCC44, false);
            }
        }

        private String buildLocationString() {
            if (quest.isRegion()) {
                String mapName = quest.getMap();
                if (mapName != null && !mapName.isEmpty()) {
                    return mapName + " (Region)";
                }
                return "Region";
            }

            CoordinatesData coords = quest.getCoordinates();
            if (coords == null) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            String mapName = quest.getMap();
            if (mapName != null && !mapName.isEmpty()) {
                sb.append(mapName);
            }

            // Format as "X:142 Z:-891"
            String coordStr = "X:" + (int) coords.x() + " Z:" + (int) coords.z();
            if (sb.length() > 0) {
                sb.append(" \u2022 "); // bullet separator
            }
            sb.append(coordStr);

            return sb.toString();
        }

        @Override
        public boolean mouseClicked(Click click, boolean simulated) {
            if (click.button() == 0) {
                // Check pin icon area (rightmost 20px, row 2)
                int entryX = this.getX();
                int entryY = this.getY();
                int entryWidth = this.getWidth();
                int pinHitX = entryX + entryWidth - 20;
                int pinIconY = entryY + 12;
                if (click.x() >= pinHitX && click.x() <= entryX + entryWidth
                        && click.y() >= pinIconY && click.y() <= pinIconY + 14) {
                    HudPinManager.toggle(quest.getId());
                    if (QuestListWidget.this.parentScreen instanceof MainScreen mainScreen) {
                        mainScreen.refreshAfterPinToggle();
                    }
                    return true;
                }
                QuestListWidget.this.setSelected(this);
                QuestListWidget.this.handleEntryClick(this);
                return true;
            }
            return false;
        }
    }
}
