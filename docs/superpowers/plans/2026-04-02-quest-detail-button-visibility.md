# Quest Detail Button Visibility

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show only relevant action buttons on the QuestScreen detail view based on the player's relationship to the quest. Non-members see Join or Request (never Edit/Delete). Members see Edit and Delete (never Join/Request). View-only contributors see Edit greyed out with a tooltip. Non-owners never see Delete.

**Architecture:** Change `QuestScreen.buildViewMode()` to hide (remove from layout) buttons that don't apply, instead of showing them greyed out. Also merge Join/Request into a single `btn-interact` button (consistent with the MainScreen merge plan).

**Tech Stack:** owo-ui 0.13.0, Fabric 1.21.11

---

### Task 1: Hide Edit and Delete for non-members

**Problem:** Edit and Delete buttons are always shown (greyed out when not applicable). If the player can't join or hasn't joined yet, these buttons are noise.

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java:284-324`

- [ ] **Step 1: Hide Edit button for non-members**

Change the Edit button logic from:

```java
    ButtonComponent editBtn = root.childById(ButtonComponent.class, "btn-edit");
    editBtn.onPress(b -> enterEditMode());
    editBtn.active = canEdit;
```

to:

```java
    ButtonComponent editBtn = root.childById(ButtonComponent.class, "btn-edit");
    if (canJoinOrRequest) {
      // Non-member: hide Edit entirely
      buttonRow.removeChild(editBtn);
    } else {
      editBtn.onPress(b -> enterEditMode());
      if (canEdit) {
        editBtn.active = true;
      } else {
        editBtn.active = false;
        editBtn.tooltip(Text.translatable("gui.disquests.tooltip.view_only"));
      }
    }
```

- [ ] **Step 2: Hide Delete button for non-owners**

Change the Delete button logic from:

```java
    ButtonComponent deleteBtn = root.childById(ButtonComponent.class, "btn-delete");
    deleteBtn.onPress(b -> confirmDelete());
    deleteBtn.active = isOwner;
```

to:

```java
    ButtonComponent deleteBtn = root.childById(ButtonComponent.class, "btn-delete");
    if (isOwner) {
      deleteBtn.onPress(b -> confirmDelete());
    } else {
      buttonRow.removeChild(deleteBtn);
    }
```

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java
git commit -m "fix: hide Edit/Delete buttons for non-members on quest detail

Non-members only see Join or Request. Members see Edit (greyed
out with tooltip if view-only). Only the owner sees Delete."
```

---

### Task 2: Merge Join and Request into single button on QuestScreen

**Problem:** Like the MainScreen, Join and Request should be a single context-sensitive button on the quest detail view.

**Files:**
- Modify: `client/src/main/resources/assets/disquests/owo_ui/quest_screen_view.xml`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`

- [ ] **Step 1: Replace btn-join and btn-request with btn-interact in quest_screen_view.xml**

Find and remove both button elements:

```xml
                    <button id="btn-join">
                        ...
                    </button>
                    <button id="btn-request">
                        ...
                    </button>
```

Replace with a single button:

```xml
                    <button id="btn-interact">
                        <text>Join</text>
                        <sizing>
                            <horizontal method="fixed">70</horizontal>
                            <vertical method="fixed">20</vertical>
                        </sizing>
                    </button>
```

- [ ] **Step 2: Update QuestScreen.java button logic**

Replace the separate Join and Request button blocks (lines 288-307):

```java
    // Join button (OPEN quests, not yet a member)
    ButtonComponent joinBtn = root.childById(ButtonComponent.class, "btn-join");
    if (canJoinOrRequest && quest.getVisibility() == Visibility.OPEN) {
      joinBtn.onPress(b -> joinQuest());
    } else {
      buttonRow.removeChild(joinBtn);
    }

    // Request button (CLOSED quests, not yet a member)
    ButtonComponent requestBtn = root.childById(ButtonComponent.class, "btn-request");
    if (canJoinOrRequest && quest.getVisibility() == Visibility.CLOSED) {
      if (ClientSession.isRequested(quest.getId())) {
        requestBtn.active = false;
        requestBtn.setMessage(Text.translatable("gui.disquests.btn.requested"));
      } else {
        requestBtn.onPress(b -> requestAccess());
      }
    } else {
      buttonRow.removeChild(requestBtn);
    }
```

with:

```java
    // Interact button: Join (OPEN), Request (CLOSED), or hidden (member/owner)
    ButtonComponent interactBtn = root.childById(ButtonComponent.class, "btn-interact");
    if (canJoinOrRequest) {
      if (quest.getVisibility() == Visibility.OPEN) {
        interactBtn.setMessage(Text.translatable("gui.disquests.btn.join"));
        interactBtn.onPress(b -> joinQuest());
      } else if (quest.getVisibility() == Visibility.CLOSED) {
        if (ClientSession.isRequested(quest.getId())) {
          interactBtn.setMessage(Text.translatable("gui.disquests.btn.requested"));
          interactBtn.active = false;
          interactBtn.tooltip(Text.translatable("gui.disquests.tooltip.already_requested"));
        } else {
          interactBtn.setMessage(Text.translatable("gui.disquests.btn.request"));
          interactBtn.onPress(b -> requestAccess());
        }
      } else {
        // PRIVATE quest viewed by non-member (shouldn't normally happen)
        buttonRow.removeChild(interactBtn);
      }
    } else {
      buttonRow.removeChild(interactBtn);
    }
```

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/resources/assets/disquests/owo_ui/quest_screen_view.xml
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java
git commit -m "feat: merge Join/Request into single interact button on QuestScreen

Context-sensitive button shows Join for OPEN, Request for CLOSED,
Requested (greyed out with tooltip) if already requested."
```

---

### Task 3: Add tooltip translation key

**Files:**
- Modify: `client/src/main/resources/assets/disquests/lang/en_us.json5`
- Modify: `client/src/main/resources/assets/disquests/lang/fr_fr.json5`

- [ ] **Step 1: Add view_only tooltip key to en_us.json5**

Add in the tooltip section:

```json5
    "view_only": "You have view-only access",
```

- [ ] **Step 2: Add view_only tooltip key to fr_fr.json5**

```json5
    "view_only": "Tu as un accès en lecture seule",
```

Note: The `already_requested` key may already exist from the MainScreen merge plan. If so, skip adding it again.

- [ ] **Step 3: Build to verify**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/resources/assets/disquests/lang/en_us.json5
git add client/src/main/resources/assets/disquests/lang/fr_fr.json5
git commit -m "i18n: add view-only tooltip translation"
```

---

### Task 4: Update E2E tests

**Files:**
- Check: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/`

- [ ] **Step 1: Search for btn-join and btn-request references in tests**

Run: `grep -r "btn-join\|btn-request\|btn-edit\|btn-delete" client/src/testmod/`

Update any references to `btn-join` or `btn-request` to use `btn-interact`. For tests that assert Edit/Delete button visibility, update assertions to check for removal from layout rather than greyed-out state.

- [ ] **Step 2: Verify testmod compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run E2E tests**

Run: `./gradlew :client:runIntegrationTest`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add client/src/testmod/
git commit -m "test: update E2E tests for quest detail button visibility changes"
```
