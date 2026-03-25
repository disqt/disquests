package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.ClientSession;
import com.disqt.disquests.client.data.Quest;
import com.disqt.disquests.client.gui.helper.Colors;
import com.disqt.disquests.client.gui.helper.TagColors;
import com.disqt.disquests.client.gui.component.TextFieldComponent;
import com.disqt.disquests.client.gui.widget.MultiLineTextFieldWidget;
import com.disqt.disquests.common.TagConstraints;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TagPickerScreen extends DisquestsBaseScreen {

    private final Quest quest;
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
                ButtonComponent btn = UIComponents.button(
                        Text.literal(tag).withColor(TagColors.getForeground(tag)),
                        b -> addTagAndReturn(tag));
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
