package net.atif.buildnotes.test;

import net.atif.buildnotes.client.ClientCache;
import net.atif.buildnotes.client.ClientSession;
import net.atif.buildnotes.client.KeyBinds;
import net.atif.buildnotes.data.Note;
import net.atif.buildnotes.gui.screen.MainScreen;
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

        // Save
        context.clickScreenButton("Save");
        context.waitTicks(20);
        context.waitForScreen(MainScreen.class);
        context.takeScreenshot("03_note_in_list");

        // Open note
        context.clickScreenButton("Open");
        context.waitTicks(10);
        context.waitForScreen(ViewNoteScreen.class);
        context.takeScreenshot("03_note_view");

        // Delete
        context.clickScreenButton("Delete");
        context.waitTicks(5);
        context.clickScreenButton("Confirm");
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

        // Save
        context.clickScreenButton("Save");
        context.waitTicks(20);
        context.waitForScreen(MainScreen.class);
        context.takeScreenshot("04_build_in_list");

        // Open
        context.clickScreenButton("Open");
        context.waitTicks(10);
        context.takeScreenshot("04_build_view");

        // Delete
        context.clickScreenButton("Delete");
        context.waitTicks(5);
        context.clickScreenButton("Confirm");
        context.waitTicks(20);

        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
    }

    private void test5_serverSync(ClientGameTestContext context) {
        // Create a note
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);
        context.clickScreenButton("Notes");
        context.waitTicks(5);
        context.clickScreenButton("Add");
        context.waitTicks(10);
        context.getInput().typeChars("Persistent Note");
        context.waitTicks(5);
        context.clickScreenButton("Save");
        context.waitTicks(20);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);

        // Disconnect and reconnect
        TestHelper.disconnect(context);
        TestHelper.connectToServer(context);
        TestHelper.waitForHandshake(context);

        // Verify note persists
        boolean found = context.computeOnClient(client -> {
            for (Note n : ClientCache.getNotes()) {
                if ("Persistent Note".equals(n.getTitle())) return true;
            }
            return false;
        });
        if (!found) throw new AssertionError("Persistent Note not found after reconnect");
    }

    private void test6_hudPinning(ClientGameTestContext context) {
        // Open the existing note
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);
        context.clickScreenButton("Notes");
        context.waitTicks(5);
        context.clickScreenButton("Open");
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

        // Unpin
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);
        context.clickScreenButton("Open");
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

        boolean viewOnly = context.computeOnClient(client -> !ClientSession.hasEditPermission());
        if (!viewOnly) throw new AssertionError("Expected VIEW_ONLY after allow_all false");

        // Screenshot with disabled buttons
        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);
        context.clickScreenButton("Open");
        context.waitTicks(10);
        context.takeScreenshot("07_view_only");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);

        // Re-enable editing
        TestHelper.rconCommand("buildnotes allow_all true");
        TestHelper.disconnect(context);
        TestHelper.connectToServer(context);
        TestHelper.waitForHandshake(context);

        boolean canEdit = context.computeOnClient(client -> ClientSession.hasEditPermission());
        if (!canEdit) throw new AssertionError("Expected CAN_EDIT after allow_all true");

        context.getInput().pressKey(KeyBinds.openGuiKey);
        context.waitForScreen(MainScreen.class);
        context.clickScreenButton("Open");
        context.waitTicks(10);
        context.takeScreenshot("07_can_edit");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(5);
    }
}
