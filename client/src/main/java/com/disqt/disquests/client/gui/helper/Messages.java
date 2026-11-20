package com.disqt.disquests.client.gui.helper;

import com.disqt.disquests.client.data.NoticeText;
import net.minecraft.network.chat.Component;

/** Turns the Minecraft-free {@link NoticeText.Message} into a translated component. */
public final class Messages {

  private Messages() {}

  public static Component toComponent(NoticeText.Message message) {
    Object[] args = new Object[message.args().size()];
    for (int i = 0; i < args.length; i++) {
      Object arg = message.args().get(i);
      args[i] = arg instanceof NoticeText.Message nested ? toComponent(nested) : arg;
    }
    return Component.translatable(message.key(), args);
  }
}
