# FabricClientGameTest API Reference

Fabric's official client-side game testing framework. Runs a real Minecraft client with full input simulation. Ships with `fabric-client-gametest-api-v1`.

**No MockFabric exists.** No headless Screen testing library exists. This is the only way to test Fabric mod GUIs. Most popular mods (Sodium, Iris, ModMenu, REI) have zero GUI tests. The few that do (MouseTweaks, Skyblocker, ChestESP) all use this framework.

## Setup

### build.gradle.kts (client module)

```kotlin
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "your-mod-test"
        enableClientGameTests = true
        eula = true
    }
}
```

This creates a `testmodClient` source set automatically.

### Entrypoint (fabric.mod.json in testmod resources)

```json
{
  "entrypoints": {
    "fabric-client-gametest": [
      "com.example.test.MyClientTest"
    ]
  }
}
```

### Test Class

```java
public class MyClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        // Tests go here
    }
}
```

### Run

```bash
./gradlew runClientGameTest
```

Requires GPU or virtual framebuffer (Xvfb on CI). Tests run sequentially; game returns to title screen between tests and closes when all finish.

## ClientGameTestContext

The main test context. All methods run on the client gametest thread.

### Constants

| Field | Value | Description |
|-------|-------|-------------|
| `DEFAULT_TIMEOUT` | 10 seconds | Default for wait methods |
| `NO_TIMEOUT` | unlimited | Disables timeout |

### Tick Control

```java
context.waitTick();              // Execute one tick, wait for completion
context.waitTicks(20);           // Execute N ticks
```

### Waiting / Assertions

```java
// Wait for predicate (fails after DEFAULT_TIMEOUT)
int ticksWaited = context.waitFor(client -> client.currentScreen == null);

// Wait with custom timeout
context.waitFor(client -> someCondition, 200);

// Wait for specific screen type
context.waitForScreen(MyScreen.class);
```

### Screen Interaction

```java
// Open a screen
context.setScreen(() -> new MyScreen(params));

// Click button by translation key (fails if not found)
context.clickScreenButton("my.mod.button.save");

// Try click (returns boolean, doesn't fail)
boolean clicked = context.tryClickScreenButton("my.mod.button.save");
```

### Screenshots

```java
// Capture screenshot
Path path = context.takeScreenshot("my-test-screenshot");

// Visual regression -- compare against template
context.assertScreenshotEquals("expected-screenshot");

// Find subimage in screenshot -- returns coordinates
Vector2i pos = context.assertScreenshotContains("template-image");
```

### Thread Execution

```java
// Run action on render thread
context.runOnClient(client -> {
    // Access client state safely
    Screen screen = client.currentScreen;
});

// Compute value on render thread
String title = context.computeOnClient(client -> {
    return client.currentScreen.getTitle().getString();
});
```

### Input & World

```java
TestInput input = context.getInput();              // Get input handler
TestWorldBuilder builder = context.worldBuilder();  // Create test worlds
context.restoreDefaultGameOptions();                // Reset all options
```

## TestInput

Full input simulation: mouse, keyboard, modifiers, window.

### Mouse

```java
TestInput input = context.getInput();

// Position cursor
input.setCursorPos(100.0, 200.0);   // Absolute position
input.moveCursor(10.0, -5.0);       // Relative movement

// Click (press + release + wait 1 tick)
input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

// Hold / release (manual control)
input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);

// Hold for duration
input.holdMouseFor(GLFW.GLFW_MOUSE_BUTTON_LEFT, 20); // 20 ticks

// Scroll
input.scroll(3.0);                  // Vertical only
input.scroll(0.0, 3.0);             // Horizontal + vertical
```

### Keyboard

All key methods accept: `KeyBinding`, `Function<GameOptions,KeyBinding>`, `InputUtil.Key`, or raw `int keyCode`.

