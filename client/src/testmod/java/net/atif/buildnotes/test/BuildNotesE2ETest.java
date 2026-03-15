package net.atif.buildnotes.test;

import net.atif.buildnotes.client.ClientCache;
import net.atif.buildnotes.client.ClientSession;
import net.atif.buildnotes.client.KeyBinds;
import net.atif.buildnotes.data.Note;
import net.atif.buildnotes.gui.screen.MainScreen;
import net.atif.buildnotes.data.Note;
import net.atif.buildnotes.gui.screen.ViewBuildScreen;
import java.util.List;
import net.atif.buildnotes.gui.screen.ViewNoteScreen;
import net.atif.buildnotes.hud.HudPinManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

public class BuildNotesE2ETest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        test1_serverHandshake(context);
        test2_smokeTest(context);
        test3_noteCrud(context);
        test4_buildCrud(context);
        test5_serverSync(context);
        test6_hudPinning(context);
        test7_permissions(context);
    }

    private void test1_serverHandshake(ClientGameTestContext context) {
        TestHelper.connectToServer(context);
        TestHelper.waitForHandshake(context);

        boolean onServer = context.computeOnClient(client -> ClientSession.isOnServer());
        if (!onServer) throw new AssertionError("Expected to be on server after handshake");

        boolean canEdit = context.computeOnClient(client -> ClientSession.hasEditPermission());
        if (!canEdit) throw new AssertionError("Expected CAN_EDIT permission by default");
    }

    private void test2_smokeTest(ClientGameTestContext context) {
        // Keybind only works in-game (currentScreen == null), so must be connected first
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitTicks(5);
        context.waitForScreen(MainScreen.class);
        context.takeScreenshot("01_main_screen_empty");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
    }

    private void test3_noteCrud(ClientGameTestContext context) {
        // Open main screen
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);

        // Create note
        context.clickScreenButton("Add");
        context.waitTicks(10);
        context.getInput().typeChars("Test Note");
        context.waitTicks(5);
        context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
        context.waitTicks(5);
        context.getInput().typeChars("Hello world");
        context.waitTicks(5);

        // Save — navigates to ViewNoteScreen
        context.clickScreenButton("Save");
        context.waitTicks(20);
        context.waitForScreen(ViewNoteScreen.class);
        context.takeScreenshot("03_note_view");

        // Delete from ViewNoteScreen
        context.clickScreenButton("Delete");
        context.waitTicks(5);
        context.clickScreenButton("Yes");
        context.waitTicks(20);

        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
    }

    private void test4_buildCrud(ClientGameTestContext context) {
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);
        context.clickScreenButton("Builds");
        context.waitTicks(5);

        // Create build
        context.clickScreenButton("Add");
        context.waitTicks(10);
        context.getInput().typeChars("Test Build");
        context.waitTicks(5);

        // Save — navigates to ViewBuildScreen
        context.clickScreenButton("Save");
        context.waitTicks(20);
        context.waitForScreen(ViewBuildScreen.class);
        context.takeScreenshot("04_build_view");

        // Delete from ViewBuildScreen
        context.clickScreenButton("Delete");
        context.waitTicks(5);
        context.clickScreenButton("Yes");
        context.waitTicks(20);

        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
    }

    private void test5_serverSync(ClientGameTestContext context) {
        // Create a SERVER-scoped note (only SERVER scope syncs to the server)
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);
        context.clickScreenButton("Notes");
        context.waitTicks(5);
        context.clickScreenButton("Add");
        context.waitTicks(10);
        // Cycle scope to SERVER: Per-Server -> Global -> Server (Shared)
        // Each click calls saveNote() internally, so the note gets saved with each scope
        context.clickScreenButton("Scope: Per-Server");
        context.waitTicks(5);
        context.clickScreenButton("Scope: Global");
        context.waitTicks(5);
        // Save with SERVER scope — sends C2S SAVE_NOTE to Paper
        context.clickScreenButton("Save");
        context.waitTicks(40);
        context.waitForScreen(ViewNoteScreen.class);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(10);

        // Disconnect and reconnect
        TestHelper.disconnect(context);
        TestHelper.connectToServer(context);
        TestHelper.waitForHandshake(context);
        // Wait for INITIAL_SYNC — arrives asynchronously after handshake
        context.waitTicks(60);

        // Verify server-synced note persists after reconnect
        boolean found = context.computeOnClient(client -> !ClientCache.getNotes().isEmpty());
        if (!found) throw new AssertionError("No server-synced notes found after reconnect");
    }

    private void test6_hudPinning(ClientGameTestContext context) {
        // Open the first server note directly via computeOnClient
        context.runOnClient(client -> {
            List<Note> notes = ClientCache.getNotes();
            if (!notes.isEmpty()) {
                client.setScreen(new ViewNoteScreen(null, notes.get(0)));
            }
        });
        context.waitTicks(10);
        context.waitForScreen(ViewNoteScreen.class);

        // Pin it
        context.clickScreenButton("Pin to HUD");
        context.waitTicks(5);
        context.clickScreenButton("Close");
        context.waitTicks(10);

        // Screenshot with HUD overlay
        context.waitTicks(20);
        context.takeScreenshot("06_hud_pin_visible");

        boolean pinned = context.computeOnClient(client -> HudPinManager.getPinnedNoteId() != null);
        if (!pinned) throw new AssertionError("Expected note to be pinned");

        // Unpin — open same note again
        context.runOnClient(client -> {
            List<Note> notes = ClientCache.getNotes();
            if (!notes.isEmpty()) {
                client.setScreen(new ViewNoteScreen(null, notes.get(0)));
            }
        });
        context.waitTicks(10);
        context.waitForScreen(ViewNoteScreen.class);
        context.clickScreenButton("Unpin");
        context.waitTicks(5);
        context.clickScreenButton("Close");
        context.waitTicks(10);

        context.takeScreenshot("06_hud_pin_removed");

        boolean unpinned = context.computeOnClient(client -> HudPinManager.getPinnedNoteId() == null);
        if (!unpinned) throw new AssertionError("Expected note to be unpinned");
    }

    private void test7_permissions(ClientGameTestContext context) {
        // Disable editing
        TestHelper.rconCommand("buildnotes allow_all false");
        TestHelper.disconnect(context);
        TestHelper.connectToServer(context);
        TestHelper.waitForHandshake(context);
        context.waitTicks(60);

        boolean viewOnly = context.computeOnClient(client -> !ClientSession.hasEditPermission());
        if (!viewOnly) throw new AssertionError("Expected VIEW_ONLY after allow_all false");

        // Screenshot with disabled buttons — open note directly
        context.runOnClient(client -> {
            List<Note> notes = ClientCache.getNotes();
            if (!notes.isEmpty()) {
                client.setScreen(new ViewNoteScreen(null, notes.get(0)));
            }
        });
        context.waitTicks(10);
        context.takeScreenshot("07_view_only");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);

        // Re-enable editing
        TestHelper.rconCommand("buildnotes allow_all true");
        TestHelper.disconnect(context);
        TestHelper.connectToServer(context);
        TestHelper.waitForHandshake(context);
        context.waitTicks(60);

        boolean canEdit = context.computeOnClient(client -> ClientSession.hasEditPermission());
        if (!canEdit) throw new AssertionError("Expected CAN_EDIT after allow_all true");

        context.runOnClient(client -> {
            List<Note> notes = ClientCache.getNotes();
            if (!notes.isEmpty()) {
                client.setScreen(new ViewNoteScreen(null, notes.get(0)));
            }
        });
        context.waitTicks(10);
        context.takeScreenshot("07_can_edit");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);

        // Disconnect and return to title screen (required by game test framework)
        TestHelper.disconnect(context);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(10);
    }
}
