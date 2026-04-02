# Wiki-Link Enhancements Design

**Date:** 2026-04-01
**Status:** Designed, ready for planning
**Branch:** `fix/wiki-link-rendering` (builds on existing wiki-link bug fixes)

## Context

Wiki-links (`[[Quest Name]]`) render as amber underlined text in view mode and resolve correctly. Three enhancements improve the wiki-link experience: hover previews, edit mode syntax highlighting, and edit mode hover previews.

## Enhancement 1: Hover Preview Popup (View Mode)

**What:** When hovering over a wiki-link in view mode, show a floating preview card near the mouse cursor with the linked quest's title (bold), first 3 rendered markdown lines, and tag chips.

**Behavior:**
- Popup follows the mouse cursor with offset (+12px x, +16px y from cursor position)
- Clamps to screen edges to avoid overflow
- Dismisses immediately on mouse-out
- Clicking the wiki-link navigates to the quest (existing behavior, unchanged)
- If the linked quest is not in `ClientCache` (private/deleted), show nothing

**Dimensions:** Fixed 150px wide. Title + up to 3 rendered markdown lines + tag chips. Truncate with "..." if content exceeds 3 lines. The 3-line limit applies to rendered lines (after markdown processing), not source lines.

**Where:** `MarkdownWidget` already tracks `wikiLinkHitboxes` with UUIDs in `draw()`. On hover, look up the quest via `ClientCache.getQuestById(uuid)` and delegate rendering to `HoverPreviewRenderer`.

## Enhancement 2: Edit Mode Wiki-Link Styling

**What:** In the content text field (edit mode), `[[Quest Name]]` appears in amber with italics and underline instead of plain white. The double brackets are included in the styling.

**Behavior:**
- All `[[...]]` patterns are styled the same (amber + italics + underline)
- No live validation -- broken/unresolvable links are not distinguished. Validation happens on save in view mode.
- Unclosed `[[` without matching `]]` is not styled (treated as normal text)
- `[[]]` (empty brackets) is styled but produces no hover preview

**Where:** `MultiLineTextFieldWidget.render()` currently draws each display line as a single `textRenderer.draw()` call. Change to segmented drawing:

1. Before drawing a display line, scan for `[[` and `]]` pairs. Build a segment list: `[(start, end, isWikiLink), ...]`.
2. Cache segments per display line. Recompute only when text changes (dirty flag tied to `rebuildDisplayLines()`).
3. For each segment: normal segments draw with default style; wiki-link segments draw with amber (0xe8a86d) + italics + underline.
4. Track x offset per segment via `textRenderer.getWidth(segmentText)`.
5. Cursor and selection rendering are unchanged -- they operate on character positions, not styled segments.

**Wrapped lines:** Pattern scanning works on the logical line, then maps to display line character ranges via `displayToOffset`. Each portion of a wrapped wiki-link gets its segment styled.

## Enhancement 3: Edit Mode Hover Preview

**What:** Same hover preview as Enhancement 1, but in the edit mode text field.

**Behavior:** Identical to view mode preview (follows cursor, dismisses on mouse-out, fixed dimensions).

**Where:** `MultiLineTextFieldWidget` on mouse move:

1. Use `absoluteIndexFromMouse()` to get the character position under the cursor.
2. Check if it falls inside a `[[...]]` span (reuse the cached segment list from Enhancement 2).
3. Extract the quest title from between the brackets.
4. Search `ClientCache` by title match (no UUIDs available in edit mode -- titles were reverse-resolved). If no match or ambiguous, show nothing.
5. Call `HoverPreviewRenderer.draw()` with the quest and mouse coordinates.

## Architecture

### New: `HoverPreviewRenderer`

A stateless utility class in `client/gui/helper/`. Shared by both view mode (`MarkdownWidget`) and edit mode (`MultiLineTextFieldWidget`).

**Input:** `Quest`, mouse x/y, `OwoUIGraphics` context, screen dimensions (for edge clamping).

**Rendering:**
1. Dark panel background (matching the mod's theme)
2. Quest title in bold
3. Up to 3 rendered markdown lines via `MarkdownRenderer.render(quest.getContent())`, truncated
4. Tag chips using `TagColors.getColor()` for each tag
5. Positioned at cursor + offset, clamped to screen bounds

This is a raw `DrawContext` renderer, not an owo-ui component. It draws directly in the widget's `draw()`/`render()` method, guaranteeing it paints on top.

### Modified: `MarkdownWidget`

- Track mouse position in `draw()` (already receives graphics context)
- On each frame, check if mouse is inside any `wikiLinkHitbox`
- If hovered: look up quest via `ClientCache.getQuestById(uuid)`, call `HoverPreviewRenderer.draw()`
- Expose `isPreviewVisible()` for E2E testing

### Modified: `MultiLineTextFieldWidget`

- **Syntax highlighting:** Segmented draw in `render()` as described in Enhancement 2
- **Hover detection:** Mouse position tracking + `absoluteIndexFromMouse()` + segment list lookup
- **Preview rendering:** Call `HoverPreviewRenderer.draw()` when hovering a wiki-link segment
- Expose `isPreviewVisible()` for E2E testing

### Data Flow

| Mode | UUID source | Quest lookup |
|------|-------------|--------------|
| View | `wikiLinkHitboxes` (extracted from `RunCommand` click events) | `ClientCache.getQuestById(uuid)` |
| Edit | None (titles only, reverse-resolved) | `ClientCache` title search (imprecise, same as autocomplete) |

## Testing

### E2E Tests (`WikiLinkJourney.java`)

Hover preview and syntax highlighting are raw draw calls, not owo-ui components -- they cannot be queried via `childById`. Tests use debug `isPreviewVisible()` flags to verify the hover detection pipeline.

| Order | Test | Assertion |
|-------|------|-----------|
| 8 | Hover wiki-link in view mode | `isPreviewVisible()` true on MarkdownWidget |
| 9 | Move mouse away | `isPreviewVisible()` false |
| 10 | Enter edit mode, check content | `[[Link Target]]` text present, not broken by segmented rendering |
| 11 | Hover `[[Link Target]]` in edit mode | `isPreviewVisible()` true on MultiLineTextFieldWidget |

Visual correctness (amber color, italics, underline, preview content) is manual QA.

## Implementation Order

Enhancements 1 and 2 are independent and can be parallelized. Enhancement 3 depends on Enhancement 2's segment caching for hover detection. Tests depend on all three.

1. `HoverPreviewRenderer` (new shared utility)
2. Enhancement 2: edit mode syntax highlighting (segmented draw in `MultiLineTextFieldWidget`)
3. Enhancement 1: view mode hover preview (`MarkdownWidget` + `HoverPreviewRenderer`)
4. Enhancement 3: edit mode hover preview (`MultiLineTextFieldWidget` + `HoverPreviewRenderer`)
5. E2E tests