```java
// Press and release (waits 1 tick)
input.pressKey(client.options.inventoryKey);
input.pressKey(options -> options.sneakKey);
input.pressKey(GLFW.GLFW_KEY_ESCAPE);

// Hold / release (manual)
input.holdKey(GLFW.GLFW_KEY_W);
input.releaseKey(GLFW.GLFW_KEY_W);

// Hold for duration
input.holdKeyFor(GLFW.GLFW_KEY_SPACE, 10);

// Type text (for text fields, not key actions)
input.typeChar('A');
input.typeChars("Hello World");
```

### Modifier Keys

```java
input.holdControl();    // Left Ctrl (Left Super on macOS)
input.releaseControl();
input.holdShift();      // Left Shift
input.releaseShift();
input.holdAlt();        // Left Alt
input.releaseAlt();
```

### Window

```java
input.resizeWindow(1280, 720);
```

## TestWorldBuilder

Create singleplayer worlds or dedicated servers for tests.

```java
// Singleplayer world (most common)
try (TestSingleplayerContext sp = context.worldBuilder().create()) {
    sp.getClientWorld().waitForChunksRender();
    // ... run tests in world ...
}

// With custom settings
context.worldBuilder()
    .setUseConsistentSettings(true)
    .adjustSettings(creator -> {
        // Modify WorldCreator settings
    })
    .create();

// Dedicated server
TestDedicatedServerContext server = context.worldBuilder().createServer();
TestDedicatedServerContext server = context.worldBuilder().createServer(serverProperties);
```

## Common Patterns

### Test a Screen Button Click

```java
context.setScreen(() -> new MyScreen(null, testData));
context.waitTick();

TestInput input = context.getInput();
// Position cursor over button and click
input.setCursorPos(buttonX, buttonY);
input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
context.waitTick();

// Assert state changed
boolean result = context.computeOnClient(client -> {
    MyScreen screen = (MyScreen) client.currentScreen;
    return screen.getSomeState();
});
assert result;
```

### Test Text Field Input

```java
context.setScreen(() -> new MyScreen(null));
context.waitTick();

TestInput input = context.getInput();
// Click the text field to focus it
input.setCursorPos(fieldX, fieldY);
input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
context.waitTick();

// Type text
input.typeChars("Hello World");
context.waitTick();

// Assert field contains text
String text = context.computeOnClient(client -> {
    MyScreen screen = (MyScreen) client.currentScreen;
    return screen.getTextField().getText();
});
assert text.equals("Hello World");
```

### Test With Server Connection

```java
try (TestSingleplayerContext sp = context.worldBuilder().create()) {
    sp.getClientWorld().waitForChunksRender();

    // Open screen that needs server connection
    context.setScreen(() -> new MyNetworkedScreen());
    context.waitTick();

    // Wait for server response
    context.waitFor(client -> {
        MyNetworkedScreen screen = (MyNetworkedScreen) client.currentScreen;
        return screen.hasReceivedData();
    });

    // Assert data received correctly
    // ...
}
```

## CI (GitHub Actions)

Requires virtual framebuffer since there's no GPU:

```yaml
- name: Run client game tests
  uses: modmuss50/xvfb-action@v1
  with:
    run: ./gradlew runClientGameTest
```

Or manually with Xvfb:

```bash
xvfb-run ./gradlew runClientGameTest
```

## Javadoc

- [Package summary](https://maven.fabricmc.net/docs/fabric-api-0.125.3+1.21.5/net/fabricmc/fabric/api/client/gametest/v1/package-summary.html)
- [ClientGameTestContext](https://maven.fabricmc.net/docs/fabric-api-0.125.3+1.21.5/net/fabricmc/fabric/api/client/gametest/v1/context/ClientGameTestContext.html)
- [TestInput](https://maven.fabricmc.net/docs/fabric-api-0.125.3+1.21.5/net/fabricmc/fabric/api/client/gametest/v1/TestInput.html)
- [FabricClientGameTest](https://maven.fabricmc.net/docs/fabric-api-0.125.3+1.21.5/net/fabricmc/fabric/api/client/gametest/v1/FabricClientGameTest.html)
