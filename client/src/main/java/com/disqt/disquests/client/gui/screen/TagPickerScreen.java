package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientCache;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.component.TagChipComponent;
import com.disqt.disquests.client.gui.component.TextFieldComponent;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import com.disqt.disquests.common.TagConstraints;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class TagPickerScreen extends DisquestsBaseScreen {

  private final Quest quest;
  private final Screen returnScreen;

  private TextFieldComponent filterField;
  private FlowLayout chipCloud;
  private LabelComponent hintLabel;

  public TagPickerScreen(@Nullable Screen parent, Quest quest, Screen returnScreen) {
    super(DataSource.asset(Identifier.of("disquests", "tag_picker_screen")), parent);
    this.quest = quest;
    this.returnScreen = returnScreen;
  }

  @Override
  protected void build(FlowLayout root) {
    applyThemeRoot(root);

    FlowLayout panel = root.childById(FlowLayout.class, "panel");
    if (panel != null) applyThemePanel(panel);

    // Unified filter/create field
    FlowLayout filterRow = root.childById(FlowLayout.class, "filter-row");
    if (filterRow != null) {
      MultiLineTextFieldWidget filterWidget =
          new MultiLineTextFieldWidget(
              client.textRenderer,
              0,
              0,
              160,
              14,
              "",
              Text.translatable("gui.disquests.placeholder.filter_or_create").getString(),
              1,
              false);
      filterField = new TextFieldComponent(filterWidget);
      filterField.sizing(Sizing.fill(100), Sizing.fixed(14));
      filterField.id("filter-field");
      filterField.getDelegate().setChangedListener(text -> rebuildChipCloud());
      filterRow.child(filterField);
    }

    // Chip cloud container
    chipCloud = root.childById(FlowLayout.class, "chip-cloud");

    // Hint label
    hintLabel = root.childById(LabelComponent.class, "hint-label");
    if (hintLabel != null) {
      hintLabel.text(
          Text.translatable("gui.disquests.label.enter_to_create").withColor(Colors.TEXT_MUTED));
      hintLabel.shadow(true);
    }

    rebuildChipCloud();

    // Cancel button
    root.childById(ButtonComponent.class, "btn-cancel")
        .onPress(b -> navigateToScreen(returnScreen));
  }

  @Override
  public boolean keyPressed(KeyInput keyInput) {
    // Intercept Enter to create a custom tag from the current filter text
    if (keyInput.key() == GLFW.GLFW_KEY_ENTER || keyInput.key() == GLFW.GLFW_KEY_KP_ENTER) {
      if (filterField != null) {
        String raw = filterField.getText().trim().toLowerCase();
        if (!raw.isEmpty()) {
          // Strip leading # for convenience
          if (raw.startsWith("#")) {
            raw = raw.substring(1);
          }
          if (raw.length() > TagConstraints.MAX_TAG_LENGTH) {
            raw = raw.substring(0, TagConstraints.MAX_TAG_LENGTH);
          }
          if (TagConstraints.TAG_PATTERN.matcher(raw).matches()) {
            addTagAndReturn(raw);
            return true;
          }
        }
      }
    }
    return super.keyPressed(keyInput);
  }

  /** Build or rebuild the chip cloud based on the current filter text. */
  private void rebuildChipCloud() {
    if (chipCloud == null) return;
    chipCloud.clearChildren();

    String filter = filterField != null ? filterField.getText().trim().toLowerCase() : "";
    // Strip leading # for hashtag-independent search (Issue #16)
    String normalizedFilter = filter.startsWith("#") ? filter.substring(1) : filter;

    List<String> existing = quest.getTags();

    Set<String> allTags = ClientCache.getAllKnownTags();

    List<String> available = new ArrayList<>();
    for (String tag : allTags) {
      if (existing.contains(tag)) continue; // already on quest
      if (!normalizedFilter.isEmpty() && !tag.contains(normalizedFilter)) continue;
      available.add(tag);
    }

    boolean hasExactMatch =
        !normalizedFilter.isEmpty()
            && (allTags.contains(normalizedFilter) || existing.contains(normalizedFilter));

    if (available.isEmpty()) {
      LabelComponent none =
          UIComponents.label(
              Text.translatable("gui.disquests.label.no_matching_tags")
                  .withColor(Colors.TEXT_MUTED));
      none.shadow(true);
      chipCloud.child(none);
    } else {
      for (String tag : available) {
        TagChipComponent chip = new TagChipComponent(tag).onSelect(t -> addTagAndReturn(t));
        chipCloud.child(chip);
      }
    }

    // Update hint visibility: show "Press Enter to create" when text is non-empty
    // and doesn't exactly match an existing tag
    if (hintLabel != null) {
      if (!normalizedFilter.isEmpty()
          && !hasExactMatch
          && TagConstraints.TAG_PATTERN.matcher(normalizedFilter).matches()) {
        hintLabel.text(
            Text.translatable("gui.disquests.label.enter_to_create").withColor(Colors.TEXT_MUTED));
      } else {
        hintLabel.text(Text.empty());
      }
    }
  }

  private void addTagAndReturn(String tag) {
    List<String> tags = quest.getTags();
    if (!tags.contains(tag) && tags.size() < TagConstraints.MAX_TAGS) {
      tags.add(tag);
    }
    navigateToScreen(returnScreen);
  }
}
