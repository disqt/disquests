package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.DisquestsConfig;
import com.disqt.disquests.client.gui.helper.TagColors;
import com.disqt.disquests.client.gui.helper.Theme;
import com.disqt.disquests.client.hud.HudPinManager;
import com.disqt.disquests.client.markdown.MarkdownRenderer;
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

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class QuestEntryComponent extends BaseUIComponent {

    public static final int ENTRY_HEIGHT = 38;
    private static final Identifier PIN_ICON = Identifier.of("disquests", "icon/pin");
    private static final Identifier PIN_ACTIVE_ICON = Identifier.of("disquests", "icon/pin_active");

    // Static constants cached once across all instances
    private static final Text HIDDEN_CONTENT_TEXT = Text.literal("Request access to view").formatted(Formatting.ITALIC);


    private final Quest quest;
    private final String firstLine;
    private final boolean isOwnedByPlayer;
    private final boolean isContributor;
    private final boolean hideContent;

    // Cached visibility badge (fix 1)
    private final Text cachedVisibilityText;
    private final int cachedVisibilityWidth;

    // Cached owner text (fix 2)
    private final Text cachedOwnerText;
    private final int cachedOwnerWidth;

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
        this.isOwnedByPlayer = quest.isOwner(playerUuid);
        this.isContributor = quest.isContributor(playerUuid);
        this.hideContent = quest.isContentHidden(playerUuid);

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

        // --- Row 3: Tags ---
        List<String> tags = quest.getTags();
        if (tags == null || tags.isEmpty()) {
            context.drawText(textRenderer, Text.literal("no tags").formatted(Formatting.ITALIC),
                    entryX + 4, entryY + 24, Colors.TEXT_MUTED, false);
        } else {
            int tagX = entryX + 4;
            for (String tag : tags) {
                int bg = TagColors.getBackground(tag);
                int fg = TagColors.getForeground(tag);
                int tagWidth = textRenderer.getWidth(tag) + 6; // 3px padding each side
                if (tagX + tagWidth > entryX + entryWidth - 4) break; // don't overflow
                context.fill(tagX, entryY + 24, tagX + tagWidth, entryY + 34, bg);
                context.drawText(textRenderer, Text.literal(tag), tagX + 3, entryY + 25, fg, false);
                tagX += tagWidth + 3; // 3px gap
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

}
