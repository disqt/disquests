package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.data.Quest;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class EditQuestScreen extends BaseScreen {
    public EditQuestScreen(Screen parent, Quest quest) {
        super(Text.literal("Edit Quest"), parent);
    }
}
