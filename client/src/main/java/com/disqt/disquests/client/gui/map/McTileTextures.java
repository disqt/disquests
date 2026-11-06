package com.disqt.disquests.client.gui.map;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Minecraft-backed tile textures registered under the disquests namespace. */
public final class McTileTextures implements TileTextures {
  @Override
  public int[] upload(TileKey key, byte[] png) throws IOException {
    NativeImage image = NativeImage.read(png);
    Minecraft.getInstance()
        .getTextureManager()
        .register(id(key), new DynamicTexture(() -> key.texturePath(), image));
    return new int[] {image.getWidth(), image.getHeight()};
  }

  @Override
  public void release(TileKey key) {
    Minecraft.getInstance().getTextureManager().release(id(key));
  }

  public static Identifier id(TileKey key) {
    return Identifier.fromNamespaceAndPath("disquests", key.texturePath());
  }
}
