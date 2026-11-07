# In-Game World Map with Quest Regions Design

**Date:** 2026-11-06
**Status:** Implemented in 0.6.0 (branch feat/world-map)
**Branch:** `feat/world-map` (off `main`, after inline images or independently; they do not
touch the same files except lang files)

## Context

Quests can carry a location: a point, or a rectangle from two corners, in a named dimension
(`QuestData.coordinates`, `isRegion`, `coordinates2`, `map`). The only way to see where a quest
is today is the "View in BlueMap" button, which opens the browser. That was the tool available
at the time; it takes you out of the game, and a point or a box is a poor description of "the
area this quest is about".

Leo wants the quest board's home to be a map of the world, drawn in-game, with each quest's
area drawn on it as a polygon, and quests edited by drawing that polygon on the same map.

## Intent

Open the board, see the land around you with the quests that live there, click one to open
it. Writing a quest about a place means drawing its outline on the map, not typing coordinates.
We do not build a world renderer: the map image comes from BlueMap, which already renders the
server's worlds and serves flat tiles over HTTP. We draw quests on top.

## Goals

- Home view of the board is a pannable, zoomable top-down map of the current dimension,
  centred on the player at open, with the player's position marked.
- Each located quest is drawn as a filled, outlined polygon (a single point draws as a pin).
  Hover shows the title; click opens the quest.
- Quests without a location stay reachable: a slim list down one side, unplaced quests at the
  top, with the existing tabs, filters and search.
- Dimension switcher (overworld, nether, end), overworld default; the map follows the
  dimension the player is in when the board opens.
- Quest editor gets a "Set location" flow on the same map: click corners, close the shape,
  drag corners, or place a single pin.
- Existing quests migrate without data loss: a point stays a pin, a rectangle becomes a
  four-corner polygon.
- Input: pan by holding middle mouse and dragging; zoom with the scroll wheel around the
  cursor. A "centre on me" button.

## Non-goals

- 3D view, terrain shading beyond what BlueMap's flat tiles give, caves, markers other than
  quests and the player.
- Rendering the map outside the quest board (HUD minimap, map item).
- Live tile freshness: tiles are whatever BlueMap last rendered and the CDN cached (up to a
  day). Good enough for quest areas.
- Multi-polygon quests, holes, or polygons spanning dimensions. One polygon per quest.
- Replacing BlueMap's web map; the "View in BlueMap" button stays as a secondary link.

## Anti-goals

- A board that is unusable when disqt.com is unreachable or BlueMap is down. Without tiles the
  map draws a dark grid with the quest polygons still on it and a one-line notice; the list
  still works.
- Hitching the render thread on tile fetches or decodes.
- Hammering the server: tiles are cached in memory, fetched once per zoom level per tile per
  session, with a small disk cache.
- Losing the editor's keyboard path: coordinates must still be typeable for people who
  prefer numbers (the X/Y/Z fields stay for the pin case; the polygon editor shows the vertex
  list as editable numbers).

## Constraints

### Map source (verified 2026-11-06 against the live server)

- BlueMap 5.29 at `<bluemap-url>/maps/<mapId>/tiles/<lod>/x<X>/z<Z>.png`, `mapId` from the
  existing `bluemap-map-names` config the server already sends to the client in the handshake
  (overworld → `world_new`, nether → `world_new_nether`, end → `world_new_the_end`).
- Lowres settings from `maps/<mapId>/settings.json`: `tileSize [500,500]`, `lodFactor 5`,
  `lodCount 3`. A lod-N tile covers `500 * 5^(N-1)` blocks: lod 1 = 500 blocks at 1 px/block,
  lod 2 = 2,500 blocks at 5 blocks/px, lod 3 = 12,500 blocks at 25 blocks/px. Tile X/Z index
  = `floor(blockX / coverage)`.
- Each PNG is 501×1002: the top 501×501 is the colour map (one pixel of overlap on the far
  edge), the bottom half is height/light data. We draw only the top half; the bottom half is
  available later for hillshading.
- Tiles are `image/png`, `Cache-Control: public, max-age=86400`. Missing tiles (unrendered
  area) return 404; draw them as the dark grid.
- The client reads the per-map settings once per session so a BlueMap config change does not
  require a Disquests release. Defaults above are the fallback when settings.json fails.

### Client stack

- Minecraft 26.3, Fabric, owo-lib 0.13.1+26.3 (vendored), Java 25.
- Textures: `NativeImage.read(byte[])` → `DynamicTexture` registered with the `TextureManager`
  under `disquests:tile/<map>/<lod>/<x>/<z>`; released on eviction.
