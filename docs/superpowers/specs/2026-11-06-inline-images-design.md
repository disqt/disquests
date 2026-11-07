# Inline Images in Quest Content Design

**Date:** 2026-11-06
**Status:** Designed, ready for planning
**Branch:** `feat/inline-images` (off `main`, after v0.5.3)

## Context

Quest content is markdown rendered by `MarkdownRenderer` (commonmark-java with autolink,
strikethrough and task-list extensions) into `RenderedLine`s that `MarkdownWidget` wraps and
draws. Today a link to an image is just an underlined URL; the reader has to open the browser
to see it. Leo wants a picture that is linked in a quest to simply appear in the quest, scaled
to fit, so a quest can carry a screenshot of the build site, a schematic or a map crop.

## Intent

A quest author pastes an image URL (Discord attachment, Imgur, the disqt.com map, anything
https) into the content, and every reader sees the picture inline where the link sits. No
upload feature, no new storage: the quest text stays plain markdown and the picture lives
wherever it already lives.

## Goals

- `![alt](https://…/x.png)` and a bare `https://…/x.png` link render as the image, inline,
  in view mode.
- Scaled to the content width, never upscaled beyond its natural size, aspect preserved.
  Natural size is in GUI units, like everything else the screen draws (a 400 px image is 400
  GUI units wide, so it scales with the GUI scale setting the same way text does). Designer's
  call: consistency with the rest of the UI over physical-pixel fidelity.
- Clicking the image opens the URL in the browser (existing `UrlOpener`).
- Loading is asynchronous and never stalls the render thread; a placeholder shows while
  loading and a broken-image box with the URL shows on failure.
- Works offline-tolerant: a failed fetch does not break the rest of the quest.

## Non-goals

- Rendering images in the editor's raw text (`MultiLineTextFieldWidget`). The editor keeps
  showing the link text, styled as a link as today.
- Uploading or hosting images; animated GIF playback (first frame is fine).
- Hover previews, lightbox/zoom, image captions beyond the alt text.
- Server-side fetching or proxying.

## Anti-goals (what would make this a failure)

- A quest with a dead or slow image link that hangs or breaks the quest screen.
- A 20 MB image that a reader downloads every time they open the quest.
- A quest that can make every client on the server fetch an arbitrary URL on sync. Images load
  only when the quest is opened in the viewer, never on list render or hover preview.

## Constraints

- Minecraft 26.3, Fabric, owo-lib 0.13.1+26.3 (vendored), Java 25. Rendering goes through
  `GuiGraphicsExtractor` (26.3 render-state pipeline); `MarkdownWidget` already extracts into it.
- Textures: stb decode on the IO thread into a `NativeImage`, then `DynamicTexture` on the
  render thread, registered with the
  `TextureManager` under a `disquests:img/<sha256-of-url>` identifier, released when evicted.
- HTTP: `java.net.http.HttpClient` (JDK), https only (plain http is allowed only when the
  JVM property `disquests.test.allowHttp` is true; the loom gametest run config sets
  it, like the existing `disquests.test.*` properties, so the E2E journey can serve from a
  local plain-HTTP server; production never sets it), 10 s timeout, follow redirects, response
  size cap 8 MiB, content-type must start with `image/`. Decode on a worker thread, upload the
  texture on the render thread (`Minecraft.execute`).
- Formats: png, jpg/jpeg, gif (first frame). webp is not treated as an image link at all
  (stb_image cannot decode it; a link to one stays a clickable text link). Decoding goes through
  stb_image directly, not `NativeImage.read`, which only accepts PNG. Images over 4096 px a side
  or 16 megapixels are refused before their pixels are allocated.
- Nothing is sent to the server: the common protocol and SQLite schema are untouched.

## Design

### Detection

