# Mouse Back/Forward Navigation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Support mouse back (button 3) and mouse forward (button 4) for navigating between Disquests screens, like a web browser.

**Architecture:** Add a static screen history stack to `DisquestsBaseScreen`. Every `navigateToScreen` call pushes the current screen onto the back stack and clears the forward stack. Mouse back pops from the back stack and pushes current onto forward. Mouse forward does the reverse. Override `mouseClicked` in `DisquestsBaseScreen` to intercept buttons 3 and 4.

**Tech Stack:** GLFW (button constants), owo-ui `BaseUIModelScreen`, Fabric 1.21.11

---

### Task 1: Add screen history stacks

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java`

- [ ] **Step 1: Add static history stacks**

Add these fields at the top of `DisquestsBaseScreen`, after the existing static constants:

```java
  private static final java.util.Deque<Screen> backStack = new java.util.ArrayDeque<>();
  private static final java.util.Deque<Screen> forwardStack = new java.util.ArrayDeque<>();
  private static final int MAX_HISTORY = 20;
```

- [ ] **Step 2: Add a static method to clear history**

Add a method to clear both stacks (useful when closing all screens):

```java
  public static void clearHistory() {
    backStack.clear();
    forwardStack.clear();
  }
```

- [ ] **Step 3: Push current screen onto back stack in navigateToScreen**

Change `navigateToScreen` from:

```java
  protected void navigateToScreen(Screen screen) {
    if (this.client != null) {
      this.client.setScreen(screen);
    }
  }
```

to:

```java
  protected void navigateToScreen(Screen screen) {
    if (this.client != null) {
      backStack.push(this);
      if (backStack.size() > MAX_HISTORY) backStack.removeLast();
      forwardStack.clear();
      this.client.setScreen(screen);
    }
  }
```

- [ ] **Step 4: Clear history when closing to game (no parent)**

In the `close()` method, clear history when navigating out of Disquests entirely. Change:

```java
  @Override
  public void close() {
    if (this.client != null) {
      this.client.setScreen(parent);
    }
  }
```

to:

```java
  @Override
  public void close() {
    if (this.client != null) {
      if (parent == null) {
        clearHistory();
      }
      this.client.setScreen(parent);
    }
  }
```

- [ ] **Step 5: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java
git commit -m "feat: add screen navigation history stacks

navigateToScreen now pushes the current screen onto a back stack
and clears the forward stack. History clears when exiting to game."
```

---

### Task 2: Handle mouse back/forward buttons

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java`

- [ ] **Step 1: Override mouseClicked to intercept back/forward buttons**

Add this method to `DisquestsBaseScreen`, after the existing `close()` method:

```java
  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int button) {
    // GLFW button 3 = mouse back, button 4 = mouse forward
    if (button == 3) {
      goBack();
      return true;
    } else if (button == 4) {
      goForward();
      return true;
    }
    return super.mouseClicked(mouseX, mouseY, button);
  }

  private void goBack() {
    if (this.client == null || backStack.isEmpty()) return;
    forwardStack.push(this);
    if (forwardStack.size() > MAX_HISTORY) forwardStack.removeLast();
    this.client.setScreen(backStack.pop());
  }

  private void goForward() {
    if (this.client == null || forwardStack.isEmpty()) return;
    backStack.push(this);
    if (backStack.size() > MAX_HISTORY) backStack.removeLast();
    this.client.setScreen(forwardStack.pop());
  }
```

Note: `goBack`/`goForward` call `client.setScreen` directly (not `navigateToScreen`) to avoid double-pushing onto the stacks.

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java
git commit -m "feat: mouse back/forward buttons navigate screen history

GLFW button 3 (back) and button 4 (forward) navigate the screen
history stack, like a web browser."
```

---

### Task 3: Handle MainScreen direct setScreen calls

**Problem:** `MainScreen` uses `this.client.setScreen(...)` directly in a few places (lines 390, 396) instead of `navigateToScreen`. These bypass the history stack.

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java:390,396`

- [ ] **Step 1: Replace direct setScreen calls with navigateToScreen**

Change line 390 from:

```java
    this.client.setScreen(new QuestScreen(this, newQuest, true));
```

to:

```java
    navigateToScreen(new QuestScreen(this, newQuest, true));
```

Change line 396 from:

```java
      this.client.setScreen(new QuestScreen(this, sel));
```

to:

```java
      navigateToScreen(new QuestScreen(this, sel));
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git commit -m "fix: use navigateToScreen in MainScreen for history tracking

Replace direct client.setScreen calls so screen transitions are
recorded in the back/forward history."
```

---

### Task 4: E2E test for mouse back/forward

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/QuestLifecycleJourney.java` (or create a new `NavigationJourney.java` if more appropriate)

- [ ] **Step 1: Add test for mouse back navigation**

Create a test that:
1. Opens MainScreen
2. Opens a quest (navigates to QuestScreen)
3. Clicks mouse button 3 (back)
4. Asserts MainScreen is shown

```java
@Test
@Order(N)
@PlayerA
@DisplayName("Mouse back button returns to previous screen")
void mouseBackButtonNavigates(ClientGameTestContext context) {
  given("player is on MainScreen with quests");
  openMainScreen(context);
  waitForEntryCount(context, expectedCount);

  when("player opens a quest");
  clickEntry(context, 0);
  click(context, "btn-open");
  waitForViewMode(context);
  assertScreenIs(context, QuestScreen.class);

  and("presses mouse back button");
  context.getInput().pressMouse(3); // GLFW button 3 = back
  context.waitTicks(2);

  then("MainScreen is shown");
  waitForScreen(context, MainScreen.class);
  assertScreenIs(context, MainScreen.class);
}
```

- [ ] **Step 2: Add test for mouse forward navigation**

```java
@Test
@Order(N+1)
@PlayerA
@DisplayName("Mouse forward button goes to next screen after back")
void mouseForwardButtonNavigates(ClientGameTestContext context) {
  given("player went back to MainScreen from QuestScreen");
  assertScreenIs(context, MainScreen.class);

  when("player presses mouse forward button");
  context.getInput().pressMouse(4); // GLFW button 4 = forward
  context.waitTicks(2);

  then("QuestScreen is shown again");
  waitForScreen(context, QuestScreen.class);
  assertScreenIs(context, QuestScreen.class);
}
```

Note: Verify that `context.getInput().pressMouse(int)` accepts the GLFW button index. If the API requires `GLFW.GLFW_MOUSE_BUTTON_4` / `GLFW.GLFW_MOUSE_BUTTON_5` constants, use those instead (values are 3 and 4 respectively).

- [ ] **Step 3: Verify testmod compiles**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run tests**

Run: `./gradlew :client:runSoloTests -PtestFilter=NavigationJourney` (or the relevant journey class)
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add client/src/testmod/
git commit -m "test: verify mouse back/forward screen navigation

E2E tests for GLFW button 3 (back) and button 4 (forward)
navigating through screen history."
```