- Mouse: owo `UIComponent.onMouseDrag(MouseButtonEvent, dx, dy)` carries the button; use
  `InputConstants.MOUSE_BUTTON_MIDDLE` (26.3 SDL3 numbering: left 1, middle 2, right 3; never
  hardcode the numbers). `onMouseScroll(x, y, amount)` for zoom. Scissor to the component.
- HTTP via JDK `HttpClient`, https only (plain http allowed only when the JVM property
  `disquests.test.allowHttp` is set by the loom gametest run config, shared with the inline
  images feature), 10 s timeout, 2 MiB per tile cap. The fetcher is shared with the inline
  images `ImageCache` if that ships first.

### Data and protocol

- Polygon is a list of (x, z) block coordinates in the quest's dimension, 1 to 64 vertices,
  in order, implicitly closed. Y is not stored for polygons; the pin keeps x, y, z.
- Protocol bump to `ProtocolVersion.V2`: quests gain `polygon: List<(double x, double z)>`
  (empty = none). Server writes V1 clients the derived legacy fields (first vertex as
  coordinates; bounding box as corner 2 with `isRegion` true when the polygon has more than
  one vertex), so 0.5.x clients keep working during the rollout. V2 clients read `polygon` and
  ignore the legacy fields, except a lone point (pin) which stays in `coordinates` with its y.
- SQLite: new table `quest_polygons (quest_id, idx, x, z)`. Migration on plugin start:
  rows with `is_region = 1` and both corners get a four-vertex polygon; rows with only
  `coordinates` stay a pin. Legacy columns stay for V1 writes and are kept in sync on save.
- Dimension key: the canonical values are the vanilla dimension ids the client editor
  already writes, `overworld`, `the_nether`, `the_end`. Today the server config maps
  `nether` (not `the_nether`) to a BlueMap id, so a nether quest has no map id; the plugin's
  default `config.yml` changes to the canonical keys and the lookup accepts the old `nether` /
  `end` spellings too, so the VPS config keeps working until it is edited (a note in the
  release notes, no scheduled VPS change). A located quest must have a dimension in V2: the
  editor defaults it to the dimension the player is standing in. Migration backfills
  `map = NULL` on rows that have coordinates to `bluemap-default-map` (overworld on disqt);
  the live DB has 11 overworld pins, 1 end pin, 14 unlocated quests, and one orphan row with a
  second corner but no first (the migration drops the orphan corner).

## Design

### Screens

**Home (MainScreen).** Layout becomes two panes: the map fills the screen, the list is a
collapsible 180 px panel on the right. The existing tab bar (My quests / Quest board), filter
row and search row move to the top of the panel; the list below it shows unplaced quests first
under an "Unplaced" header, then placed quests sorted as today. Clicking a list entry pans the
map to the quest if it is placed, then opens it as today. A toolbar on the map's top-left has
the dimension switcher (three buttons), "centre on me", and a zoom indicator.

The map draws exactly what the list shows: the active tab, Open/Closed filter and search
feed one quest list that both the panel and the `QuestPolygonLayer` consume, so the two never
disagree. On "My quests" that includes private quests; they use the same colours.

Polygon style: fill 25 % alpha, 1 px outline, colour by state: open quests amber, closed
quests grey; the player's own quests get a second, brighter outline. Hover raises fill to
45 % and shows a tooltip with the title and owner. Click opens `QuestScreen`. Where polygons
overlap, the smallest area wins the click. Pins draw as a 7 px marker with the same colours
and a title tooltip.

Zoom levels map to BlueMap lods: zoom 0 = lod 3 (25 blocks/px, whole world), zoom 1 = lod 2,
zoom 2 = lod 1 (1 px/block), zoom 3 = lod 1 drawn at 2× (4 px per block area, no new tiles).
Scroll steps between zoom levels around the cursor; pixel scale is continuous between levels
(tiles are drawn scaled, the lod switches at the midpoint). Default on open: zoom 2, centred
on the player. The player marker is a small arrow pointing in the facing direction. Tiles
outside the world border (±5,000 blocks overworld/end, ±1,250 nether) are not requested.

**Quest editor (QuestScreen, edit mode).** The coordinate rows are replaced by a
location card: a 200 px tall mini map (same component, non-interactive except for the
"Edit on map" button) showing the current shape, plus the vertex list as numbers for the
keyboard path. "Edit on map" opens a full-screen `MapEditScreen` on top of the editor:

- Left-click adds a vertex at the clicked block. Clicking the first vertex (within 6 px) or
  pressing Enter closes the shape and returns to the editor. Escape cancels.
- Right-click removes the last vertex. Dragging a vertex with left button moves it.
- One vertex and Enter = a pin (y taken from the terrain height is not available; y defaults
  to the player's current y when they are in that dimension, otherwise 64, editable in the
  vertex list).
- Middle-drag pans, wheel zooms, as on the home map.
- A "Clear location" button removes the shape.

