# owo-ui Full Screen Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate all remaining vanilla screens (ConfirmScreen, ConfigScreen, ContributorScreen, QuestScreen) to owo-ui, completing the full GUI migration.

**Architecture:** Each screen migrates independently. Simple screens first (ConfirmScreen, ConfigScreen) to build patterns, then ContributorScreen, then QuestScreen (most complex). All extend `DisquestsBaseScreen` with XML layouts. Custom widgets (MarkdownWidget, MultiLineTextFieldWidget) are ported to `BaseUIComponent` or wrapped via `UIComponents.wrapVanillaWidget()`. Delete `BaseScreen`, `DarkButtonWidget`, `UIHelper`, `ScreenLayouts` when no longer referenced.

**Tech Stack:** Java 21, Fabric 1.21.11, owo-lib 0.13.0+1.21.11, XML UI models

**Spec:** `docs/superpowers/specs/2026-03-20-owo-ui-migration-design.md`
**owo-ui Reference:** `docs/references/owo-ui.md`
**owo-ui Reviewer Agent:** `.claude/agents/owo-ui-reviewer.md`

**IMPORTANT:** After each screen migration, run the owo-ui-reviewer agent checklist and E2E tests with GLFW input simulation before proceeding.

---

## Phase 1: ConfirmScreen (trivial)

### Task 1: ConfirmScreen XML + Migration

**Files:**
- Create: `client/src/main/resources/assets/disquests/owo_ui/confirm_screen.xml`
- Rewrite: `client/.../gui/screen/ConfirmScreen.java`

**Current:** 57 lines. Centered panel with message + Yes/No buttons. Takes parent, message, onConfirm, onCancel.

**Target layout:**
```
root (vertical flow, VANILLA_TRANSLUCENT, centered)
└── panel (vertical flow, DARK_PANEL, content-sized, centered)
    ├── message-label (label, max-width, centered)
    ├── spacer
    └── button-row (horizontal flow, centered, gap 4)
        ├── btn-yes ("Yes")
        └── btn-no ("No")
```

- [ ] **Step 1:** Create `confirm_screen.xml` with the layout above
- [ ] **Step 2:** Rewrite `ConfirmScreen` extending `DisquestsBaseScreen`. Constructor takes `Screen parent, Text message, Runnable onConfirm, Runnable onCancel`. In `build()`, look up buttons by ID, wire `onPress`.
- [ ] **Step 3:** Verify `showConfirm()` in `BaseScreen` and other callers still work (they construct `ConfirmScreen` directly).
- [ ] **Step 4:** Build: `./gradlew :client:build`
- [ ] **Step 5:** Run E2E: `./gradlew :client:runClientGameTest`
- [ ] **Step 6:** Run owo-ui-reviewer checklist
- [ ] **Step 7:** Commit

---

## Phase 2: ConfigScreen (simple)

### Task 2: ConfigScreen XML + Migration

**Files:**
- Create: `client/src/main/resources/assets/disquests/owo_ui/config_screen.xml`
- Rewrite: `client/.../gui/screen/ConfigScreen.java`

**Current:** 72 lines. Title + slider (pinned width) + Save/Cancel buttons.

**Target layout:**
```
root (vertical flow, VANILLA_TRANSLUCENT, centered)
├── title-label ("Configuration", centered)
├── spacer
├── slider-row (horizontal flow, centered)
│   ├── slider-label ("Pinned Width:")
│   └── slider (discrete slider, min/max from DisquestsConfig)
├── spacer
└── button-row (horizontal flow, centered, gap 4)
    ├── btn-save ("Save")
    └── btn-cancel ("Cancel")
```

- [ ] **Step 1:** Create `config_screen.xml`
- [ ] **Step 2:** Rewrite `ConfigScreen` extending `DisquestsBaseScreen`. Use owo-ui `UIComponents.discreteSlider()` or `UIComponents.slider()`. Wire Save to persist + close, Cancel to close.
- [ ] **Step 3:** Build + E2E
- [ ] **Step 4:** Run owo-ui-reviewer checklist
- [ ] **Step 5:** Commit

---

## Phase 3: ContributorScreen (medium)

### Task 3: ContributorScreen XML + Migration

**Files:**
- Create: `client/src/main/resources/assets/disquests/owo_ui/contributor_screen.xml`
- Rewrite: `client/.../gui/screen/ContributorScreen.java`

**Current:** 227 lines. Title + pending requests section (amber header, Accept/Deny buttons) + contributor list (permission toggle, remove) + close button.

