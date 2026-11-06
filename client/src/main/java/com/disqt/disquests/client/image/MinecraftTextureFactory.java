package com.disqt.disquests.client.image;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Decodes image bytes with {@link NativeImage} and registers them with the texture manager. */
public final class MinecraftTextureFactory implements TextureFactory {

  @Override
  public Texture create(String key, byte[] bytes) throws IOException {
    NativeImage image = NativeImage.read(bytes);
    DynamicTexture texture = new DynamicTexture(() -> key, image);
    Minecraft.getInstance().getTextureManager().register(Images.textureId(key), texture);
    return new Texture(key, image.getWidth(), image.getHeight());
  }

  @Override
  public void release(String key) {
    Minecraft.getInstance().getTextureManager().release(Images.textureId(key));
  }
}
