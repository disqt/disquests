package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.data.Quest;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import org.lwjgl.glfw.GLFW;

public class AutocompleteDropdown {

  private static final int MAX_RESULTS = 5;
  private static final int ITEM_HEIGHT = 12;
  private static final int BG_COLOR = 0xEE1a1a2e;
  private static final int HOVER_COLOR = 0xEE3a3a5e;
  private static final int TEXT_COLOR = 0xFFe0e0e0;

  private boolean visible = false;
  private int selectedIndex = 0;
  private List<Quest> results = List.of();
  private int x, y;
  private Consumer<String> onSelect;

  public void update(String query, int cursorX, int cursorY) {
    if (query == null) {
      hide();
      return;
    }
    String lowerQuery = query.toLowerCase();
    results =
        Stream.concat(ClientCache.getMyQuests().stream(), ClientCache.getServerQuests().stream())
            .filter(q -> q.getTitle() != null)
            .filter(q -> lowerQuery.isEmpty() || q.getTitle().toLowerCase().startsWith(lowerQuery))
            .limit(MAX_RESULTS)
            .toList();
    if (results.isEmpty()) {
      hide();
      return;
    }
    this.x = cursorX;
    this.y = cursorY + 10;
    this.selectedIndex = 0;
    this.visible = true;
  }

  public void hide() {
    visible = false;
    results = List.of();
  }

  public boolean isVisible() {
    return visible;
  }

  public void setOnSelect(Consumer<String> onSelect) {
    this.onSelect = onSelect;
  }

  public boolean onKeyDown(int keyCode) {
    if (!visible) return false;
    if (keyCode == GLFW.GLFW_KEY_DOWN) {
      selectedIndex = Math.min(selectedIndex + 1, results.size() - 1);
      return true;
    } else if (keyCode == GLFW.GLFW_KEY_UP) {
      selectedIndex = Math.max(selectedIndex - 1, 0);
      return true;
    } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
      selectCurrent();
      return true;
    } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
      hide();
      return true;
    }
    return false;
  }

  private void selectCurrent() {
    if (selectedIndex < results.size() && onSelect != null) {
      onSelect.accept(results.get(selectedIndex).getTitle());
    }
    hide();
  }

  public void draw(OwoUIGraphics context, int offsetX, int offsetY) {
    if (!visible || results.isEmpty()) return;
    TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    int width = 150;
    int height = results.size() * ITEM_HEIGHT;
    int drawX = x + offsetX;
    int drawY = y + offsetY;

    context.fill(drawX, drawY, drawX + width, drawY + height, BG_COLOR);
    for (int i = 0; i < results.size(); i++) {
      int itemY = drawY + i * ITEM_HEIGHT;
      if (i == selectedIndex) {
        context.fill(drawX, itemY, drawX + width, itemY + ITEM_HEIGHT, HOVER_COLOR);
      }
      Quest q = results.get(i);
      String display = textRenderer.trimToWidth(q.getTitle(), width - 4);
      context.drawText(textRenderer, display, drawX + 2, itemY + 2, TEXT_COLOR, false);
    }
  }
}
