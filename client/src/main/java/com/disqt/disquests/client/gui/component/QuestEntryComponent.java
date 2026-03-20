package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.hud.HudPinManager;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
import com.disqt.disquests.common.model.CoordinatesData;
import com.disqt.disquests.common.model.Visibility;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Consumer;

public class QuestEntryComponent extends BaseUIComponent {

    public static final int ENTRY_HEIGHT = 38;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd MM yyyy");
    private static final Identifier PIN_ICON = Identifier.of("disquests", "icon/pin");
    private static final Identifier PIN_ACTIVE_ICON = Identifier.of("disquests", "icon/pin_active");

    private final Quest quest;
    private final String firstLine;
    private final String formattedDateTime;
    private final boolean isOwnedByPlayer;
    private final boolean hideContent;

    private boolean selected = false;

    private Consumer<QuestEntryComponent> onClick;
    private Consumer<QuestEntryComponent> onDoubleClick;
    private Consumer<QuestEntryComponent> onPinToggle;

    public QuestEntryComponent(Quest quest) {
        this.quest = quest;

        UUID playerUuid = ClientSession.getEffectivePlayerUuid();
        this.isOwnedByPlayer = playerUuid != null && playerUuid.equals(quest.getOwnerUuid());
        boolean isContributor = quest.getContributors().stream()
                .anyMatch(c -> c.getUuid().equals(playerUuid));

        this.hideContent = quest.getVisibility() == Visibility.CLOSED
                && !isOwnedByPlayer && !isContributor;

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

        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(quest.getLastModified()), ZoneId.systemDefault()
        );
        this.formattedDateTime = dateTime.format(DATE_TIME_FORMATTER);
    }

    // --- Fluent setters ---

    public QuestEntryComponent onClick(Consumer<QuestEntryComponent> callback) {
        this.onClick = callback;
        return this;
    }

    public QuestEntryComponent onDoubleClick(Consumer<QuestEntryComponent> callback) {
        this.onDoubleClick = callback;
        return this;
    }

    public QuestEntryComponent onPinToggle(Consumer<QuestEntryComponent> callback) {
        this.onPinToggle = callback;
        return this;
    }

    public QuestEntryComponent selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public boolean isSelected() {
        return selected;
    }

    public Quest getQuest() {
        return quest;
    }

    // --- Size ---

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return 200;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return ENTRY_HEIGHT;
    }

    // --- Rendering ---

    @Override
    public void draw(OwoUIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        int entryX = this.x();
        int entryY = this.y();
        int entryWidth = this.width();

        // Hover / selection highlight
        boolean hovered = mouseX >= entryX && mouseX < entryX + entryWidth
                && mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT;

        if (selected) {
            context.fill(entryX, entryY, entryX + entryWidth, entryY + ENTRY_HEIGHT, 0x44FFFFFF);
        } else if (hovered) {
            context.fill(entryX, entryY, entryX + entryWidth, entryY + ENTRY_HEIGHT, 0x22FFFFFF);
        }

        // --- Row 1: Title + visibility badge + pending + owner ---

        Text visibilityText = null;
        if (quest.getVisibility() != null) {
            switch (quest.getVisibility()) {
                case PRIVATE ->
                        visibilityText = Text.literal("Private").formatted(Formatting.LIGHT_PURPLE);
                case CLOSED ->
                        visibilityText = Text.literal("Closed").formatted(Formatting.YELLOW);
                case OPEN ->
                        visibilityText = Text.literal("Open").formatted(Formatting.GREEN);
            }
        }

        Text ownerText = null;
        int ownerWidth = 0;
        if (!isOwnedByPlayer && quest.getOwnerName() != null) {
            ownerText = Text.literal(" by " + quest.getOwnerName()).formatted(Formatting.GRAY);
            ownerWidth = textRenderer.getWidth(ownerText);
        }

        int visibilityWidth = visibilityText != null ? textRenderer.getWidth(visibilityText) : 0;

        int rightSideWidth = 0;
        if (visibilityText != null) rightSideWidth += visibilityWidth;
        if (ownerText != null) rightSideWidth += ownerWidth;
        if (rightSideWidth > 0) rightSideWidth += 7;

        int availableTitleWidth = entryWidth - 8;
        if (rightSideWidth > 0) availableTitleWidth -= rightSideWidth;

        String truncatedTitle = textRenderer.trimToWidth(quest.getTitle(), availableTitleWidth);
        context.drawText(textRenderer, truncatedTitle, entryX + 4, entryY + 4, Colors.TEXT_PRIMARY, false);

        int rightX = entryX + entryWidth - 4;
        if (ownerText != null) {
            rightX -= ownerWidth;
            context.drawText(textRenderer, ownerText, rightX, entryY + 4, Colors.TEXT_MUTED, false);
        }
        if (isOwnedByPlayer) {
            int pendingCount = ClientCache.getPendingCount(quest.getId());
            if (pendingCount > 0) {
                Text pendingText = Text.literal(" (" + pendingCount + " pending)");
                int pendingWidth = textRenderer.getWidth(pendingText);
                rightX -= pendingWidth;
                context.drawText(textRenderer, pendingText, rightX, entryY + 4, Colors.AMBER, false);
            }
        }
        if (visibilityText != null) {
            rightX -= visibilityWidth;
            context.drawText(textRenderer, visibilityText, rightX, entryY + 4, Colors.TEXT_PRIMARY, false);
        }

        // --- Row 2: Content preview + pin icon ---
        if (hideContent) {
            context.drawText(textRenderer,
                    Text.literal("Request access to view").formatted(Formatting.ITALIC),
                    entryX + 4, entryY + 14, Colors.TEXT_MUTED, false);
        } else {
            String truncatedContent = textRenderer.trimToWidth(firstLine, entryWidth - 22);
            context.drawText(textRenderer,
                    Text.literal(truncatedContent).formatted(Formatting.GRAY),
                    entryX + 4, entryY + 14, Colors.TEXT_MUTED, false);
        }

        int pinIconSize = 10;
        int pinIconX = entryX + entryWidth - pinIconSize - 4;
        int pinIconY = entryY + 14;
        boolean pinned = ClientSession.isPinned(quest.getId());
        Identifier pinIcon = pinned ? PIN_ACTIVE_ICON : PIN_ICON;
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, pinIcon, pinIconX, pinIconY, pinIconSize, pinIconSize);

        // --- Row 3: Last modified + location / requested ---
        context.drawText(textRenderer, "Last Modified: " + formattedDateTime,
                entryX + 4, entryY + 24, Colors.TEXT_MUTED, false);

        if (ClientSession.isRequested(quest.getId())) {
            String requestedText = "Requested";
            int requestedWidth = textRenderer.getWidth(requestedText);
            context.drawText(textRenderer, requestedText,
                    entryX + entryWidth - requestedWidth - 4, entryY + 24, 0xFFCCCC44, false);
        } else {
            String locationStr = buildLocationString();
            if (!locationStr.isEmpty()) {
                int locationWidth = textRenderer.getWidth(locationStr);
                context.drawText(textRenderer, locationStr,
                        entryX + entryWidth - locationWidth - 4, entryY + 24, Colors.TEXT_MUTED, false);
            }
        }
    }

    // --- Interaction ---

    @Override
    public boolean onMouseDown(Click click, boolean doubled) {
        if (click.button() != 0) return false;

        // Pin icon hit area: rightmost 20px, row 2 (y+12 to y+26)
        int entryX = this.x();
        int entryY = this.y();
        int entryWidth = this.width();
        int pinHitX = entryX + entryWidth - 20;

        if (click.x() >= pinHitX && click.x() <= entryX + entryWidth
                && click.y() >= entryY + 12 && click.y() <= entryY + 26) {
            HudPinManager.toggle(quest.getId());
            if (onPinToggle != null) onPinToggle.accept(this);
            return true;
        }

        if (doubled) {
            if (onDoubleClick != null) onDoubleClick.accept(this);
        } else {
            if (onClick != null) onClick.accept(this);
        }
        return true;
    }

    // --- Helpers ---

    private String buildLocationString() {
        if (quest.isRegion()) {
            String mapName = quest.getMap();
            if (mapName != null && !mapName.isEmpty()) {
                return mapName + " (Region)";
            }
            return "Region";
        }

        CoordinatesData coords = quest.getCoordinates();
        if (coords == null) return "";

        StringBuilder sb = new StringBuilder();
        String mapName = quest.getMap();
        if (mapName != null && !mapName.isEmpty()) {
            sb.append(mapName);
        }

        String coordStr = "X:" + (int) coords.x() + " Z:" + (int) coords.z();
        if (sb.length() > 0) {
            sb.append(" \u2022 ");
        }
        sb.append(coordStr);

        return sb.toString();
    }
}
