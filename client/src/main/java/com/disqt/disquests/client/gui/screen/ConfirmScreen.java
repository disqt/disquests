package com.disqt.disquests.client.gui.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class ConfirmScreen extends DisquestsBaseScreen {

    private final Text message;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmScreen(@Nullable Screen parent, Text message, Runnable onConfirm, Runnable onCancel) {
        super(DataSource.asset(Identifier.of("disquests", "confirm_screen")), parent);
        this.message = message;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void build(FlowLayout root) {
        applyThemeRoot(root);
        applyThemePanel(root.childById(FlowLayout.class, "panel"));

        root.childById(LabelComponent.class, "message-label")
                .text(message);

        root.childById(ButtonComponent.class, "btn-yes")
                .onPress(b -> onConfirm.run());

        root.childById(ButtonComponent.class, "btn-no")
                .onPress(b -> onCancel.run());
    }

}
