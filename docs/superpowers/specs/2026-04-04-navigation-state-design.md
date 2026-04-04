# Navigation State Refresh Design

## Problem

The back/forward navigation stacks store frozen screen instances. When the user navigates back, owo-ui calls `build()` on the old instance, which renders stale data from the `Quest` object stored at construction time -- not the current cache state. This causes:

1. **Stale content** -- edit a quest, go back, see the pre-edit version
2. **Ghost quests** -- a deleted quest still appears when navigating back
3. **Stale permissions** -- contributor access revoked but edit buttons still visible

## Design

Replace `Deque<Screen>` with `Deque<NavEntry>` where entries describe *what to show*, not *a snapshot of showing it*. On back/forward, rebuild a fresh screen from the current cache.

### NavEntry

Sealed interface in `DisquestsBaseScreen.java` with one record per screen type:

```java
sealed interface NavEntry {
  record QuestNav(UUID questId, boolean editing) implements NavEntry {}
  record MainNav(boolean serverTab, String searchTerm) implements NavEntry {}
}
```

Only store the minimal state needed to reconstruct the screen. Scroll position, selection state, etc. are intentionally discarded -- the user confirmed these are unimportant.

### Navigation Flow

**navigateToScreen(Screen screen):**
- Capture `this.toNavEntry()` and push it onto `backStack`
- Clear `forwardStack`
- Call `client.setScreen(screen)`

**goBack():**
- Pop entry from `backStack`
- Push `this.toNavEntry()` onto `forwardStack`
- Call `client.setScreen(entry.toScreen(parent))` -- rebuilds from cache
- If the entry can't be rebuilt (quest deleted), skip it and try the next entry

**goForward():**
- Mirror of goBack with stacks reversed

### Screen Changes

**DisquestsBaseScreen:**
- Add `abstract NavEntry toNavEntry()` -- each screen knows how to serialize itself
- Change stack types from `Deque<Screen>` to `Deque<NavEntry>`
- Add static `Screen rebuildFromEntry(NavEntry entry, Screen parent)` that creates the appropriate screen from cache
- Handle missing quests: if `ClientCache.getQuestById()` returns null, skip the entry

**QuestScreen:**
- `toNavEntry()` returns `new QuestNav(quest.getId(), editing)`
- Constructor and fields unchanged

**MainScreen:**
- `toNavEntry()` returns `new MainNav(isServerTab, searchTerm)`
- Needs to expose which tab is active and the current search term

### Edge Cases

- **Quest deleted while in back stack:** `rebuildFromEntry` returns null, `goBack` skips to the next entry. If the stack is exhausted, do nothing.
- **Quest permissions changed:** Fresh rebuild from cache means `build()` re-checks `quest.canEdit(myUuid)` with current data. Buttons reflect current permissions.
- **Edit mode in back stack:** `QuestNav(id, editing=true)` reopens in edit mode, but content comes from cache (the last saved version), not the user's unsaved edits. This is correct -- unsaved edits should not survive navigation.
- **Empty stacks:** Same as today -- `goBack`/`goForward` are no-ops.

### What This Does NOT Change

- `ClientCache` stays as-is (no listener system needed)
- `MainScreen.tick()` still polls `ClientCache.getVersion()` for live updates
- `QuestScreen.tick()` still auto-closes on quest deletion
- `ClientPacketHandler` S2C handlers still force-refresh the active screen
- The `parent` field on screens is unchanged -- it's for the close/Escape behavior, not back/forward
