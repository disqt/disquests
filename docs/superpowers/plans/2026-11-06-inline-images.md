# Inline Images in Quest Content Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Image links in a quest's markdown render as the picture, inline and scaled, in the quest viewer.

**Architecture:** `MarkdownRenderer` emits an image line (a `RenderedLine` with an `imageUrl`) for `![alt](url)` and for bare https links to png/jpg/jpeg/gif/webp; `MarkdownWidget` lays those out as image blocks whose height comes from a session-wide `ImageCache` that fetches bytes on a small IO pool and turns them into GPU textures on the render thread. Everything with Minecraft types stays in thin adapters (`MinecraftTextureFactory`, `Images`) so the cache, the fetcher, the detector and the fit math are plain Java with unit tests.

**Tech Stack:** Java 25, Fabric 0.20.1 on Minecraft 26.3, owo-lib 0.13.1+26.3 (vendored), commonmark-java (already a dependency), JDK `java.net.http.HttpClient`, JUnit 6 (`libs.junit.jupiter`), Fabric client gametest E2E harness in `client/src/testmod`.

**Spec:** `docs/superpowers/specs/2026-11-06-inline-images-design.md`

## Global Constraints

- Branch `feat/inline-images` off `main` (post v0.5.3). Conventional commits; commitlint rejects a capitalised subject and a header over 100 chars.
- Formatting: google-java-format via spotless; lefthook runs `spotlessApply` on commit, so run `./gradlew spotlessApply` before each commit anyway.
- The client `test` source set has no Minecraft on its classpath (`client/build.gradle.kts` dependencies comment). Anything unit-tested must not import `net.minecraft.*`, `com.mojang.*` or owo.
- The loom dev client crashes natively on this PC on 26.3 (see `.claude.local.md`), so E2E journeys are verified in CI only. Unit tests and `./gradlew build` run locally.
- https only in production; plain http only when the JVM property `disquests.test.allowHttp` is `true`.
- Fetch limits: 10 s timeout, follow redirects, 8 MiB response cap, `Content-Type` must start with `image/`.
- Cache limits: 32 textures or 64 MiB of pixels (width × height × 4), LRU; failed URLs memoised for the session; no disk cache.
- Images never load outside `MarkdownWidget` (never on list render, HUD or hover preview).
- Natural size is in GUI units; never upscale beyond it; aspect preserved; placeholder height 48 while loading or failed.
- Texture identifiers: `disquests:img/<sha256 hex of the url>`.
- Lang keys live in `client/src/main/resources/assets/disquests/lang/{en_us,fr_fr}.json5`, nested json5 (`"gui.disquests.image.{}": { ... }`).

## Review Focus

- The same image URL twice in one quest: one fetch, two blocks drawn. Test in Task 3 (dedup) and Task 6 (two blocks).
- URL with a query string or an uppercase extension (`.../a.PNG?x=1`): still an image. Test in Task 1.
- Image narrower than the content width: drawn at natural size, not stretched. Test in Task 3 (`ImageLayout.fit`).
- Server answers 200 with bytes the decoder rejects: block shows the failed box, nothing throws on the render thread. Test in Task 3 (factory throws → `Failed`).
- A texture evicted while a quest is open: the next `get` returns `Loading` and refetches, the widget relayouts. Test in Task 3 (post-eviction state) and Task 6 (relayout trigger).

---

### Task 1: Image link detector

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/markdown/ImageLinkDetector.java`
- Test: `client/src/test/java/com/disqt/disquests/client/markdown/ImageLinkDetectorTest.java`

**Interfaces:**
- Produces: `public final class ImageLinkDetector` with
  `public static boolean isImageUrl(String destination)` (scheme + extension rule) and
  `public static boolean isBareImageLink(String destination, String linkText)` (true when `isImageUrl(destination)` and `linkText` equals `destination` after trimming),
  `public static boolean allowHttp()` (reads `Boolean.getBoolean("disquests.test.allowHttp")` once, cached in a static) and `static void setAllowHttpForTests(boolean)`.

- [ ] **Step 1: Write the failing tests**

```java
class ImageLinkDetectorTest {
  @AfterEach void reset() { ImageLinkDetector.setAllowHttpForTests(false); }

