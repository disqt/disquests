package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.gui.helper.Theme;
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

    // Static constants cached once across all instances
    private static final Text HIDDEN_CONTENT_TEXT = Text.literal("Request access to view").formatted(Formatting.ITALIC);
    private static final String REQUESTED_STR = "Requested";


    private final Quest quest;
    private final String firstLine;
    private final String formattedDateTime;
    private final boolean isOwnedByPlayer;
    private final boolean isContributor;
    private final boolean hideContent;

    // Cached visibility badge (fix 1)
    private final Text cachedVisibilityText;
    private final int cachedVisibilityWidth;

    // Cached owner text (fix 2)
    private final Text cachedOwnerText;
    private final int cachedOwnerWidth;

    // Cached location string (fix 4)
    private final String cachedLocationString;
    private final int cachedLocationWidth;

    // Cached "Last Modified: ..." string (fix 9)
    private final String cachedLastModifiedText;

    // Cached truncated content (fix 5) — depends on width known at draw time
    private Text cachedContentText;
    private int cachedContentWidth = -1;

    // Cached truncated title (fix 6) — depends on width known at draw time
    private String cachedTruncatedTitle;
    private int cachedTitleAvailableWidth = -1;

    // Cached pending badge (fix 8)
    private int lastPendingCount = -1;
    private Text cachedPendingText;
    private int cachedPendingWidth;

    private boolean selected = false;

    private Consumer<QuestEntryComponent> onClick;
    private Consumer<QuestEntryComponent> onDoubleClick;
    private Consumer<QuestEntryComponent> onPinToggle;

    public QuestEntryComponent(Quest quest) {
        this.quest = quest;

        UUID playerUuid = ClientSession.getEffectivePlayerUuid();
        this.isOwnedByPlayer = playerUuid != null && playerUuid.equals(quest.getOwnerUuid());
        this.isContributor = quest.getContributors().stream()
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
        this.cachedLastModifiedText = "Last Modified: " + formattedDateTime;

        // Cache visibility text + width (fix 1)
        Text visText = null;
        if (quest.getVisibility() != null) {
            visText = switch (quest.getVisibility()) {
                case PRIVATE -> Text.literal("Private").formatted(Formatting.LIGHT_PURPLE);
                case CLOSED  -> Text.literal("Closed").formatted(Formatting.YELLOW);
                case OPEN    -> Text.literal("Open").formatted(Formatting.GREEN);
            };
        }
        this.cachedVisibilityText = visText;
        if (visText != null) {
            this.cachedVisibilityWidth = MinecraftClient.getInstance().textRenderer.getWidth(visText);
        } else {
            this.cachedVisibilityWidth = 0;
        }

        // Cache owner text + width (fix 2)
        if (!isOwnedByPlayer && quest.getOwnerName() != null) {
            this.cachedOwnerText = Text.literal(" by " + quest.getOwnerName()).formatted(Formatting.GRAY);
            this.cachedOwnerWidth = MinecraftClient.getInstance().textRenderer.getWidth(cachedOwnerText);
        } else {
            this.cachedOwnerText = null;
            this.cachedOwnerWidth = 0;
        }

        // Cache location string + width (fix 4)
        this.cachedLocationString = buildLocationString();
        if (!cachedLocationString.isEmpty()) {
            this.cachedLocationWidth = MinecraftClient.getInstance().textRenderer.getWidth(cachedLocationString);
        } else {
            this.cachedLocationWidth = 0;
        }

        // Debug: log construction state
        LOGGER.debug("Created QuestEntryComponent: quest={}, owner={}, isOwned={}, visibility={}, pinned={}",
                quest.getTitle(), quest.getOwnerName(), isOwnedByPlayer,
                quest.getVisibility(), ClientSession.isPinned(quest.getId()));

        // Debug: subscribe to owo-ui event streams for dispatch tracing
        if (LOGGER.isDebugEnabled()) {
            this.mouseDown().subscribe((click, doubled) -> {
                LOGGER.debug("[EVENT mouseDown] quest={}, click=({}, {}), doubled={}", quest.getTitle(), click.x(), click.y(), doubled);
                return false;
            });
            this.mouseEnter().subscribe(() -> {
                LOGGER.debug("[EVENT mouseEnter] quest={}", quest.getTitle());
            });
        }
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
            context.fill(entryX, entryY, entryX + entryWidth, entryY + ENTRY_HEIGHT, Colors.ENTRY_SELECTED);
        } else if (hovered) {
            context.fill(entryX, entryY, entryX + entryWidth, entryY + ENTRY_HEIGHT, Colors.ENTRY_HOVER);
        }

        // Accent line (no-op when ACCENT_LINE_ACTIVE is transparent)
        if (Colors.ACCENT_LINE_ACTIVE != 0x00000000) {
            int stripeColor = selected ? Colors.ACCENT_LINE_ACTIVE : Colors.ACCENT_LINE_INACTIVE;
            context.fill(entryX, entryY, entryX + 2, entryY + ENTRY_HEIGHT, stripeColor);
        }

        // Inset bevel for selected entries
        if (selected && DisquestsConfig.getTheme() == Theme.INSET) {
            context.fill(entryX, entryY, entryX + entryWidth, entryY + 1, 0xFF0A0A0A);
            context.fill(entryX, entryY, entryX + 1, entryY + ENTRY_HEIGHT, 0xFF0A0A0A);
            context.fill(entryX, entryY + ENTRY_HEIGHT - 1, entryX + entryWidth, entryY + ENTRY_HEIGHT, 0xFF2A2A2A);
            context.fill(entryX + entryWidth - 1, entryY, entryX + entryWidth, entryY + ENTRY_HEIGHT, 0xFF2A2A2A);
        }

        // --- Row 1: Title + visibility badge + pending + owner ---

        int rightSideWidth = 0;
        if (cachedVisibilityText != null) rightSideWidth += cachedVisibilityWidth;
        if (cachedOwnerText != null) rightSideWidth += cachedOwnerWidth;
        if (rightSideWidth > 0) rightSideWidth += 7;

        int availableTitleWidth = entryWidth - 8;
        if (rightSideWidth > 0) availableTitleWidth -= rightSideWidth;

        // Cache truncated title (fix 6): recompute only when available width changes
        if (availableTitleWidth != cachedTitleAvailableWidth) {
            cachedTruncatedTitle = textRenderer.trimToWidth(quest.getTitle(), availableTitleWidth);
            cachedTitleAvailableWidth = availableTitleWidth;
        }
        context.drawText(textRenderer, cachedTruncatedTitle, entryX + 4, entryY + 4, Colors.TEXT_PRIMARY, false);

        int rightX = entryX + entryWidth - 4;
        if (cachedOwnerText != null) {
            rightX -= cachedOwnerWidth;
            context.drawText(textRenderer, cachedOwnerText, rightX, entryY + 4, Colors.TEXT_MUTED, false);
        }
        if (isOwnedByPlayer) {
            int pendingCount = ClientCache.getPendingCount(quest.getId());
            if (pendingCount > 0) {
                // Cache pending text (fix 8): rebuild only when count changes
                if (pendingCount != lastPendingCount) {
                    cachedPendingText = Text.literal(" (" + pendingCount + " pending)");
                    cachedPendingWidth = textRenderer.getWidth(cachedPendingText);
                    lastPendingCount = pendingCount;
                }
                rightX -= cachedPendingWidth;
                context.drawText(textRenderer, cachedPendingText, rightX, entryY + 4, Colors.AMBER, false);
            }
        }
        if (cachedVisibilityText != null) {
            rightX -= cachedVisibilityWidth;
            context.drawText(textRenderer, cachedVisibilityText, rightX, entryY + 4, Colors.TEXT_PRIMARY, false);
        }

        // --- Row 2: Content preview + pin icon ---
        if (hideContent) {
            // Static constant (fix 3)
            context.drawText(textRenderer, HIDDEN_CONTENT_TEXT,
                    entryX + 4, entryY + 14, Colors.TEXT_MUTED, false);
        } else {
            // Cache truncated content (fix 5): recompute only when available width changes
            int contentAvailWidth = entryWidth - 22;
            if (contentAvailWidth != cachedContentWidth) {
                String truncated = textRenderer.trimToWidth(firstLine, contentAvailWidth);
                cachedContentText = Text.literal(truncated).formatted(Formatting.GRAY);
                cachedContentWidth = contentAvailWidth;
            }
            context.drawText(textRenderer, cachedContentText,
                    entryX + 4, entryY + 14, Colors.TEXT_MUTED, false);
        }

        // Only show pin icon for quests the player is part of
        if (isOwnedByPlayer || isContributor) {
            int pinIconSize = 10;
            int pinIconX = entryX + entryWidth - pinIconSize - 4;
            int pinIconY = entryY + 14;
            boolean pinned = ClientSession.isPinned(quest.getId());
            Identifier pinIcon = pinned ? PIN_ACTIVE_ICON : PIN_ICON;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, pinIcon, pinIconX, pinIconY, pinIconSize, pinIconSize);
        }

        // --- Row 3: Last modified + location / requested ---
        context.drawText(textRenderer, cachedLastModifiedText,
                entryX + 4, entryY + 24, Colors.TEXT_MUTED, false);

        if (ClientSession.isRequested(quest.getId())) {
            // Width computed once via static constant (fix 7)
            int requestedWidth = textRenderer.getWidth(REQUESTED_STR);
            context.drawText(textRenderer, REQUESTED_STR,
                    entryX + entryWidth - requestedWidth - 4, entryY + 24, 0xFFCCCC44, false);
        } else {
            if (!cachedLocationString.isEmpty()) {
                context.drawText(textRenderer, cachedLocationString,
                        entryX + entryWidth - cachedLocationWidth - 4, entryY + 24, Colors.TEXT_MUTED, false);
            }
        }
    }

    // --- Interaction ---

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Disquests.QuestEntry");
    private static final long DOUBLE_CLICK_MS = 300;
    private static java.lang.ref.WeakReference<QuestEntryComponent> lastClickedEntry = new java.lang.ref.WeakReference<>(null);
    private static long lastClickTime;

    @Override
    public boolean onMouseDown(Click click, boolean doubled) {
        LOGGER.debug("onMouseDown called: click=({}, {}), button={}, quest={}", click.x(), click.y(), click.button(), quest.getTitle());
        if (click.button() != 0) return false;

        // owo-ui passes component-relative coordinates in Click
        double relX = click.x();
        double relY = click.y();
        LOGGER.debug("  relX={}, relY={}, width={}, pinArea=[{}-{}, 12-26], isPinned={}",
                relX, relY, this.width(), this.width() - 20, this.width(), ClientSession.isPinned(quest.getId()));

        // Pin icon hit area: rightmost 20px, row 2 (y+12 to y+26)
        if (relX >= this.width() - 20 && relX <= this.width()
                && relY >= 12 && relY <= 26) {
            if (!isOwnedByPlayer && !isContributor) {
                LOGGER.debug("  -> PIN CLICK blocked: player is not owner or contributor of {}", quest.getTitle());
                return true;
            }
            LOGGER.debug("  -> PIN CLICK detected, toggling pin for {}", quest.getTitle());
            HudPinManager.toggle(quest.getId());
            LOGGER.debug("  -> after toggle, isPinned={}", ClientSession.isPinned(quest.getId()));
            if (onPinToggle != null) onPinToggle.accept(this);
            return true;
        }

        // Per-component double-click: ignore vanilla's screen-level doubled flag,
        // only treat as double-click if the same entry was clicked twice
        long now = System.currentTimeMillis();
        boolean isDoubleClick = lastClickedEntry.get() == this && (now - lastClickTime) < DOUBLE_CLICK_MS;
        lastClickedEntry = new java.lang.ref.WeakReference<>(this);
        lastClickTime = now;

        if (isDoubleClick) {
            LOGGER.debug("  -> DOUBLE CLICK on {}", quest.getTitle());
            if (onDoubleClick != null) onDoubleClick.accept(this);
        } else {
            LOGGER.debug("  -> SINGLE CLICK (select) on {}", quest.getTitle());
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
