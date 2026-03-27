package com.disqt.disquests.client.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * A single custom payload wrapper that carries raw bytes. All Disquests packet types are
 * multiplexed through this one channel using the common module's PacketCodec for encode/decode.
 */
public record RawPayload(byte[] data) implements CustomPayload {

  public static final CustomPayload.Id<RawPayload> ID =
      new CustomPayload.Id<>(Identifier.of("disquests", "main"));

  public static final PacketCodec<PacketByteBuf, RawPayload> CODEC =
      CustomPayload.codecOf(RawPayload::write, RawPayload::read);

  private void write(PacketByteBuf buf) {
    buf.writeBytes(data);
  }

  private static RawPayload read(PacketByteBuf buf) {
    byte[] bytes = new byte[buf.readableBytes()];
    buf.readBytes(bytes);
    return new RawPayload(bytes);
  }

  @Override
  public Id<? extends CustomPayload> getId() {
    return ID;
  }
}
