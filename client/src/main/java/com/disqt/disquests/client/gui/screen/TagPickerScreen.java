package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientSession;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class TagPickerScreen extends DisquestsBaseScreen {

  private final Quest quest;
  private final Screen returnScreen;

  private TextFieldComponent filterField;
  private FlowLayout chipCloud;
  private TextFieldComponent customTagField;

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

    // Filter field
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
              Text.translatable("gui.disquests.placeholder.filter_tags").getString(),
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
    rebuildChipCloud();

    // Custom tag field + Add button
    FlowLayout customRow = root.childById(FlowLayout.class, "custom-row");
    MultiLineTextFieldWidget textField =
        new MultiLineTextFieldWidget(
            client.textRenderer,
            0,
            0,
            120,
            14,
            "",
            Text.translatable("gui.disquests.placeholder.custom_tag").getString(),
            1,
            false);
    customTagField = new TextFieldComponent(textField);
    customTagField.sizing(Sizing.fill(70), Sizing.fixed(14));
    customTagField.id("custom-tag-field");
    customRow.child(customTagField);

    ButtonComponent addBtn =
        UIComponents.button(Text.translatable("gui.disquests.btn.add"), b -> addCustomTag());
    addBtn.sizing(Sizing.fixed(40), Sizing.fixed(14));
    customRow.child(addBtn);

    // Cancel button
    root.childById(ButtonComponent.class, "btn-cancel")
        .onPress(b -> navigateToScreen(returnScreen));
  }

  /** Build or rebuild the chip cloud based on the current filter text. */
  private void rebuildChipCloud() {
    if (chipCloud == null) return;
    chipCloud.clearChildren();

    String filter = filterField != null ? filterField.getText().trim().toLowerCase() : "";
    List<String> existing = quest.getTags();

    // Merge predefined + server tags, deduplicated, preserving order
    Set<String> allTags = new LinkedHashSet<>();
    allTags.addAll(ClientSession.getPredefinedTags());
    allTags.addAll(ClientSession.getServerTags());

    List<String> available = new ArrayList<>();
    for (String tag : allTags) {
      if (existing.contains(tag)) continue; // already on quest
      if (!filter.isEmpty() && !tag.contains(filter)) continue; // doesn't match filter
      available.add(tag);
    }

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
  }

  private void addTagAndReturn(String tag) {
    List<String> tags = quest.getTags();
    if (!tags.contains(tag) && tags.size() < TagConstraints.MAX_TAGS) {
      tags.add(tag);
    }
    navigateToScreen(returnScreen);
  }

  private void addCustomTag() {
    if (customTagField == null) return;
    String raw = customTagField.getText().trim().toLowerCase();
    if (raw.isEmpty()) return;
    if (raw.length() > TagConstraints.MAX_TAG_LENGTH) {
      raw = raw.substring(0, TagConstraints.MAX_TAG_LENGTH);
    }
    if (!TagConstraints.TAG_PATTERN.matcher(raw).matches()) {
      // Quietly ignore invalid input -- field stays for user to fix
      return;
    }
    addTagAndReturn(raw);
  }
}
