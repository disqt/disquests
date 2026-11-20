# Location: a Point or a Polygon, Edited on the Map

**Date:** 2026-11-20
**Status:** Approved by the owner on 2026-11-20
**Size:** small change
**Builds on:** the quest editor's location card and the full-screen map editor (0.7.x), as they
stand on the branch `feat/quest-trees-client` (PR #99)

Anything marked "Claude's call" was decided by Claude and is the first thing to challenge.

## Intent

A quest's location is **one point or a polygon of three or more points**. There is no other
shape. The owner edits it by drawing on the map, not by typing coordinates. Seen during the
0.9.0 playtest: with the map switched off, the editor shows only rows of X and Z fields, which
reads as a third way of entering a shape.

## How it works today

- Storage is already a list of points: one point is a pin, three or more is a polygon. The
  two-corner rectangle survives only as fields sent to clients older than V2.
- With the map on, the editor's location card shows a small map (the dot or the outline), one
  row of X/Z fields per corner, and the buttons "Edit on map", "Set Pos", "Clear location" and
  "Map: <dimension>". "Edit on map" opens the full-screen `MapEditScreen`, where clicks add
  corners.
- In `MapEditScreen`, finishing with two corners is refused: "A region needs at least 3 corners".
- With the map off (`mapAvailable()` false: old server, switched off, BlueMap unreachable) the
  coordinate rows are the whole editor.

## Approaches

1. **Draw in the full-screen map, opened by clicking the card's preview** (chosen by the owner
   on 2026-11-20). The small map stays a preview; clicking it opens the existing drawing tool.
   No "Edit on map" button. Smallest change; drawing on a 220-pixel map would be fiddly.
2. Draw directly in the card's small map. Fewer steps, but cramped, and it needs pan and zoom
   inside the card. Not chosen.
3. Keep the "Edit on map" button next to the preview. Not chosen: the owner wants the preview
   itself to be the way in.

## Design

- **The card, with the map on:** the small map preview, then the buttons "Set Pos",
  "Clear location" and "Map: <dimension>". No coordinate rows and no "Edit on map" button.
- **Clicking the preview opens the full-screen map editor**, exactly as "Edit on map" does
  today (same Done and Cancel behaviour, the editor's draft kept on return). The preview shows
  it is clickable: a hover highlight and the tooltip "Click to edit on the map" (Claude's call).
- **A quest with no location** still shows the preview, on the remembered or default view, with
  the same tooltip, so a location can be started from it (Claude's call).
- **Finishing in the map editor:**
  - one corner: a point;
  - three or more: a polygon (the existing checks stay: no self-crossing, at most 64 corners);
  - **two corners: a point**, the first corner placed (Claude's call on which one). No error
    message; the "needs at least 3 corners" refusal goes away.
  - no corner: the location is cleared, as today.
- **"Set Pos" stays**: it sets the location to a single point at the player's position, as today.
- **With the map off**, the coordinate rows remain as the only way to edit, unchanged. A typed
  location with exactly two corners is saved as a point too (the first corner), so the rule is
  the same everywhere (Claude's call).
- **The reading view** is unchanged.

## Non-goals

- Changing what is stored or sent: the point list, the V1 and V2 packet forms and the
  bounding-box fields for old clients stay as they are.
- Drawing inside the card's small map.
- Removing the coordinate rows from the map-off fallback.

## Anti-goals

- A location that is neither a point nor a polygon of three or more points reaching the server
  from this client.
- Losing the quest editor's unsaved draft (text, tree, prerequisites) on the trip to the map
  editor and back.

## Constraints

- The server already rejects more than 64 corners and non-finite values; add nothing there
  unless a two-corner list can still arrive from this client.
- E2E journeys use `btn-edit-on-map` (also as the "edit mode" marker in several waits) and the
  `vertex-x-<n>` fields: those references move to the preview (`mini-map`) and to another
  edit-mode marker. `LocationJourney`, `MapEditJourney` and the helper waits need updating.
- Both languages for the new tooltip; the "need three corners" string is removed.

## Left to the builder

- How the preview takes the click (the mini map is a `MapView`; it must not start panning).
- Which component replaces `btn-edit-on-map` as the journeys' edit-mode marker.
- Whether the two-corner rule lives in `QuestLocation` (shared) or in the client's map editor;
  a Minecraft-free unit test either way.

## Delivery

Its own pull request, on a branch stacked on `feat/quest-trees-client`, opened against `main`
once PR #99 is merged (Claude's call: the owner approved the design without choosing, and
PR #99 is waiting on the playtest).
