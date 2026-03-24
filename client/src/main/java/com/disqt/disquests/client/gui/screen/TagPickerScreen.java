package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.TagColors;
import com.disqt.disquests.client.gui.component.TextFieldComponent;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TagPickerScreen extends DisquestsBaseScreen {

    private static final int MAX_TAGS = 8;
    private static final int MAX_TAG_LENGTH = 32;
    private static final java.util.regex.Pattern TAG_PATTERN =
            java.util.regex.Pattern.compile("[a-z0-9_-]+");

    private final Quest quest;
    // Return screen -- QuestScreen in edit mode
    private final Screen returnScreen;

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

        // Predefined tag list
        FlowLayout predefinedList = root.childById(FlowLayout.class, "predefined-list");
        List<String> existing = quest.getTags();
        List<String> predefined = ClientSession.getPredefinedTags();

        if (predefined.isEmpty()) {
            LabelComponent none = UIComponents.label(
                    Text.literal("No predefined tags").withColor(Colors.TEXT_MUTED));
            none.shadow(true);
            predefinedList.child(none);
        } else {
            for (String tag : predefined) {
                if (existing.contains(tag)) continue; // already on quest
                final String t = tag;
                ButtonComponent btn = UIComponents.button(
                        Text.literal(tag).withColor(TagColors.getForeground(tag)),
                        b -> addTag(t));
                btn.sizing(Sizing.fill(100), Sizing.fixed(16));
                predefinedList.child(btn);
            }
        }

        // Custom tag field + Add button
        FlowLayout customRow = root.childById(FlowLayout.class, "custom-row");
        MultiLineTextFieldWidget textField = new MultiLineTextFieldWidget(
                client.textRenderer, 0, 0, 120, 14,
                "", "custom-tag...", 1, false);
        customTagField = new TextFieldComponent(textField);
        customTagField.sizing(Sizing.fill(70), Sizing.fixed(14));
        customTagField.id("custom-tag-field");
        customRow.child(customTagField);

        ButtonComponent addBtn = UIComponents.button(Text.literal("Add"), b -> addCustomTag());
        addBtn.sizing(Sizing.fixed(40), Sizing.fixed(14));
        customRow.child(addBtn);

        // Cancel button
        root.childById(ButtonComponent.class, "btn-cancel")
                .onPress(b -> navigateToScreen(returnScreen));
    }

    private void addTag(String tag) {
        List<String> tags = quest.getTags();
        if (!tags.contains(tag) && tags.size() < MAX_TAGS) {
            tags.add(tag);
        }
        navigateToScreen(returnScreen);
    }

    private void addCustomTag() {
        if (customTagField == null) return;
        String raw = customTagField.getText().trim().toLowerCase();
        if (raw.isEmpty()) return;
        if (raw.length() > MAX_TAG_LENGTH) {
            raw = raw.substring(0, MAX_TAG_LENGTH);
        }
        if (!TAG_PATTERN.matcher(raw).matches()) {
            // Quietly ignore invalid input -- field stays for user to fix
            return;
        }
        final String tag = raw;
        List<String> tags = quest.getTags();
        if (!tags.contains(tag) && tags.size() < MAX_TAGS) {
            tags.add(tag);
        }
        navigateToScreen(returnScreen);
    }
}
