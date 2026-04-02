# Replace Close Button with Back Arrow

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the "Close" button from all screens and add a back arrow button (`<`) in the top-left corner instead. The back arrow navigates to the parent screen (same as what Close did).

**Architecture:** Add a `btn-back` button in each screen's XML layout at the top-left, wire it to `close()` in `DisquestsBaseScreen` (which already navigates to `parent`). Remove `btn-close` from all XML layouts and Java wiring. On MainScreen (no parent), back arrow closes the GUI entirely. Update all E2E tests that reference `btn-close`.

**Tech Stack:** owo-ui 0.13.0 XML layouts, Fabric 1.21.11, JUnit 5 E2E

---

### Task 1: Add back arrow to DisquestsBaseScreen (shared logic)

**Problem:** Every screen needs the same back arrow behavior. Instead of wiring it per-screen, handle it in the base class.

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java`

- [ ] **Step 1: Add a wireBackButton helper method**

Add this method to `DisquestsBaseScreen`:

```java
  /**
   * Wires the back arrow button (btn-back) to close this screen.
   * Call from each screen's build() after the root is available.
   */
  protected void wireBackButton(FlowLayout root) {
    ButtonComponent backBtn = root.childById(ButtonComponent.class, "btn-back");
    if (backBtn != null) {
      backBtn.onPress(b -> this.close());
    }
  }
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java
git commit -m "feat: add wireBackButton helper to DisquestsBaseScreen"
```

---

### Task 2: Add back arrow to MainScreen XML and remove Close button

**Files:**
- Modify: `client/src/main/resources/assets/disquests/owo_ui/main_screen.xml`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`

- [ ] **Step 1: Add btn-back to the top of main_screen.xml**

Insert a horizontal flow row before the title label (after line 20 `<children>`), positioned at the left:

```xml
            <!-- Back arrow -->
            <flow-layout direction="horizontal" id="back-row">
                <sizing>
                    <horizontal method="fill">100</horizontal>
                    <vertical method="content"/>
                </sizing>
                <padding>
                    <left>4</left>
                </padding>
                <children>
                    <button id="btn-back">
                        <text>&lt;</text>
                        <sizing>
                            <horizontal method="fixed">20</horizontal>
                            <vertical method="fixed">20</vertical>
                        </sizing>
                    </button>
                </children>
            </flow-layout>
```

Note: `&lt;` is the XML entity for `<`.

- [ ] **Step 2: Remove btn-close from main_screen.xml**

Delete the `btn-close` button element (lines around 160-166):

```xml
                    <button id="btn-close">
                        <text>Close</text>
                        <sizing>
                            <horizontal method="fixed">70</horizontal>
                            <vertical method="fixed">20</vertical>
                        </sizing>
                    </button>
```

- [ ] **Step 3: Update MainScreen.java — remove Close button wiring, add back button**

Remove the `btnClose` field declaration:

```java
  private ButtonComponent btnClose;
```

Remove the `btnClose` lookup:

```java
    this.btnClose = root.childById(ButtonComponent.class, "btn-close");
```

Remove the `btnClose.onPress(...)` block:

```java
    this.btnClose.onPress(
        btn -> {
          if (this.client != null) this.client.setScreen(null);
        });
```

Add `wireBackButton(root)` in the `build()` method, after `applyThemeRoot(root)`.

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add client/src/main/resources/assets/disquests/owo_ui/main_screen.xml
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git commit -m "feat: replace Close with back arrow on MainScreen"
```

---

### Task 3: Add back arrow to QuestScreen XML and remove Close button

**Files:**
- Modify: `client/src/main/resources/assets/disquests/owo_ui/quest_screen_view.xml`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`

- [ ] **Step 1: Add btn-back to the top of quest_screen_view.xml**

Insert the same back-row pattern before the header-row (after line 19 `<children>`):

```xml
            <!-- Back arrow -->
            <flow-layout direction="horizontal" id="back-row">
                <sizing>
                    <horizontal method="fill">100</horizontal>
                    <vertical method="content"/>
                </sizing>
                <padding>
                    <left>4</left>
                </padding>
                <children>
                    <button id="btn-back">
                        <text>&lt;</text>
                        <sizing>
                            <horizontal method="fixed">20</horizontal>
                            <vertical method="fixed">20</vertical>
                        </sizing>
                    </button>
                </children>
            </flow-layout>
```

- [ ] **Step 2: Remove btn-close from quest_screen_view.xml**

Delete the `btn-close` button element (lines around 154-160).

- [ ] **Step 3: Update QuestScreen.java**

Remove the Close button wiring (line 324):

```java
    root.childById(ButtonComponent.class, "btn-close").onPress(b -> this.close());
```

Add `wireBackButton(root)` in `buildViewMode()`, after the root is available.

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add client/src/main/resources/assets/disquests/owo_ui/quest_screen_view.xml
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java
git commit -m "feat: replace Close with back arrow on QuestScreen"
```

---

### Task 4: Add back arrow to ContributorScreen XML and remove Close button

**Files:**
- Modify: `client/src/main/resources/assets/disquests/owo_ui/contributor_screen.xml`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/ContributorScreen.java`

- [ ] **Step 1: Add btn-back to the top of contributor_screen.xml**

Insert the same back-row pattern before the title label (after `<children>` on line 19):

```xml
            <!-- Back arrow -->
            <flow-layout direction="horizontal" id="back-row">
                <sizing>
                    <horizontal method="fill">100</horizontal>
                    <vertical method="content"/>
                </sizing>
                <padding>
                    <left>4</left>
                </padding>
                <children>
                    <button id="btn-back">
                        <text>&lt;</text>
                        <sizing>
                            <horizontal method="fixed">20</horizontal>
                            <vertical method="fixed">20</vertical>
                        </sizing>
                    </button>
                </children>
            </flow-layout>
```

- [ ] **Step 2: Remove btn-close from contributor_screen.xml**

Delete the `btn-close` button element (lines around 92-98).

- [ ] **Step 3: Update ContributorScreen.java**

Remove line 139:

```java
    root.childById(ButtonComponent.class, "btn-close").onPress(b -> this.close());
```

Add `wireBackButton(root)` in `build()`.

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add client/src/main/resources/assets/disquests/owo_ui/contributor_screen.xml
git add client/src/main/java/com/disqt/disquests/client/gui/screen/ContributorScreen.java
git commit -m "feat: replace Close with back arrow on ContributorScreen"
```

---

### Task 5: Update all E2E tests

**Problem:** Many E2E tests use `click(context, "btn-close")` to navigate back from QuestScreen to MainScreen. All of these need to change to `click(context, "btn-back")`.

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/WikiLinkJourney.java` (3 occurrences)
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/CoordinatesJourney.java` (1 occurrence)
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/TagJourney.java` (1 occurrence)
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/QuestContentJourney.java` (1 occurrence)
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/QuestLifecycleJourney.java` (1 occurrence)
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/SearchAndFilterJourney.java` (1 occurrence)
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/PinAndHudJourney.java` (2 occurrences)
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/duo/TwoPlayerJourneys.java` (2 occurrences)

- [ ] **Step 1: Replace all btn-close references with btn-back**

In every file listed above, replace:

```java
click(context, "btn-close");
```

with:

```java
click(context, "btn-back");
```

- [ ] **Step 2: Verify testmod compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run full E2E suite**

Run: `./gradlew :client:runIntegrationTest`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add client/src/testmod/
git commit -m "test: update all E2E tests to use btn-back instead of btn-close"
```