**View mode (QuestScreen).** The location card shows the mini map with the shape, the
"View in BlueMap" button beneath it as a secondary link.

### Components

- `MapView` (owo `BaseUIComponent`): owns camera (centre block x/z, zoom), draws tiles from
  `TileCache`, then an overlay list of `MapLayer`s (quest polygons, player marker, edit
  vertices), handles pan/zoom input, converts between screen and block coordinates.
  Reused by home, the editor's mini map and `MapEditScreen`.
- `TileCache` (client static): LRU of decoded tiles keyed by (map, lod, x, z), 256 textures
  max; failed/404 tiles memoised for the session; in-flight dedup; fetch on a 2-thread pool,
  texture upload on the render thread. Disk cache under `.minecraft/disquests/tiles/` keyed
  by URL hash, honouring the response's max-age (so tiles are revalidated daily, not per
  session). Reads `maps/<id>/settings.json` once for lod parameters.
- `QuestPolygonLayer`: draws polygons/pins for a quest list with hover and click hit-testing
  (point-in-polygon in block space).
- `PolygonEditLayer`: vertex handles, add/remove/drag.
- Polygon fill: the 26.3 gui pipeline only offers axis-aligned rect fills, so fill by
  scanline in screen space (one 1 px-tall rect per span per row, spans from the polygon's
  edge crossings, even-odd rule), clipped to the component. No triangulation. Outline with
  1 px segments drawn as short rects (Bresenham steps). Cache the span list per quest per
  (zoom, camera) frame only; it is cheap. Polygons may be concave but must be simple (no
  self-intersection); the editor rejects a closing click that would create a
  self-intersecting shape with a short notice.

### Migration and rollout

1. Server plugin update first (V2 aware, writes V1 for old clients). Existing data migrates
   on start; the legacy columns stay.
2. Client release next, in the modpack. V1 clients against the V2 server see polygons as
   bounding boxes. The other direction is not supported for data: the server's `HANDSHAKE`
   gains a trailing protocol-version field (the existing `remaining()`-tolerant reader
   accepts its absence), and a V2 client that sees no field or a lower version keeps the old
   list home and the old coordinate editor, with a one-line "server needs updating" notice.

### Tests

- `PacketCodec` round-trip tests for V2 polygons and the V1 downgrade (bounding box, first
  vertex).
- `DataManager` migration test on a temp SQLite with the three legacy cases (none, pin,
  rectangle).
- Pure-Java unit tests: tile index math for all three lods and negative coordinates, screen ↔
  block conversion at each zoom, point-in-polygon, scanline spans for a concave shape,
  self-intersection check, dimension key normalisation (`nether` → `the_nether`).
- `TileCache` tests with a fake fetcher: dedup, 404 memoised, LRU eviction releases textures,
  disk cache hit avoids fetch.
- E2E journeys (client gametest, following `UIActions`): open board → map visible centred on
  player → quest with polygon rendered and clickable; editor → draw three vertices → save →
  reopen shows polygon; dimension switch; tiles served by a local plain-http fixture server
  (one lod-1 tile checked in as a test resource, `bluemap-url` pointed at it in the harness's
  plugin config) → tile texture loaded; tiles unreachable (dead port) → board still usable;
  V1 server (harness flag) → old list home with the notice.

## Left to the builder

- Exact colours, icon art for the pin and player arrow, lang strings (EN and FR).
- Tooltip layout and whether to show tags in it.
- Whether to animate pans when clicking a list entry (a short ease is nice, not required).
- Disk cache format (plain files named by hash plus a small index is fine).
- How the list panel collapses on narrow windows (below 640 px GUI width, make it a toggle
  button).

## Deviations recorded during implementation

- **V1 downgrade uses the bounding box for both corners.** `coordinates` and `coordinates2` are the polygon's bounding-box min/max (not first vertex plus a corner), so the old BlueMap link lands on the region centre.
- **Zoom is stepped.** Four discrete steps (0..3) mapped to lods; there is no continuous scale between levels.
- **Handshake always writes the full form** plus a trailing `varint serverProtocolVersion`; the player-version gate was dropped.
- **`MapView` takes mouse-click focus** while interactive, because owo delivers drag and key events only to the focused component.
- **`unplaced-header` only appears when both groups are visible** (unplaced and placed quests).
- **Mini-map height is capped on short screens** (`min(200, max(60, height/4))` in edit mode, `min(120, max(60, height/5))` in view mode; never below 60 px).
- **The old coordinate editor is not kept** (Rollout 2). Against a V1 server the location card's vertex rows are the editor and "Edit on map" is hidden, with the `server_outdated` notice under the buttons. Quests read from a V1 server are normalised on arrival, so a legacy rectangle becomes its 4-corner polygon and saves back as the same bounding box.
