package com.disqt.disquests.client.gui.helper;

import io.wispforest.owo.config.annotation.*;

@Modmenu(modId = "disquests")
@Config(name = "disquests-config", wrapperName = "DisquestsConfigWrapper")
public class DisquestsConfigModel {

    @RangeConstraint(min = 100, max = 400)
    public int pinnedWidth = 200;

    @Hook
    public Theme theme = Theme.FROSTED;

    @SectionHeader("hud")
    public int pinnedX = -1;
    public int pinnedY = -1;
}