**Target layout:**
```
root (vertical flow, VANILLA_TRANSLUCENT, fill)
├── title-label ("Contributors", centered)
├── pending-section (vertical flow, content, hidden when empty)
│   ├── pending-header ("Pending Requests", amber)
│   ├── separator (1px amber line)
│   └── pending-list (vertical flow, gap 2)
│       └── [entries added programmatically: name + Accept + Deny]
├── contributor-list (scroll vertical, fill)
│   └── contributor-flow (vertical flow, gap 2)
│       └── [entries added programmatically: name + permission + remove]
├── empty-label ("No contributors yet", hidden when has contributors)
└── btn-close ("Close")
```

Each pending request entry and contributor entry can be a small helper component or built inline with horizontal FlowLayout containing label + buttons.

- [ ] **Step 1:** Create `contributor_screen.xml`
- [ ] **Step 2:** Rewrite `ContributorScreen`. Pending requests and contributors added programmatically in `build()`. Use zero-sizing to hide/show pending section.
- [ ] **Step 3:** Wire `respondToRequest()`, `togglePermission()`, `removeContributor()` with optimistic updates.
- [ ] **Step 4:** Build + E2E (testContributorScreenNoInvite should still pass)
- [ ] **Step 5:** Run owo-ui-reviewer checklist
- [ ] **Step 6:** Commit

---

## Phase 4: QuestScreen (complex -- break into sub-tasks)

QuestScreen is 1101 lines with two distinct modes. Migrate in stages.

### Task 4: QuestScreen XML Models (view + edit)

**Files:**
- Create: `client/src/main/resources/assets/disquests/owo_ui/quest_screen_view.xml`
- Create: `client/src/main/resources/assets/disquests/owo_ui/quest_screen_edit.xml`

Two separate XML models because view and edit are completely different layouts. The screen switches between them by swapping the UI model (or rebuilding).

**View mode layout:**
```
root (vertical flow, VANILLA_TRANSLUCENT, fill)
├── title-area (read-only text, fill width)
├── content-area (scroll vertical, fill)
│   └── [MarkdownWidget wrapped or ported to BaseUIComponent]
├── metadata-row (horizontal flow, when has coords)
│   ├── coords-label
│   ├── map-label
│   └── btn-bluemap ("View on BlueMap")
├── contributors-row (when has contributors)
│   └── contributors-label
└── button-row (horizontal flow, centered, gap 4)
    ├── btn-edit
    ├── btn-leave (conditional)
    ├── btn-delete
    └── btn-close
```

**Edit mode layout:**
```
root (vertical flow, VANILLA_TRANSLUCENT, fill)
├── title-field (text box, fill width, 1 line)
├── content-area (horizontal flow, fill)
│   ├── content-field (MultiLineTextFieldWidget wrapped, fill)
│   └── formatting-help (vertical flow, 160px, conditional)
├── coords-section (vertical flow)
│   ├── coords-row (X/Y/Z fields + Set Pos + Region + Clear)
│   ├── corner2-row (conditional, X/Y/Z + Set Pos)
│   └── map-row (map label + Auto + Clear)
├── settings-row (horizontal flow, owner only)
│   ├── btn-visibility
│   └── btn-contributors
└── button-row (horizontal flow, centered, gap 4)
    ├── btn-save
    └── btn-cancel
```

- [ ] **Step 1:** Create both XML files
- [ ] **Step 2:** Build to verify XML parses
- [ ] **Step 3:** Commit

### Task 5: Port MarkdownWidget to BaseUIComponent

**Files:**
- Modify: `client/.../gui/widget/MarkdownWidget.java`

Port from vanilla widget to owo-ui `BaseUIComponent`. The rendering logic stays the same -- change the inheritance, adapt `draw()` to use `OwoUIGraphics`, and update mouse handling to use `onMouseDown(Click, boolean)`.

Key changes:
- Extend `BaseUIComponent` instead of `DrawableHelper`/vanilla widget
- `draw(OwoUIGraphics, mouseX, mouseY, partialTicks, delta)` instead of `render(DrawContext, ...)`
- `onMouseDown(Click, boolean)` for checkbox and link clicks -- remember clicks are component-relative
- `determineHorizontalContentSize` / `determineVerticalContentSize` for sizing

- [ ] **Step 1:** Change class signature and imports
- [ ] **Step 2:** Adapt `draw()` method
- [ ] **Step 3:** Adapt mouse handling (relative coords!)
- [ ] **Step 4:** Build + verify
- [ ] **Step 5:** Commit

### Task 6: Wrap MultiLineTextFieldWidget for owo-ui

**Files:**
- Modify: `client/.../gui/widget/MultiLineTextFieldWidget.java` (minor)

