# Remove "no tags" Label

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the italic "no tags" text from the quest list and quest detail screen. When a quest has no tags, show nothing instead.

**Architecture:** Two small changes — skip the "no tags" rendering in `QuestEntryComponent` and `QuestScreen`.

**Tech Stack:** owo-ui 0.13.0, Fabric 1.21.11

---

### Task 1: Remove "no tags" from quest list entries

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/component/QuestEntryComponent.java:317-319`

- [ ] **Step 1: Skip rendering when tags are empty**

In the `render` method, change:

```java
    List<String> tags = quest.getTags();
    if (tags == null || tags.isEmpty()) {
      context.drawText(
          textRenderer, EMPTY_TAGS_TEXT, entryX + 4, entryY + 24, Colors.TEXT_MUTED, false);
    } else {
```

to:

```java
    List<String> tags = quest.getTags();
    if (tags != null && !tags.isEmpty()) {
```

Remove the `else` keyword so the tag rendering block runs when tags are non-empty, and nothing renders when empty. Keep the closing brace.

- [ ] **Step 2: Remove the unused EMPTY_TAGS_TEXT constant**

Delete these lines (36-37):

```java
  private static final Text EMPTY_TAGS_TEXT =
      Text.translatable("gui.disquests.label.no_tags").formatted(Formatting.ITALIC);
```

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/component/QuestEntryComponent.java
git commit -m "fix: remove 'no tags' label from quest list entries

Show empty space instead of italic 'no tags' text when a quest
has no tags assigned."
```

---

### Task 2: Remove "no tags" from quest detail view mode

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java:173-181`

- [ ] **Step 1: Skip the label when tags are empty**

Change:

```java
    if (viewTags.isEmpty()) {
      LabelComponent noTagsLabel =
          UIComponents.label(
              Text.translatable("gui.disquests.label.no_tags")
                  .withColor(Colors.TEXT_MUTED)
                  .styled(s -> s.withItalic(true)));
      noTagsLabel.shadow(false);
      tagDisplay.child(noTagsLabel);
    } else {
      for (String tag : viewTags) {
        tagDisplay.child(new TagChipComponent(tag));
      }
    }
```

to:

```java
    for (String tag : viewTags) {
      tagDisplay.child(new TagChipComponent(tag));
    }
```

The `tagDisplay` FlowLayout will simply be empty when there are no tags, taking up no visual space due to `Sizing.content()` height.

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew :client:classes`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java
git commit -m "fix: remove 'no tags' label from quest detail view

Show empty space instead of italic 'no tags' text in view mode
when a quest has no tags."
```

---

### Task 3: Update E2E tests that assert "no tags"

**Files:**
- Check all test files for assertions on the "no tags" label text.

- [ ] **Step 1: Search for test assertions referencing "no tags"**

Run: `grep -r "no.tags\|no_tags\|EMPTY_TAGS" client/src/testmod/`

If any tests assert the presence of a "no tags" label, update them to assert the tag display area is empty instead. If no tests reference it, skip this task.

- [ ] **Step 2: Build and run affected tests**

Run: `./gradlew :client:compileTestmodJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit (if changes were needed)**

```bash
git add client/src/testmod/
git commit -m "test: update assertions after removing 'no tags' label"
```
