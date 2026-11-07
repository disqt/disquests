package com.disqt.disquests.client.gui.map;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Minecraft-backed tile textures registered under the disquests namespace. */
public final class McTileTextures implements TileTextures {
  @Override
  public Object decode(byte[] png, int tileSize) throws IOException {
    try (NativeImage source = NativeImage.read(png)) {
      int w = Math.min(tileSize, source.getWidth());
      int h = Math.min(tileSize, source.getHeight());
      NativeImage cropped = new NativeImage(source.format(), w, h, false);
      try {
        source.copyRect(cropped, 0, 0, 0, 0, w, h, false, false);
      } catch (RuntimeException e) {
        cropped.close();
        throw e;
      }
      return cropped;
    }
  }

  @Override
  public int[] upload(TileKey key, Object decoded) throws IOException {
    NativeImage image = (NativeImage) decoded;
    int[] size = {image.getWidth(), image.getHeight()};
    Minecraft.getInstance()
        .getTextureManager()
        .register(id(key), new DynamicTexture(() -> key.texturePath(), image));
    return size;
  }

  @Override
  public void discard(Object decoded) {
    if (decoded instanceof NativeImage image) {
      image.close();
    }
  }

  @Override
  public void release(TileKey key) {
    Minecraft.getInstance().getTextureManager().release(id(key));
  }

  public static Identifier id(TileKey key) {
    return Identifier.fromNamespaceAndPath("disquests", key.texturePath());
  }
}