  @Test void httpsPngIsImage() { assertTrue(ImageLinkDetector.isImageUrl("https://i.imgur.com/a.png")); }
  @Test void extensionIsCaseInsensitiveAndQueryIgnored() {
    assertTrue(ImageLinkDetector.isImageUrl("https://cdn.discordapp.com/x/a.PNG?ex=1&is=2"));
    assertTrue(ImageLinkDetector.isImageUrl("https://x/y.JpEg"));
  }
  @Test void allFiveExtensions() {
    for (String ext : List.of("png", "jpg", "jpeg", "gif", "webp"))
      assertTrue(ImageLinkDetector.isImageUrl("https://x/a." + ext), ext);
  }
  @Test void nonImageExtensionIsNot() { assertFalse(ImageLinkDetector.isImageUrl("https://x/a.pdf")); }
  @Test void extensionInQueryOnlyIsNot() { assertFalse(ImageLinkDetector.isImageUrl("https://x/a?f=b.png")); }
  @Test void httpIsNotUnlessAllowed() {
    assertFalse(ImageLinkDetector.isImageUrl("http://127.0.0.1:8080/a.png"));
    ImageLinkDetector.setAllowHttpForTests(true);
    assertTrue(ImageLinkDetector.isImageUrl("http://127.0.0.1:8080/a.png"));
  }
  @Test void malformedUrlIsNot() { assertFalse(ImageLinkDetector.isImageUrl("https://x/a b.png")); assertFalse(ImageLinkDetector.isImageUrl("")); assertFalse(ImageLinkDetector.isImageUrl(null)); }
  @Test void bareLinkRequiresTextEqualToUrl() {
    assertTrue(ImageLinkDetector.isBareImageLink("https://x/a.png", "https://x/a.png"));
    assertFalse(ImageLinkDetector.isBareImageLink("https://x/a.png", "caption"));
  }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd client && ../gradlew :client:test --tests "com.disqt.disquests.client.markdown.ImageLinkDetectorTest"`
Expected: compilation failure, `ImageLinkDetector` does not exist.

- [ ] **Step 3: Implement `ImageLinkDetector`**

Parse with `java.net.URI.create` inside a try (any exception → false); require scheme `https`, or `http` when `allowHttp()`; take `uri.getPath()`, lower-case it, and check the suffix against the five extensions.

- [ ] **Step 4: Run tests to verify they pass**

Run: same command. Expected: 8 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/markdown/ImageLinkDetector.java client/src/test/java/com/disqt/disquests/client/markdown/ImageLinkDetectorTest.java
git commit -m "feat: detect image links in quest markdown"
```

---

### Task 2: Image lines from the markdown renderer

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/markdown/RenderedLine.java`
- Modify: `client/src/main/java/com/disqt/disquests/client/markdown/MarkdownRenderer.java` (`renderBlock` Paragraph branch ~line 215, `appendInline` ~line 340)
- Modify: `docs/superpowers/specs/2026-11-06-inline-images-design.md` (Block model paragraph)

**Interfaces:**
- Consumes: `ImageLinkDetector.isBareImageLink(String, String)` from Task 1.
- Produces: `RenderedLine` record becomes `(MutableComponent text, int indent, float scale, String imageUrl, String imageAlt)`; existing factories keep their signatures and pass `null, null`; new `public static RenderedLine image(String url, String alt, int indent)` whose `text` is `Component.literal("[" + (alt == null || alt.isBlank() ? "image" : alt) + "]")` in `ChatFormatting.GRAY`; `public boolean isImage()` returns `imageUrl != null`. Every existing consumer (`HoverPreviewRenderer`, `QuestEntryComponent.renderPreviewLine`, `QuestScreen`) keeps compiling and shows the bracketed placeholder text for an image line.

- [ ] **Step 1: Extend `RenderedLine`**

Add the two fields, the `image(...)` factory and `isImage()`. Keep the record (the spec said "sealed interface"; a record field is the smaller change and keeps all consumers untouched — update the spec's Block model paragraph to say so).

- [ ] **Step 2: Emit image lines in `MarkdownRenderer`**

In `renderBlock`'s `Paragraph` branch, walk the paragraph's inline children and split at image nodes: a child is an image when it is `org.commonmark.node.Image` (url = `getDestination()`, alt = plain text of its children) or a `Link` for which `ImageLinkDetector.isBareImageLink(link.getDestination(), <plain text of children>)` is true. Accumulate the non-image children into a `MutableComponent` via the existing `appendInline`; flush it as `RenderedLine.normal(text, indent)` when non-blank before each image and at the end; emit `RenderedLine.image(url, alt, indent)` for each image. List items and block quotes go through `collectInlineText`, not this branch, so an image inside a list item still renders as its alt text (acceptable; spec scope is paragraphs).

Add a private helper `static String plainText(Node)` that concatenates `Text` literals of a node's descendants (reuse `collectPlainText`'s logic on inline nodes).

- [ ] **Step 3: Compile**

Run: `./gradlew :client:compileJava :client:spotlessApply`
Expected: BUILD SUCCESSFUL (no unit test can cover this class: it uses `net.minecraft` components; Task 7's E2E asserts the split).

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/markdown docs/superpowers/specs/2026-11-06-inline-images-design.md
git commit -m "feat: markdown renderer emits image lines for image links"
```

---

### Task 3: Image cache core (plain Java)

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/image/ImageState.java`
- Create: `client/src/main/java/com/disqt/disquests/client/image/ImageFetcher.java`
- Create: `client/src/main/java/com/disqt/disquests/client/image/TextureFactory.java`
- Create: `client/src/main/java/com/disqt/disquests/client/image/ImageCache.java`
- Create: `client/src/main/java/com/disqt/disquests/client/image/ImageLayout.java`
- Test: `client/src/test/java/com/disqt/disquests/client/image/ImageCacheTest.java`
- Test: `client/src/test/java/com/disqt/disquests/client/image/ImageLayoutTest.java`

**Interfaces:**
- Produces:
  - `public sealed interface ImageState permits ImageState.Loading, ImageState.Loaded, ImageState.Failed` with records `Loading()`, `Loaded(String textureKey, int width, int height)`, `Failed(String reason)`.
  - `public interface ImageFetcher { byte[] fetch(String url) throws java.io.IOException; }`
  - `public interface TextureFactory { record Texture(String key, int width, int height) {} Texture create(String key, byte[] bytes) throws java.io.IOException; void release(String key); }` — `create` is always invoked on the render executor.
  - `public final class ImageCache` with constructor `ImageCache(ImageFetcher fetcher, TextureFactory textures, java.util.concurrent.Executor ioExecutor, java.util.concurrent.Executor renderExecutor, int maxEntries, long maxPixelBytes)`, `public ImageState get(String url)` (starts a fetch on first call for an unknown url, returns the current state; after a texture was evicted it returns `Loading` and refetches), `public synchronized int loadedCount()`, `public static String textureKey(String url)` = `"img/" + sha256 hex of the url`.
  - `public final class ImageLayout { public static int[] fit(int naturalW, int naturalH, int availableW) }` → `{drawW, drawH}`: `drawW = min(naturalW, max(1, availableW))`, `drawH = max(1, round(drawW * naturalH / naturalW))`.
  - Pixel bytes of a texture = `width * height * 4L`.

- [ ] **Step 1: Write the failing tests**

`ImageLayoutTest`: `fit(400, 200, 300)` → `{300, 150}`; `fit(100, 50, 300)` → `{100, 50}` (no upscaling); `fit(1000, 10, 300)` → `{300, 3}`; `fit(10, 1000, 300)` → `{10, 1000}`.

`ImageCacheTest` with a `FakeFetcher` (map url → bytes or IOException, counts calls) and a `FakeTextures` (parses the bytes as `"WxH"` text to a size, throws IOException on `"bad"`, records created and released keys); executors are `Runnable::run` unless a test needs to hold a fetch, in which case a `java.util.concurrent.LinkedBlockingQueue<Runnable>` executor is drained by hand.

```java
@Test void firstGetReturnsLoadingThenLoaded()       // queued io executor: get → Loading; drain → Loaded(key,64,32)
@Test void sameUrlFetchedOnce()                     // get twice, drain → fetcher.calls == 1
@Test void failedFetchIsMemoised()                  // fetcher throws → Failed; get again → still Failed, calls == 1
@Test void undecodableBytesAreFailed()              // textures.create throws → Failed("decode")
@Test void lruEvictsOldestAndReleasesTexture()      // maxEntries 2: load a,b, touch a, load c → released == [b]
@Test void pixelBudgetEvicts()                      // maxPixelBytes = 2 * 64*32*4 - 1: third 64x32 image evicts the least recent
@Test void evictedUrlReloads()                      // after eviction get(b) → Loading then Loaded, fetcher.calls for b == 2
@Test void textureKeyIsStableSha256()               // textureKey("https://x/a.png") equals "img/" + known hex
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :client:test --tests "com.disqt.disquests.client.image.*"`
Expected: compilation failure.

- [ ] **Step 3: Implement the five classes**

`ImageCache` keeps `LinkedHashMap<String, Loaded>` in access order, a `Set<String> inFlight`, a `Map<String, Failed> failed`, and `long pixelBytes`; all state changes under `synchronized (this)`. `get`: failed → return it; loaded → return (touch); in flight → `Loading`; else mark in flight, `ioExecutor.execute(fetch)`; the fetch calls `fetcher.fetch`, then `renderExecutor.execute(() -> textures.create(...))`, then stores `Loaded` and evicts (releasing via `textures.release` on the render executor) until within both limits. Any exception → `Failed(reason)` with `"fetch"` or `"decode"` as reason prefix.

- [ ] **Step 4: Run tests to verify they pass**

Run: same command. Expected: 12 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/image client/src/test/java/com/disqt/disquests/client/image
git commit -m "feat: session image cache with LRU eviction and failure memo"
```

---

### Task 4: HTTP image fetcher

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/image/HttpImageFetcher.java`
- Test: `client/src/test/java/com/disqt/disquests/client/image/HttpImageFetcherTest.java`

**Interfaces:**
- Consumes: `ImageFetcher`, `ImageLinkDetector.allowHttp()`.
- Produces: `public final class HttpImageFetcher implements ImageFetcher` with constructor `HttpImageFetcher(long maxBytes)` (production value `8L * 1024 * 1024`) and a no-arg constructor using it. `fetch` throws `IOException` with messages starting `"scheme"`, `"status"`, `"content-type"` or `"too large"`.

- [ ] **Step 1: Write the failing tests**

Use `com.sun.net.httpserver.HttpServer` on port 0 in `@BeforeAll`, with handlers: `/ok.png` → 200, `Content-Type: image/png`, 10 bytes; `/text.png` → 200, `text/html`; `/big.png` → 200, `image/png`, 100 bytes; `/missing.png` → 404. Tests set `ImageLinkDetector.setAllowHttpForTests(true)` in `@BeforeEach` and reset after.

```java
@Test void fetchesImageBytes()            // new HttpImageFetcher(1000).fetch(url("/ok.png")).length == 10
@Test void rejectsHttpWhenNotAllowed()    // setAllowHttpForTests(false) → IOException message startsWith "scheme"
@Test void rejectsNonImageContentType()   // "/text.png" → message startsWith "content-type"
@Test void rejectsOversizedBody()         // new HttpImageFetcher(50).fetch("/big.png") → startsWith "too large"
@Test void rejectsNon200()                // "/missing.png" → startsWith "status"
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :client:test --tests "com.disqt.disquests.client.image.HttpImageFetcherTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement `HttpImageFetcher`**

One shared `HttpClient` (`followRedirects(NORMAL)`, `connectTimeout(10 s)`), request `timeout(10 s)`, `User-Agent: Disquests`. Check scheme (https, or http when `allowHttp()`), send with `BodyHandlers.ofInputStream()`, check status 200 and content type, then read at most `maxBytes + 1` bytes and throw if the extra byte arrives. A `Content-Length` over the cap short-circuits before reading.

- [ ] **Step 4: Run tests to verify they pass**

Run: same command. Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/image/HttpImageFetcher.java client/src/test/java/com/disqt/disquests/client/image/HttpImageFetcherTest.java
git commit -m "feat: https image fetcher with size and content-type limits"
```

---

### Task 5: Minecraft adapters and the global cache

**Files:**
- Create: `client/src/main/java/com/disqt/disquests/client/image/MinecraftTextureFactory.java`
- Create: `client/src/main/java/com/disqt/disquests/client/image/Images.java`

**Interfaces:**
- Consumes: `TextureFactory`, `ImageCache`, `HttpImageFetcher`.
- Produces: `public final class Images { public static ImageCache cache(); public static net.minecraft.resources.Identifier textureId(String textureKey); }` — `cache()` lazily builds the singleton with `new HttpImageFetcher()`, `new MinecraftTextureFactory()`, a 2-thread daemon `Executors.newFixedThreadPool` named `disquests-image-%d`, `Minecraft.getInstance()::execute`, limits 32 and `64L * 1024 * 1024`. `textureId(key)` = `Identifier.fromNamespaceAndPath("disquests", key)`.

- [ ] **Step 1: Implement `MinecraftTextureFactory`**

`create(key, bytes)`: `NativeImage image = NativeImage.read(bytes)` (throws `IOException` on undecodable data); `DynamicTexture tex = new DynamicTexture(() -> key, image)`; `Minecraft.getInstance().getTextureManager().register(Images.textureId(key), tex)`; return `new Texture(key, image.getWidth(), image.getHeight())`. `release(key)`: `getTextureManager().release(Images.textureId(key))`.

- [ ] **Step 2: Implement `Images`**

As in Interfaces. Verify the `DynamicTexture(Supplier<String>, NativeImage)` constructor and `TextureManager.register/release` signatures with `javap` against the loom-mapped jar if the compile disagrees.

- [ ] **Step 3: Compile**

Run: `./gradlew :client:compileJava :client:spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/image
git commit -m "feat: minecraft texture adapter and global image cache"
```

---

### Task 6: Image blocks in the markdown widget

**Files:**
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/widget/MarkdownWidget.java` (`WrappedEntry` ~line 166, `rebuildWrappedLines` ~183, `draw` ~239, hover block ~294, `onMouseDown` ~374)
- Modify: `client/src/main/java/com/disqt/disquests/client/gui/screen/QuestScreen.java:202` (give the widget `id("content-markdown")`)
- Modify: `client/src/main/resources/assets/disquests/lang/en_us.json5`, `fr_fr.json5`

**Interfaces:**
- Consumes: `RenderedLine.isImage()/imageUrl()/imageAlt()`, `Images.cache().get(url)`, `Images.textureId`, `ImageLayout.fit`, `ImageState`.
- Produces: on `MarkdownWidget`, `public int imageBlockCount()` and `public int loadedImageCount()` (for the E2E journey). Lang keys `gui.disquests.image.loading` = "Loading image…" / "Chargement de l'image…" and `gui.disquests.image.failed` = "Image failed to load" / "Échec du chargement de l'image".

- [ ] **Step 1: Extend `WrappedEntry`**

Add `String imageUrl` and `int blockHeight` (0 for text); `height(Font)` returns `blockHeight` when `imageUrl != null`. Text entries pass `null, 0`.

- [ ] **Step 2: Lay out image lines in `rebuildWrappedLines`**

For `line.isImage()`: `ImageState s = Images.cache().get(line.imageUrl())`; if `Loaded` → `int[] wh = ImageLayout.fit(s.width(), s.height(), availableWidth - indent)`, entry `blockHeight = wh[1]`; else `blockHeight = 48`. Record the url in a `Set<String> unsettledImages` when the state is `Loading`; clear the set at the top of the rebuild. Add 4 px of spacing after an image block (`totalContentHeight += blockHeight + 4`).

- [ ] **Step 3: Relayout when a load settles**

At the top of `draw`, before the width check: if `unsettledImages` is non-empty and any of them is now not `Loading`, set `lastKnownWidth = -1` so the existing rebuild branch runs.

- [ ] **Step 4: Draw image blocks**

In the draw loop, when `entry.imageUrl != null`: state `Loaded(key, w, h)` → `int[] wh = ImageLayout.fit(w, h, availableWidth - indent)`; `context.blit(RenderPipelines.GUI_TEXTURED, Images.textureId(key), drawX, drawY, 0f, 0f, wh[0], wh[1], w, h, w, h)` (the 12-arg overload: x, y, u, v, drawW, drawH, regionW, regionH, textureW, textureH; confirm the order with `javap -cp <loom jar> net.minecraft.client.gui.GuiGraphicsExtractor` if the image renders stretched). `Loading`/`Failed` → `context.fill(drawX, drawY, drawX + availableWidth - indent, drawY + 48, 0xFF202020)` and the lang text centred with `context.text(...)` in `Colors.TEXT_MUTED`; for `Failed` draw the URL on a second line in AQUA underlined. Every image block (any state) adds an `ImageHitbox(x, y, w, h, url)` record to a new `List<ImageHitbox> imageHitboxes` cleared with the others.

- [ ] **Step 5: Hover and click**

Hover over an image hitbox: `context.setTooltipForNextFrame(Component.literal(url), mouseX, mouseY)`. In `onMouseDown`, before the wiki-link loop: an image hitbox hit → `UrlOpener.open(url)`, return true.

- [ ] **Step 6: Test accessors, widget id, lang keys**

`imageBlockCount()` counts entries with `imageUrl != null`; `loadedImageCount()` counts those whose cache state is `Loaded`. In `QuestScreen` after `new MarkdownWidget(rendered)` add `markdownWidget.id("content-markdown")`. Add the two lang keys to both json5 files under a new `"gui.disquests.image.{}"` block.

- [ ] **Step 7: Build**

Run: `./gradlew spotlessApply build`
Expected: BUILD SUCCESSFUL, `client/build/libs/client.jar` produced.

- [ ] **Step 8: Commit**

```bash
git add client/src/main/java/com/disqt/disquests/client/gui client/src/main/resources/assets/disquests/lang
git commit -m "feat: render inline images in the quest viewer"
```

---

### Task 7: E2E journey and harness flag

**Files:**
- Modify: `client/build.gradle.kts` (both `clientGameTest` and `clientGameTestB` run configs, next to the other `-Ddisquests.test.*` vmArgs)
- Create: `client/src/testmod/resources/inline-image-test.png` (any opaque 64×32 PNG; generate once with Python `struct`+`zlib` or copy a sprite and note its size in the test)
- Create: `client/src/testmod/java/com/disqt/disquests/test/integration/journeys/solo/InlineImageJourney.java`

**Interfaces:**
- Consumes: `UIActions.openMainScreen/click/type/waitForScreen/findComponent`, `UIAssertions.assertComponentExists`, `MarkdownWidget.imageBlockCount()/loadedImageCount()`, the `content-markdown` id.

- [ ] **Step 1: Add the harness flag**

`vmArg("-Ddisquests.test.allowHttp=true")` in both run configs.

- [ ] **Step 2: Write the journey**

Follow `QuestContentJourney`'s shape (`@IntegrationTest`, `@BeforeAll resetServerAndSync()`, ordered `@PlayerA` tests). In `@BeforeAll` start `com.sun.net.httpserver.HttpServer` on `127.0.0.1:0` serving the resource at `/img.png` with `Content-Type: image/png`, keep the port in a static; stop it in `@AfterAll`.

Test 1 "image link renders as an image block": create a quest titled "Image Test" with content `"Before
https://example.invalid/not-fetched.txt
![site](http://127.0.0.1:<port>/img.png)
After
http://127.0.0.1:<port>/img.png"` (the same image twice: once as `![alt](url)`, once as a bare link), save, `waitForScreen(QuestScreen.class)`, then
`context.waitFor(c -> findComponent(context, MarkdownWidget.class, "content-markdown").loadedImageCount() == 2, TIMEOUT)`, assert `imageBlockCount() == 2`, and assert the server received exactly one request for `/img.png` (count in the handler).

Test 2 "unreachable image shows the failed box, screen still works": edit the quest, replace the content with `"![x](http://127.0.0.1:1/none.png)"`, save, wait until `imageBlockCount() == 1 && loadedImageCount() == 0` and the cache state for that url is `Failed` (expose via `Images.cache().get(url) instanceof ImageState.Failed` inside `computeOnClient`), then `click(context, "btn-edit")` and `assertComponentExists(context, "formatting-panel")`.

- [ ] **Step 3: Compile the testmod**

Run: `./gradlew :client:compileTestmodJava spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit and push, verify in CI**

```bash
git add client/build.gradle.kts client/src/testmod
git commit -m "test: e2e journey for inline images"
git push -u origin feat/inline-images
```

Open the PR (suggest `/simplify` first, per session rules) and wait for `e2e-test` to pass: `gh pr checks --watch`. If the journey fails, pull `e2e-results` with `gh run download -n e2e-results` and read `integration-sync/client-a-output.log`.

---

### Task 8: Docs and release bump

**Files:**
- Modify: `gradle.properties` (`mod_version=0.6.0`)
- Modify: `.claude/CLAUDE.md` (one line under the gotchas: unit tests cannot touch `net.minecraft`; Minecraft-free seams live in `client/image` and `markdown/ImageLinkDetector`)

- [ ] **Step 1: Bump the version and note the pattern**
- [ ] **Step 2: Commit**

```bash
git add gradle.properties .claude/CLAUDE.md
git commit -m "chore: bump to 0.6.0 for inline images"
```

After merge: tag `v0.6.0` (the release workflow builds attested jars), then publish a modpack version with the new client jar as done for 0.5.3.
