package net.atif.buildnotes.client;

import net.atif.buildnotes.Buildnotes;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class KeyBinds {

    public static KeyBinding openGuiKey;
    public static KeyBinding pinKey;

    private static final KeyBinding.Category MOD_CATEGORY = KeyBinding.Category.create(Identifier.of(Buildnotes.MOD_ID, "main"));

    public static void register() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key."+ Buildnotes.MOD_ID +".opengui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                MOD_CATEGORY
        ));

        pinKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key."+ Buildnotes.MOD_ID +".togglepin",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, // unbound by default
                MOD_CATEGORY
        ));
    }
}
