package com.disqt.disquests.client.gui.screen;

import com.disqt.disquests.client.data.Quest;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ViewQuestScreen extends BaseScreen {
    public ViewQuestScreen(Screen parent, Quest quest) {
        super(Text.literal(quest.getTitle()), parent);
    }
}