MultiLineTextFieldWidget is 800+ lines with complex cursor/selection/undo logic. Full port is risky. Instead, use `UIComponents.wrapVanillaWidget()` to embed it in owo-ui layouts. It extends `ClickableWidget` which is what `wrapVanillaWidget` expects.

- [ ] **Step 1:** Verify `MultiLineTextFieldWidget` extends `ClickableWidget` (or equivalent)
- [ ] **Step 2:** Test wrapping: `UIComponents.wrapVanillaWidget(new MultiLineTextFieldWidget(...))` compiles
- [ ] **Step 3:** If wrapping doesn't work, create a thin `MultiLineTextFieldComponent extends BaseUIComponent` that delegates to the widget
- [ ] **Step 4:** Build + verify
- [ ] **Step 5:** Commit

### Task 7: Rewrite QuestScreen View Mode

**Files:**
- Rewrite: `client/.../gui/screen/QuestScreen.java`

Start with view mode only. The screen loads `quest_screen_view.xml`, looks up components, wires buttons. MarkdownWidget is added programmatically to the content scroll area.

- [ ] **Step 1:** Rewrite QuestScreen extending `DisquestsBaseScreen`
- [ ] **Step 2:** Implement `initViewMode()` using owo-ui
- [ ] **Step 3:** Wire checkbox toggle, BlueMap button, Leave/Delete/Close buttons
- [ ] **Step 4:** Build + E2E (testClickContentDoesNotEdit, testClosedQuestContentHidden, testCheckboxNotClickableWithoutPermission)
- [ ] **Step 5:** Run owo-ui-reviewer checklist
- [ ] **Step 6:** Commit

### Task 8: Rewrite QuestScreen Edit Mode

**Files:**
- Modify: `client/.../gui/screen/QuestScreen.java`

Add edit mode. When user clicks Edit, switch to `quest_screen_edit.xml` layout (or rebuild the component tree). MultiLineTextFieldWidget wrapped for content field. Coordinate fields use owo-ui `UIComponents.textBox()`. Formatting help panel toggles visibility.

- [ ] **Step 1:** Implement `initEditMode()` using owo-ui
- [ ] **Step 2:** Wire Set Pos, Region toggle, Clear, Auto (map), Visibility, Contributors buttons
- [ ] **Step 3:** Implement `saveAndView()`, `cancelEdit()`, `enterEditMode()` with mode switching
- [ ] **Step 4:** Build + E2E (testFormattingPanelToggle)
- [ ] **Step 5:** Run owo-ui-reviewer checklist
- [ ] **Step 6:** Commit

---

## Phase 5: Cleanup

### Task 9: Delete Old Vanilla Infrastructure

**Files to check and potentially delete:**
- `client/.../gui/screen/BaseScreen.java` -- replaced by `DisquestsBaseScreen`
- `client/.../gui/widget/DarkButtonWidget.java` -- replaced by owo-ui buttons
- `client/.../gui/widget/ReadOnlyMultiLineTextFieldWidget.java` -- replaced by owo-ui labels or wrapped
- `client/.../gui/helper/UIHelper.java` -- replaced by owo-ui layout
- `client/.../gui/helper/ScreenLayouts.java` -- replaced by owo-ui sizing
- `client/.../gui/widget/ToastOverlay.java` -- check if still used

For each file:
- [ ] **Step 1:** `grep -r "ClassName" client/src/main/java/ --include="*.java"` to check references
- [ ] **Step 2:** Delete if unreferenced
- [ ] **Step 3:** Build to verify
- [ ] **Step 4:** Commit

### Task 10: Update In-Progress Doc + E2E Tests

- [ ] **Step 1:** Run full E2E suite: `./gradlew :client:runClientGameTest`
- [ ] **Step 2:** Run `/simplify` on the full diff
- [ ] **Step 3:** Update `docs/in-progress-owo-ui-migration.md` or delete it (migration complete)
- [ ] **Step 4:** Update CLAUDE.md GUI migration state
- [ ] **Step 5:** Final commit + push

---

## Migration Order Summary

| Phase | Screen | Lines | Difficulty | Depends On |
|-------|--------|-------|-----------|------------|
| 1 | ConfirmScreen | 57 | Easy | -- |
| 2 | ConfigScreen | 72 | Easy | -- |
| 3 | ContributorScreen | 227 | Medium | -- |
| 4a | QuestScreen XML | -- | Medium | -- |
| 4b | MarkdownWidget port | ~260 | Medium | 4a |
| 4c | MultiLineTextFieldWidget wrap | ~10 | Easy | 4a |
| 4d | QuestScreen View Mode | ~400 | Hard | 4a, 4b |
| 4e | QuestScreen Edit Mode | ~500 | Hard | 4a, 4c, 4d |
| 5 | Cleanup | -- | Easy | All above |
