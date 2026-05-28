package com.disqt.disquests.client;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class KeyBinds {

  public static KeyMapping openGuiKey;
  public static KeyMapping pinKey;
  public static KeyMapping openConfigKey;

  private static final KeyMapping.Category MOD_CATEGORY =
      KeyMapping.Category.create(Identifier.fromNamespaceAndPath("disquests", "main"));

  public static void register() {
    openGuiKey =
        KeyMappingHelper.registerKeyBinding(
            new KeyMapping(
                "key.disquests.opengui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, MOD_CATEGORY));

    pinKey =
        KeyMappingHelper.registerKeyBinding(
            new KeyMapping(
                "key.disquests.togglepin",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, // unbound by default
                MOD_CATEGORY));

    openConfigKey =
        KeyMappingHelper.registerKeyBinding(
            new KeyMapping(
                "key.disquests.openconfig", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, MOD_CATEGORY));
  }
}
