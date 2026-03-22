package com.disqt.disquests.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class KeyBinds {

    public static KeyBinding openGuiKey;
    public static KeyBinding pinKey;
    public static KeyBinding openConfigKey;

    private static final KeyBinding.Category MOD_CATEGORY = KeyBinding.Category.create(Identifier.of("disquests", "main"));

    public static void register() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.disquests.opengui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                MOD_CATEGORY
        ));

        pinKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.disquests.togglepin",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, // unbound by default
                MOD_CATEGORY
        ));

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.disquests.openconfig",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                MOD_CATEGORY
        ));
    }
}
