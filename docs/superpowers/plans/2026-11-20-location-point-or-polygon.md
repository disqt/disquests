# Location: a Point or a Polygon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A quest location is one point or a polygon of three or more points, edited by clicking the location card's map preview, which opens the full-screen map editor.

**Architecture:** One shared rule in `common` (`QuestLocation.pointOrPolygon`) reduces any corner list to a legal shape; the client's map editor and the server's `normalize` both go through it. The location card's preview becomes a click target (no in-place corner dragging, no coordinate rows, no "Edit on map" button) while the map is available; the map-off fallback keeps today's coordinate rows.

**Tech Stack:** Java 25, Fabric client with owo-ui 0.13.1, Paper plugin, JUnit 5, the in-game journey suite.

**Spec:** `docs/superpowers/specs/2026-11-20-location-point-or-polygon-design.md`

## Global Constraints

- Branch `feat/location-point-or-polygon`, created from `feat/quest-trees-client` (PR #99's head) in its own worktree. Never push to `main`; do not merge anything.
- Commit subjects: conventional type, lowercase after the type, at most 100 characters.
- No protocol change: packets, stored data and the V1/V2 downgrade fields stay as they are.
- `./gradlew spotlessApply` before every commit; `./gradlew build` and `./gradlew :client:compileTestmodJava` must pass at the end of every task.
- Mouse buttons are compared to `InputConstants` constants, never to literals.
- Lang changes go in both `en_us.json5` and `fr_fr.json5` (French: informal "tu", all accents, gaming terms in English).
- Local journey runs are allowed only when `tasklist | grep -i -E "javaw"` prints nothing; otherwise rely on CI and say so in the report.
- New tooltip text, exactly: `Click to edit on the map` / `Clique pour modifier sur la carte`.

## Review Focus

1. Two corners placed, then Done: the quest gets a point at the first corner, no error notice, and the editor's unsaved title, text, tree and prerequisites survive the round trip (Task 3 journey).
2. A drag that starts on the preview and ends elsewhere must not open the map editor, and a right or middle click must not either (Task 2, `PreviewClick` unit test).
3. A quest with no location: clicking the preview opens the map editor on an empty drawing (Task 3 journey).
4. Map unavailable (client setting off): the card still shows coordinate rows and no preview, and saving a pin typed there works as before (Task 3 journey step with `worldMap` off).
5. A two-corner polygon arriving at the server from any client is stored as a point (Task 1 test).

---

### Task 1: The shared point-or-polygon rule

**Files:**
- Modify: `common/src/main/java/com/disqt/disquests/common/QuestLocation.java`
- Test: `common/src/test/java/com/disqt/disquests/common/QuestLocationTest.java` (existing file; add to it)

**Interfaces:**
- Produces: `public static List<PointData> QuestLocation.pointOrPolygon(List<PointData> corners)`: empty stays empty; one stays; exactly two returns a one-element list holding the first; three or more returned unchanged. Never returns the input list's mutable instance (use `List.copyOf`).

- [ ] **Step 1: Write the failing tests** in `QuestLocationTest`:

```java
@Test
void pointOrPolygonKeepsLegalShapes() {
  PointData a = new PointData(1, 2), b = new PointData(3, 4), c = new PointData(5, 6);
  assertEquals(List.of(), QuestLocation.pointOrPolygon(List.of()));
  assertEquals(List.of(a), QuestLocation.pointOrPolygon(List.of(a)));
  assertEquals(List.of(a, b, c), QuestLocation.pointOrPolygon(List.of(a, b, c)));
}

@Test
void twoCornersBecomeTheFirstPoint() {
  PointData a = new PointData(1, 2), b = new PointData(3, 4);
  assertEquals(List.of(a), QuestLocation.pointOrPolygon(List.of(a, b)));
}

@Test
void normalizeStoresATwoCornerPolygonAsAPin() {
  QuestData q = questWithPolygon(List.of(new PointData(10, 20), new PointData(30, 40)));
  QuestData n = QuestLocation.normalize(q);
  assertFalse(n.isRegion());
  assertNull(n.coordinates2());
  assertTrue(n.polygon().isEmpty());
  assertEquals(10, n.coordinates().x());
  assertEquals(20, n.coordinates().z());
}
```

`questWithPolygon` : reuse the test file's existing quest builder; add this helper only if none fits.

- [ ] **Step 2: Run** `./gradlew :common:test --tests "*QuestLocationTest"`. Expected: the three new tests fail (missing method; two corners currently give a region).
- [ ] **Step 3: Implement** `pointOrPolygon`, and make `normalize` apply it to `q.polygon()` before its size checks, so the existing `poly.size() >= 2` branch only ever sees three or more. An existing test that asserts a two-corner polygon normalizes to a region is now wrong: change it to the new rule and name it in the report.
- [ ] **Step 4: Run** `./gradlew :common:test :server:test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: treat a two-corner location as a single point`

### Task 2: Click the preview to edit; two corners finish as a point

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/gui/map/PreviewClick.java` (Minecraft-free)
- Test: `client/src/test/java/com/disqt/disquests/client/gui/map/PreviewClickTest.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/map/MapView.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/map/PolygonEditLayer.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/LocationCard.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java`
- Modify: `client/src/main/resources/assets/disquests/owo_ui/quest_screen_edit.xml` (only if the vertex list or button row needs a slot change)
- Modify: `client/src/main/resources/assets/disquests/lang/en_us.json5`, `fr_fr.json5`

**Interfaces:**
- Consumes: `QuestLocation.pointOrPolygon` (Task 1).
- Produces:
  - `PreviewClick`: `void press(double x, double y, boolean leftButton)`, `boolean release(double x, double y, boolean leftButton, boolean inside)` returning true when this press and release count as a click: left button for both, release inside the component, and at most `PreviewClick.SLOP = 3` pixels from the press in each axis. `void cancel()`.
  - `MapView.onPreviewClick(Runnable action)`: only meaningful when `interactive(false)`; the view then takes left clicks (through `PreviewClick`), draws a hover highlight (a 1 px outline in the theme's hover colour over the map's edge) and shows the tooltip `gui.disquests.map.click_to_edit` while hovered. Returns `this`.
  - The edit card's preview keeps the id `mini-map`. With the map available the card has no `vertex-x-*`/`vertex-z-*`/`vertex-y-*` fields and no `btn-edit-on-map`. `btn-set-pos`, `btn-clear-location` and `btn-map` are unchanged and `btn-set-pos` is the journeys' edit-mode marker.

- [ ] **Step 1: Write the failing test** `PreviewClickTest`:

```java
@Test
void aLeftPressAndReleaseInPlaceIsAClick() {
  PreviewClick c = new PreviewClick();
  c.press(10, 10, true);
  assertTrue(c.release(12, 9, true, true));
}

@Test
void aDragIsNotAClick() {
  PreviewClick c = new PreviewClick();
  c.press(10, 10, true);
  assertFalse(c.release(14, 10, true, true));
}

@Test
void releasingOutsideOrWithAnotherButtonIsNotAClick() {
  PreviewClick c = new PreviewClick();
  c.press(10, 10, true);
  assertFalse(c.release(10, 10, true, false));
  c.press(10, 10, false);
  assertFalse(c.release(10, 10, false, true));
  c.press(10, 10, true);
  assertFalse(c.release(10, 10, false, true));
}

@Test
void aReleaseWithoutAPressOrAfterCancelIsNotAClick() {
  PreviewClick c = new PreviewClick();
  assertFalse(c.release(10, 10, true, true));
  c.press(10, 10, true);
  c.cancel();
  assertFalse(c.release(10, 10, true, true));
}
```

- [ ] **Step 2: Run** `./gradlew :client:test --tests "*PreviewClickTest"`. Expected: FAIL (class missing).
- [ ] **Step 3: Implement `PreviewClick`** and run the test. Expected: PASS.
- [ ] **Step 4: `MapView.onPreviewClick`.** A non-interactive view with a preview action accepts mouse-click focus, feeds press and release to `PreviewClick` (button compared to `InputConstants.MOUSE_BUTTON_LEFT`), cancels on focus loss, and runs the action on a click. It still does not pan, zoom or pass events to layers. Without a preview action a non-interactive view behaves exactly as today (the reading view's mini map must not change).
- [ ] **Step 5: The edit card (`LocationCard.buildEdit`).** When `ClientSession.mapAvailable()`: build no vertex rows (return an empty list; remove `vertex-list` from its parent by id lookup from `root`), add the mini map with `interactive(false).onPreviewClick(screen::openMapEditor)`, no `PolygonEditLayer`, no `btn-edit-on-map`. A quest with no location still gets the preview (as `addMiniMap` already does). When the map is not available: unchanged (rows, no preview, the notice label). Delete what this leaves unused: `PolygonEditLayer.moveOnly`, `QuestScreen.moveMapLocation`, `refreshVertexFields`, the `edit_on_map` lang key, and anything else the compiler or a search shows dead. `QuestScreen.openMapEditor` keeps persisting the fields first.
- [ ] **Step 6: Two corners finish as a point.** In `PolygonEditLayer.tryFinish`, remove the two-corner refusal and hand `QuestLocation.pointOrPolygon(vertices())` to `onFinish`. In `QuestScreen.setLocation`, apply `pointOrPolygon` to the incoming list first, so every caller obeys the rule. Remove `gui.disquests.map.need_three_corners` from both lang files; add `gui.disquests.map.click_to_edit` with the exact texts from Global Constraints. If the map editor's hint text (`gui.disquests.map.edit_hint`) says a region needs three corners, reword it in both languages to say one corner is a point and three or more an area.
- [ ] **Step 7: Verify** `./gradlew spotlessApply build :client:compileTestmodJava`. Expected: PASS (journeys are updated in Task 3; they still compile because ids are strings).
- [ ] **Step 8: Commit** `feat: open the map editor from the location preview and drop the coordinate rows`

### Task 3: Journeys and docs

**Files:**
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/LocationJourney.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/MapEditJourney.java`
- Modify: `client/src/testmod/java/com/disqt/disquests/test/integration/bdd/UIActions.java` (only if a helper is needed)
- Modify: `.claude/CLAUDE.md`, `docs/superpowers/specs/2026-11-20-location-point-or-polygon-design.md` (status line)

**Interfaces:**
- Consumes: ids from Task 2 (`mini-map` click target, `btn-set-pos` as edit-mode marker), the rule from Task 1.

- [ ] **Step 1: `MapEditJourney`.** Replace every `click(context, "btn-edit-on-map")` by a click on `mini-map`, and every wait on `btn-edit-on-map` by a wait on `btn-set-pos`. Replace the `vertex-x-2` / `x0` field assertions by assertions on the quest's polygon in the edit screen's quest (through the existing cache or screen accessors the journey already uses), keeping the same expected coordinates. Add two steps:
  - "two corners become a point": open the map editor from the preview, place two corners, press `btn-map-done`; expect the editor back (`btn-set-pos`), the quest's polygon empty, its coordinates equal to the first corner, and the title typed before opening the map still in the title field.
  - "start a location from an empty preview": on a quest with no location, click `mini-map`; expect `MapEditScreen`; press `btn-map-cancel`; expect the editor back with no location.
- [ ] **Step 2: `LocationJourney`.** The steps that type `vertex-x-0`/`vertex-y-0`/`vertex-z-0` run with the map available, where those fields no longer exist: set the pin with `btn-set-pos` there and assert the saved coordinates equal the player's block position. Keep one step that covers the fallback: switch the client's `worldMap` config off (the way `ConfigJourney` changes settings), reopen the editor, assert `mini-map` is missing and `vertex-x-0` exists, type a pin, save, assert the coordinates, then switch `worldMap` back on in a step that also runs if the previous one failed.
- [ ] **Step 3: Run** `./gradlew :client:compileTestmodJava`, then, if no game is running (see Global Constraints), `./gradlew :client:runSoloTests -PtestFilter=MapEditJourney` and `-PtestFilter=LocationJourney`. Expected: PASS. Otherwise push and let CI run them once the pull request targets `main`; say which happened.
- [ ] **Step 4: Docs.** In `.claude/CLAUDE.md`: replace the mentions of `btn-edit-on-map` as the edit-mode marker by `btn-set-pos`; note under "World map" that the edit card's `mini-map` opens `MapEditScreen` on click, that coordinate rows exist only when `mapAvailable()` is false, and that `QuestLocation.pointOrPolygon` turns two corners into the first point. Set the spec's status to "Implemented".
- [ ] **Step 5: Commit** `test: cover the clickable location preview and the two-corner rule` and `docs: location preview and point-or-polygon rule`

## Decisions made while planning (Claude's calls)

- **The preview no longer drags corners in place.** Today the edit card's mini map lets you drag existing corners; a click target and a drag target on the same small map conflict, and the spec makes the preview a way in. Moving a corner now happens in the full-screen editor.
- **The rule also runs on the server** (`normalize`), so a two-corner polygon from any client is stored as a point.
- **The typed two-corner case cannot occur**: the fallback rows only edit corners that exist (one for a pin, three or more for a polygon), so no extra handling is needed there.