In `MarkdownRenderer`, the commonmark AST already yields `Image` nodes for `![alt](url)`.
Bare URLs come through the autolink extension as `Link` nodes whose text equals the
destination. Treat a `Link` as an image when its destination is https and its path ends in
`.png`, `.jpg`, `.jpeg`, `.gif` or `.webp` (case-insensitive, query string ignored) and the
link text is the URL itself. An explicit `[caption](https://x/y.png)` stays a text link, so an
author can opt out by giving the link a caption.

### Block model

`MarkdownRenderer` today returns `List<RenderedLine>` where every line is text. Add an image
block: the `RenderedLine` record gains two nullable fields, `imageUrl` and `imageAlt`, plus an
`image(url, alt, indent)` factory and `isImage()`; an image line's `text` is a grey "[alt]"
placeholder so consumers that only read text keep working untouched (chosen over a sealed
interface during implementation: smaller change, same behaviour). An image always occupies its
own block, so an inline image in the middle of a paragraph splits the paragraph: text before,
the image, text after.

### Loading and cache

New `ImageCache` (client, static, like `ClientCache`):

- `get(url)` returns a `Loaded(texture, width, height)`, `Loading`, or `Failed(reason)` state and
  kicks off the fetch on first call. One in-flight fetch per URL.
- LRU of at most 32 decoded textures or 64 MiB of pixels, whichever hits first; eviction
  releases the GPU texture. Failed URLs are remembered for the session (retry on next game
  launch) so a dead link is not re-fetched on every screen open.
- Bytes are not persisted to disk. (Minor call: a disk cache is a nice follow-up, not needed.)

### Layout and drawing

`MarkdownWidget.rebuildWrappedLines` gets a case for `ImageLine`: the block height is
`min(naturalWidth, availableWidth) * naturalHeight / naturalWidth` once loaded, and a fixed
48 px while loading or failed. Width is the available content width minus indent. `MarkdownWidget` is sized to fill its parent and scrolls internally via its own total content
height (there is no parent scroll container), so when a load completes the widget just forces
`rebuildWrappedLines` on its next draw (the existing `lastKnownWidth = -1` trick), from the
render thread.

Drawing: loaded → blit the texture at the computed size; loading → dark box with a centred
"Loading image…" label; failed → dark box with "Image failed to load" and the URL in link style.
A loaded image gets a hitbox (reuse the existing URL hitbox record) so a click opens the URL.

### Where it shows

`QuestScreen` view mode, through `MarkdownWidget`. The wiki-link hover preview is drawn by
`HoverPreviewRenderer`, not by the widget; it, `HudPinRenderer` and
`MarkdownRenderer.renderPreviewLine` all consume `RenderedLine.text()` today and must handle
`ImageLine` once the type is sealed: previews draw a one-line "[image]" placeholder and never
trigger a fetch (anti-goal above). Only `MarkdownWidget` calls `ImageCache.get`.

### Tests

- The client `test` source set has no Minecraft on its classpath and `MarkdownRenderer` is
  built on `net.minecraft` chat components, so the detection rule lives in a pure-Java helper,
  `ImageLinkDetector.isImageLink(destination, linkText)`, and `ImageLinkDetectorTest` covers:
  `![a](url)`-style destinations, bare image URL, captioned link to a png → not an image,
  http (not https) → not an image unless the test property is set, query strings ignored,
  case-insensitive extensions. "Image inside a paragraph splits it" is asserted in the E2E
  journey instead.
- `ImageCacheTest` (no Minecraft): size cap rejection, content-type rejection, one fetch per
  URL, failure memoised, LRU eviction count. Fetching and texture creation are both behind
  small interfaces (`ImageFetcher`, `TextureFactory`) so the test uses fakes.
- E2E journey (client gametest): a quest whose content has an image served by a tiny local HTTP
  server started by the test; assert the widget reports a loaded image block of the expected
  height and that the screen renders. Follow the existing `UIActions` helpers.

## Left to the builder

- Exact placeholder colours and label strings (add to the lang files, EN and FR).
- Whether to debounce relayout when several images finish within a frame.
- Thread pool sizing (a small fixed pool, 2 threads, is fine).
