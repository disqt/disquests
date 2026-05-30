package com.disqt.disquests.client.gui.component;

import com.disqt.disquests.client.ClientCache;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

public class TagAutocompleteDropdown {

  private static final String OVERLAY_ID = "tag-autocomplete-overlay";
  private static final int MAX_RESULTS = 8;
  private static final int BG_COLOR = 0xEE1a1a2e;
  private static final int HOVER_COLOR = 0xEE3a3a5e;
  private static final int TEXT_COLOR = 0xFFe0e0e0;

  private FlowLayout rootComponent;
  private Consumer<String> onSelect;
  private List<String> results = List.of();
  private int selectedIndex = 0;
  private boolean visible = false;

  private int dropdownX;
  private int dropdownY;

  public void setRootComponent(FlowLayout root) {
    this.rootComponent = root;
  }

  public void setOnSelect(Consumer<String> onSelect) {
    this.onSelect = onSelect;
  }

  public void update(String query, int anchorX, int anchorY, boolean positionAbove) {
    if (rootComponent == null) {
      hide();
      return;
    }
    String lowerQuery = query != null ? query.toLowerCase() : "";

    results =
        ClientCache.getAllKnownTags().stream()
            .filter(t -> lowerQuery.isEmpty() || t.toLowerCase().contains(lowerQuery))
            .limit(MAX_RESULTS)
            .toList();
    if (results.isEmpty()) {
      hide();
      return;
    }
    this.dropdownX = anchorX;
    // If positioning above, subtract estimated height
    int lineHeight = 16;
    int panelHeight = results.size() * lineHeight + 4;
    this.dropdownY = positionAbove ? anchorY - panelHeight : anchorY;
    this.selectedIndex = 0;
    this.visible = true;
    rebuildOverlay();
  }

  public void hide() {
    if (!visible) return;
    visible = false;
    results = List.of();
    if (rootComponent != null) {
      var existing = rootComponent.childById(OverlayContainer.class, OVERLAY_ID);
      if (existing != null) {
        existing.remove();
      }
    }
  }

  public boolean isVisible() {
    return visible;
  }

  public boolean onKeyDown(int keyCode) {
    if (!visible) return false;
    if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
      selectedIndex = Math.min(selectedIndex + 1, results.size() - 1);
      rebuildOverlay();
      return true;
    } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
      selectedIndex = Math.max(selectedIndex - 1, 0);
      rebuildOverlay();
      return true;
    } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
        || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
        || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
      selectCurrent();
      return true;
    } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
      hide();
      return true;
    }
    return false;
  }

  private void selectCurrent() {
    if (selectedIndex < results.size() && onSelect != null) {
      onSelect.accept(results.get(selectedIndex));
    }
    hide();
  }

  private void rebuildOverlay() {
    if (rootComponent == null) return;

    var existing = rootComponent.childById(OverlayContainer.class, OVERLAY_ID);
    if (existing != null) {
      existing.remove();
    }

    FlowLayout panel = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
    panel.surface(Surface.flat(BG_COLOR).and(Surface.outline(0xFF3a3a5e)));
    panel.padding(Insets.of(2));
    panel.positioning(Positioning.absolute(dropdownX, dropdownY));

    for (int i = 0; i < results.size(); i++) {
      String tag = results.get(i);
      LabelComponent label = UIComponents.label(Component.literal("#" + tag));
      label.color(Color.ofArgb(TEXT_COLOR));
      label.shadow(true);
      label.sizing(Sizing.content(), Sizing.content());
      label.margins(Insets.of(2, 2, 4, 4));
      if (i == selectedIndex) {
        FlowLayout highlight = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        highlight.surface(Surface.flat(HOVER_COLOR));
        highlight.child(label);
        panel.child(highlight);
      } else {
        panel.child(label);
      }
    }

    OverlayContainer<FlowLayout> overlay = UIContainers.overlay(panel);
    overlay.id(OVERLAY_ID);
    overlay.surface(Surface.BLANK);
    overlay.closeOnClick(true);
    rootComponent.child(overlay);
  }
}
