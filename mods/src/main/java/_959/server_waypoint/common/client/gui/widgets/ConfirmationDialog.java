package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/** A dialog with Cancel on the right and a confirm button to its left, each sized to fit its text. */
public class ConfirmationDialog extends DialogWidget {
    private final TranslucentButton cancelButton;

    public ConfirmationDialog(int x, int y, Component title, WidgetStack content,
                              @NotNull Runnable confirm, @NotNull Runnable cancel, Font textRenderer) {
        this(x, y, title, content, Component.translatable("server_waypoint.confirm.button"), confirm, cancel, textRenderer);
    }

    public ConfirmationDialog(int x, int y, Component title, WidgetStack content, Component confirmLabel,
                              @NotNull Runnable confirm, @NotNull Runnable cancel, Font textRenderer) {
        this(x, y, title, content,
                TranslucentButton.fitted(Component.translatable("server_waypoint.cancel.button"), cancel::run),
                TranslucentButton.fitted(confirmLabel, confirm::run),
                textRenderer);
    }

    private ConfirmationDialog(int x, int y, Component title, WidgetStack content,
                               TranslucentButton cancelButton, TranslucentButton confirmButton, Font textRenderer) {
        super(x, y, title, content, List.<AbstractWidget>of(cancelButton, confirmButton), textRenderer);
        this.cancelButton = cancelButton;
    }

    /** The Cancel button, so a screen can focus it when the dialog opens. */
    public TranslucentButton getCancelButton() {
        return this.cancelButton;
    }
}
