# Navigation State Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace frozen screen instances in back/forward stacks with lightweight NavEntry records that rebuild fresh screens from current cache state.

**Architecture:** `DisquestsBaseScreen` stores `Deque<NavEntry>` instead of `Deque<Screen>`. Each screen subclass implements `toNavEntry()` to serialize itself into a record. On back/forward, `rebuildFromEntry()` creates a fresh screen from the cache. Deleted quests are skipped.

**Tech Stack:** Java 21, owo-ui, Fabric 1.21.11

---

### Task 1: Define NavEntry and update DisquestsBaseScreen

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java`

- [ ] **Step 1: Add NavEntry sealed interface and change stack types**

Add the imports at the top of DisquestsBaseScreen.java:

```java
import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import java.util.UUID;
```

Replace the stack declarations (lines 27-28):

```java
private static final java.util.Deque<Screen> backStack = new java.util.ArrayDeque<>();
private static final java.util.Deque<Screen> forwardStack = new java.util.ArrayDeque<>();
```

With:

```java
private static final java.util.Deque<NavEntry> backStack = new java.util.ArrayDeque<>();
private static final java.util.Deque<NavEntry> forwardStack = new java.util.ArrayDeque<>();
```

Add the NavEntry sealed interface and abstract method inside the class, after the `clearHistory()` method (after line 41):

```java
  public sealed interface NavEntry {
    record QuestNav(UUID questId, boolean editing) implements NavEntry {}

    record MainNav(boolean serverTab, String searchTerm) implements NavEntry {}
  }

  public abstract NavEntry toNavEntry();

  @Nullable
  private Screen rebuildFromEntry(NavEntry entry) {
    if (entry instanceof NavEntry.QuestNav qn) {
      Quest quest = ClientCache.getQuestById(qn.questId());
      if (quest == null) return null;
      return new QuestScreen(this.parent, quest, qn.editing());
    } else if (entry instanceof NavEntry.MainNav mn) {
      ClientSession.setActiveTab(
          mn.serverTab() ? ClientSession.Tab.QUEST_BOARD : ClientSession.Tab.MY_QUESTS);
      ClientSession.setSearchTerm(mn.searchTerm());
      return new MainScreen(this.parent);
    }
    return null;
  }
```

- [ ] **Step 2: Update navigateToScreen to store NavEntry**

Replace `navigateToScreen` (lines 161-168):

```java
  public void navigateToScreen(Screen screen) {
    if (this.client != null) {
      backStack.push(this);
      if (backStack.size() > MAX_HISTORY) backStack.removeLast();
      forwardStack.clear();
      this.client.setScreen(screen);
    }
  }
```

With:

```java
  public void navigateToScreen(Screen screen) {
    if (this.client != null) {
      backStack.push(this.toNavEntry());
      if (backStack.size() > MAX_HISTORY) backStack.removeLast();
      forwardStack.clear();
      this.client.setScreen(screen);
    }
  }
```

- [ ] **Step 3: Update goBack to rebuild from NavEntry**

Replace `goBack()` (lines 65-70):

```java
  private void goBack() {
    if (this.client == null || backStack.isEmpty()) return;
    forwardStack.push(this);
    if (forwardStack.size() > MAX_HISTORY) forwardStack.removeLast();
    this.client.setScreen(backStack.pop());
  }
```

With:

```java
  private void goBack() {
    if (this.client == null) return;
    while (!backStack.isEmpty()) {
      NavEntry entry = backStack.pop();
      Screen screen = rebuildFromEntry(entry);
      if (screen != null) {
        forwardStack.push(this.toNavEntry());
        if (forwardStack.size() > MAX_HISTORY) forwardStack.removeLast();
        this.client.setScreen(screen);
        return;
      }
    }
  }
```

- [ ] **Step 4: Update goForward to rebuild from NavEntry**

Replace `goForward()` (lines 72-77):

```java
  private void goForward() {
    if (this.client == null || forwardStack.isEmpty()) return;
    backStack.push(this);
    if (backStack.size() > MAX_HISTORY) backStack.removeLast();
    this.client.setScreen(forwardStack.pop());
  }
```

With:

```java
  private void goForward() {
    if (this.client == null) return;
    while (!forwardStack.isEmpty()) {
      NavEntry entry = forwardStack.pop();
      Screen screen = rebuildFromEntry(entry);
      if (screen != null) {
        backStack.push(this.toNavEntry());
        if (backStack.size() > MAX_HISTORY) backStack.removeLast();
        this.client.setScreen(screen);
        return;
      }
    }
  }
```

- [ ] **Step 5: Build to check for compile errors**

Run: `./gradlew :client:compileJava`
Expected: FAIL -- QuestScreen, MainScreen, TagPickerScreen, ContributorScreen don't implement `toNavEntry()` yet. That's expected; we'll fix it in the next task.

- [ ] **Step 6: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/DisquestsBaseScreen.java
git commit -m "refactor: replace Screen stacks with NavEntry in back/forward navigation

Screens are rebuilt from current cache state on back/forward instead of
replaying frozen instances. Deleted quests are skipped automatically.
Does not compile yet -- subclass toNavEntry() implementations follow."
```

---

### Task 2: Implement toNavEntry() in all screen subclasses

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/TagPickerScreen.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/ContributorScreen.java`

- [ ] **Step 1: QuestScreen.toNavEntry()**

Add this method to QuestScreen, after the `isEditing()` accessor (after line 113):

```java
  @Override
  public NavEntry toNavEntry() {
    return new NavEntry.QuestNav(quest.getId(), editing);
  }
```

- [ ] **Step 2: MainScreen.toNavEntry()**

Add this method to MainScreen, after the constructor (after line 83):

```java
  @Override
  public NavEntry toNavEntry() {
    return new NavEntry.MainNav(
        currentTab == ClientSession.Tab.QUEST_BOARD,
        searchTerm != null ? searchTerm : "");
  }
```

- [ ] **Step 3: TagPickerScreen.toNavEntry()**

TagPickerScreen is a transient sub-screen of QuestScreen. If it ends up in the stack, navigating back should show the quest view. Add after the constructor (after line 38):

```java
  @Override
  public NavEntry toNavEntry() {
    return new NavEntry.QuestNav(quest.getId(), false);
  }
```

- [ ] **Step 4: ContributorScreen.toNavEntry()**

Same reasoning as TagPickerScreen. Add after the constructor (after line 35):

```java
  @Override
  public NavEntry toNavEntry() {
    return new NavEntry.QuestNav(quest.getId(), false);
  }
```

- [ ] **Step 5: Build and verify**

Run: `./gradlew :client:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java
git add client/src/main/java/com/disqt/disquests/client/gui/screen/MainScreen.java
git add client/src/main/java/com/disqt/disquests/client/gui/screen/TagPickerScreen.java
git add client/src/main/java/com/disqt/disquests/client/gui/screen/ContributorScreen.java
git commit -m "feat: implement toNavEntry() in all screen subclasses

QuestScreen and MainScreen serialize their identity to NavEntry records.
TagPickerScreen and ContributorScreen fall back to their parent quest
view since they're transient sub-screens."
```

---

### Task 3: Run E2E tests

Both tasks modify core navigation code. Run the full E2E suite to catch regressions.

- [ ] **Step 1: Run solo tests**

Run: `./gradlew :client:runSoloTests`
Expected: All tests pass.

- [ ] **Step 2: Run duo tests**

Run: `./gradlew :client:runDuoTests`
Expected: All tests pass.
