package com.disqt.disquests.client.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A single custom payload wrapper that carries raw bytes. All Disquests packet types are
 * multiplexed through this one channel using the common module's PacketCodec for encode/decode.
 */
public record RawPayload(byte[] data) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<RawPayload> ID =
      new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("disquests", "main"));

  public static final StreamCodec<FriendlyByteBuf, RawPayload> CODEC =
      CustomPacketPayload.codec(RawPayload::write, RawPayload::read);

  private void write(FriendlyByteBuf buf) {
    buf.writeBytes(data);
  }

  private static RawPayload read(FriendlyByteBuf buf) {
    byte[] bytes = new byte[buf.readableBytes()];
    buf.readBytes(bytes);
    return new RawPayload(bytes);
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return ID;
  }
}
