# Merge Join and Request into One Button

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the separate "Join" and "Request" buttons on the Quest Board tab with a single context-sensitive button that shows the appropriate action based on the selected quest's visibility and request state.

**Architecture:** Remove `btn-join` and `btn-request` from the XML layout and Java code. Add a single `btn-interact` button. The button label and behavior change based on the selected quest: "Join" for OPEN quests, "Request" for CLOSED quests, "Requested" (greyed out with tooltip) if already requested.

**Tech Stack:** owo-ui 0.13.0, Fabric 1.21.11, JUnit 5 E2E

---

### Task 1: Replace two buttons with one in XML

**Files:**
- Modify: `client/src/main/resources/assets/disquests/owo_ui/main_screen.xml`

- [ ] **Step 1: Remove btn-join and btn-request, add btn-interact**

In `main_screen.xml`, replace the two button elements:

```xml
                    <button id="btn-join">
                        <text>Join</text>
                        <sizing>
                            <horizontal method="fixed">70</horizontal>
                            <vertical method="fixed">20</vertical>
                        </sizing>
                    </button>
                    <button id="btn-request">
                        <text>Request</text>
                        <sizing>
                            <horizontal method="fixed">70</horizontal>
                            <vertical method="fixed">20</vertical>
                        </sizing>
                    </button>
```

with a single button:

```xml
                    <button id="btn-interact">
                        <text>Join</text>
                        <sizing>
                            <horizontal method="fixed">70</horizontal>
                            <vertical method="fixed">20</vertical>
                        </sizing>
                    </button>
```

- [ ] **Step 2: Commit**

```bash
git add client/src/main/resources/assets/disquests/owo_ui/main_screen.xml
git commit -m "refactor: replace btn-join and btn-request with single btn-interact"
```

---

### Task 2: Update MainScreen Java to use a single interact button

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`

- [ ] **Step 1: Replace field declarations**

Remove:

```java
  private ButtonComponent btnJoin;
  private ButtonComponent btnRequest;
```

Add:

```java
  private ButtonComponent btnInteract;
```

- [ ] **Step 2: Update build() — lookup and wiring**

Remove:

```java
    this.btnJoin = root.childById(ButtonComponent.class, "btn-join");
    this.btnRequest = root.childById(ButtonComponent.class, "btn-request");
```

```java
    this.btnJoin.onPress(btn -> joinQuest());
    this.btnRequest.onPress(btn -> requestAccess());
```

Add:

```java
    this.btnInteract = root.childById(ButtonComponent.class, "btn-interact");
    this.btnInteract.onPress(btn -> interactWithQuest());
```

- [ ] **Step 3: Add interactWithQuest method**

Add this method after the existing `requestAccess()` method:

```java
  private void interactWithQuest() {
    Quest sel = getSelectedQuest();
    if (sel == null) return;
    if (sel.getVisibility() == Visibility.OPEN) {
      joinQuest();
    } else if (sel.getVisibility() == Visibility.CLOSED) {
      requestAccess();
    }
  }
```

- [ ] **Step 4: Update tab switching logic**

In the tab switching section (around line 170-187), replace:

```java
    btnJoin.active(!isMyQuests);
    btnRequest.active(!isMyQuests);
```

```java
    actionRow.removeChild(btnJoin);
    actionRow.removeChild(btnRequest);
    if (isMyQuests) {
      actionRow.child(0, btnNewQuest);
    } else {
      actionRow.child(0, btnRequest);
      actionRow.child(0, btnJoin);
    }
```

with:

```java
    btnInteract.active(!isMyQuests);
```

```java
    actionRow.removeChild(btnInteract);
    actionRow.removeChild(btnNewQuest);
    if (isMyQuests) {
      actionRow.child(0, btnNewQuest);
    } else {
      actionRow.child(0, btnInteract);
    }
```

- [ ] **Step 5: Update updateActionButtons()**

Replace:

```java
      btnJoin.active(hasSelection && selected.getVisibility() == Visibility.OPEN);
      btnRequest.active(hasSelection && selected.getVisibility() == Visibility.CLOSED);
      if (selected != null && ClientSession.isRequested(selected.getId())) {
        markRequestButtonAsRequested();
      }
```

with:

```java
      updateInteractButton(selected);
```

Add a new method:

```java
  private void updateInteractButton(Quest selected) {
    if (selected == null) {
      btnInteract.active(false);
      btnInteract.setMessage(Text.translatable("gui.disquests.btn.join"));
      btnInteract.tooltip(null);
      return;
    }
    if (ClientSession.isRequested(selected.getId())) {
      btnInteract.setMessage(Text.translatable("gui.disquests.btn.requested"));
      btnInteract.active(false);
      btnInteract.tooltip(Text.translatable("gui.disquests.tooltip.already_requested"));
    } else if (selected.getVisibility() == Visibility.OPEN) {
      btnInteract.setMessage(Text.translatable("gui.disquests.btn.join"));
      btnInteract.active(true);
      btnInteract.tooltip(null);
    } else if (selected.getVisibility() == Visibility.CLOSED) {
      btnInteract.setMessage(Text.translatable("gui.disquests.btn.request"));
      btnInteract.active(true);
      btnInteract.tooltip(null);
    } else {
      btnInteract.active(false);
      btnInteract.tooltip(null);
    }
  }
```

- [ ] **Step 6: Update markRequestButtonAsRequested()**

Change:

```java
  private void markRequestButtonAsRequested() {
    btnRequest.setMessage(Text.translatable("gui.disquests.btn.requested"));
    btnRequest.active(false);
  }
```

to:

```java
  private void markRequestButtonAsRequested() {
    btnInteract.setMessage(Text.translatable("gui.disquests.btn.requested"));
    btnInteract.active(false);
    btnInteract.tooltip(Text.translatable("gui.disquests.tooltip.already_requested"));
  }
```

- [ ] **Step 7: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git commit -m "feat: merge Join and Request into single context-sensitive button

Shows 'Join' for OPEN quests, 'Request' for CLOSED quests,
and 'Requested' (greyed out with tooltip) if already requested."
```

---

### Task 3: Add tooltip translation key

**Files:**
- Modify: `client/src/main/resources/assets/disquests/lang/en_us.json5`
- Modify: `client/src/main/resources/assets/disquests/lang/fr_fr.json5`

- [ ] **Step 1: Add tooltip key to en_us.json5**

Add in the tooltip section:

```json5
    "already_requested": "You have already requested access",
```

- [ ] **Step 2: Add tooltip key to fr_fr.json5**

```json5
    "already_requested": "Tu as déjà demandé l'accès",
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/resources/assets/disquests/lang/en_us.json5
git add client/src/main/resources/assets/disquests/lang/fr_fr.json5
git commit -m "i18n: add 'already requested' tooltip translation"
```

---

### Task 4: Update E2E tests

**Files:**
- Check: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/`

- [ ] **Step 1: Search for btn-join and btn-request references in tests**

Run: `grep -r "btn-join\|btn-request" client/src/testmod/`

Replace any `click(context, "btn-join")` with `click(context, "btn-interact")` and any `click(context, "btn-request")` with `click(context, "btn-interact")`.

- [ ] **Step 2: Verify testmod compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run E2E tests**

Run: `./gradlew :client:runIntegrationTest`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add client/src/testmod/
git commit -m "test: update E2E tests for merged interact button"
```
