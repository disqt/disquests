package com.disqt.disquests.client.gui.widget.list;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.Visibility;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class QuestListWidget extends AbstractListWidget<QuestListWidget.QuestEntry> {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd MM yyyy");

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

        public QuestEntry(Quest quest) {
            this.quest = quest;

            // Content preview: first line of content
            String content = quest.getContent();
            if (content == null || content.isEmpty()) {
                this.firstLine = "";
            } else {
                String plain = MarkdownRenderer.stripToPlainText(content);
                String[] lines = plain.split("\n");
                this.firstLine = lines.length > 0 ? lines[0] : "";
            }

            // Format last modified timestamp
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(quest.getLastModified()), ZoneId.systemDefault()
            );
            this.formattedDateTime = dateTime.format(DATE_TIME_FORMATTER);

            // Check if this quest is pinned
            this.isPinned = ClientSession.isPinned(quest.getId());

            // Check ownership
            UUID playerUuid = ClientSession.getEffectivePlayerUuid();
            this.isOwnedByPlayer = playerUuid != null && playerUuid.equals(quest.getOwnerUuid());
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

            // Draw pinned indicator + title
            String titlePrefix = isPinned ? "* " : "";
            String fullTitle = titlePrefix + quest.getTitle();
            String truncatedTitle = client.textRenderer.trimToWidth(fullTitle, availableTitleWidth);

            if (isPinned) {
                // Draw the star in gold
                int starWidth = client.textRenderer.getWidth("* ");
                context.drawText(client.textRenderer,
                        Text.literal("*").formatted(Formatting.GOLD),
                        entryX + 4, entryY + 4, Colors.TEXT_PRIMARY, false);
                // Draw rest of title
                String titlePart = truncatedTitle.length() > 2 ? truncatedTitle.substring(2) : "";
                context.drawText(client.textRenderer, titlePart,
                        entryX + 4 + starWidth, entryY + 4, Colors.TEXT_PRIMARY, false);
            } else {
                context.drawText(client.textRenderer, truncatedTitle,
                        entryX + 4, entryY + 4, Colors.TEXT_PRIMARY, false);
            }

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
            String truncatedContent = client.textRenderer.trimToWidth(firstLine, entryWidth - 8);
            context.drawText(client.textRenderer, Text.literal(truncatedContent).formatted(Formatting.GRAY),
                    entryX + 4, entryY + 14, Colors.TEXT_MUTED, false);

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
                QuestListWidget.this.setSelected(this);
                QuestListWidget.this.handleEntryClick(this);
                return true;
            }
            return false;
        }
    }
}
