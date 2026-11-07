package com.disqt.disquests.client.image;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Decodes with stb_image directly (PNG, JPEG, GIF first frame, BMP…) rather than {@link
 * NativeImage#read}, which only accepts PNG. Dimensions are checked before decoding so an image
 * that would blow the {@link ImageCache} limits never allocates its pixels.
 */
public final class MinecraftTextureFactory implements TextureFactory {

  /** A decoded {@link NativeImage}; closing frees the stb buffer unless a texture took it over. */
  private record NativeDecoded(NativeImage image) implements Decoded {
    @Override
    public int width() {
      return image.getWidth();
    }

    @Override
    public int height() {
      return image.getHeight();
    }

    @Override
    public void close() {
      image.close();
    }
  }

  @Override
  public Decoded decode(byte[] bytes) throws IOException {
    ByteBuffer encoded = MemoryUtil.memAlloc(bytes.length);
    try {
      encoded.put(bytes).flip();
      try (MemoryStack stack = MemoryStack.stackPush()) {
        IntBuffer w = stack.mallocInt(1);
        IntBuffer h = stack.mallocInt(1);
        IntBuffer channels = stack.mallocInt(1);
        if (!STBImage.stbi_info_from_memory(encoded, w, h, channels)) {
          throw new IOException("not an image: " + STBImage.stbi_failure_reason());
        }
        int width = w.get(0);
        int height = h.get(0);
        if (width > ImageCache.MAX_SIDE
            || height > ImageCache.MAX_SIDE
            || (long) width * height > ImageCache.MAX_PIXELS) {
          throw new IOException("too large " + width + "x" + height);
        }
        ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, w, h, channels, 4);
        if (pixels == null) {
          throw new IOException("decode failed: " + STBImage.stbi_failure_reason());
        }
        // useStbFree = true: NativeImage.close() releases the buffer with stbi_image_free.
        NativeImage image =
            new NativeImage(
                NativeImage.Format.RGBA, w.get(0), h.get(0), true, MemoryUtil.memAddress(pixels));
        return new NativeDecoded(image);
      }
    } finally {
      MemoryUtil.memFree(encoded);
    }
  }

  @Override
  public Texture upload(String key, Decoded decoded) throws IOException {
    if (!(decoded instanceof NativeDecoded nd)) {
      throw new IOException("unexpected decoded type " + decoded.getClass().getSimpleName());
    }
    // DynamicTexture takes ownership of the NativeImage; the caller closes it if we throw.
    DynamicTexture texture = new DynamicTexture(() -> key, nd.image());
    Minecraft.getInstance().getTextureManager().register(Images.textureId(key), texture);
    return new Texture(key, nd.width(), nd.height());
  }

  @Override
  public void release(String key) {
    Minecraft.getInstance().getTextureManager().release(Images.textureId(key));
  }
}
