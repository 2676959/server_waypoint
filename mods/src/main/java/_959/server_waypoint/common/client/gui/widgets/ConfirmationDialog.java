package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/** A dialog with Cancel on the right and a confirm button to its left, each sized to fit its text. */
public class ConfirmationDialog extends DialogWidget {
    private static final int MIN_BUTTON_WIDTH = 50;
    private static final int BUTTON_TEXT_PADDING = 10;
    private static final int BUTTON_HEIGHT = 11;

    private final TranslucentButton cancelButton;

    public ConfirmationDialog(int x, int y, Component title, WidgetStack content,
                              @NotNull Runnable confirm, @NotNull Runnable cancel, Font textRenderer) {
        this(x, y, title, content, Component.translatable("server_waypoint.confirm.button"), confirm, cancel, textRenderer);
    }

    public ConfirmationDialog(int x, int y, Component title, WidgetStack content, Component confirmLabel,
                              @NotNull Runnable confirm, @NotNull Runnable cancel, Font textRenderer) {
        this(x, y, title, content,
                button(Component.translatable("server_waypoint.cancel.button"), cancel, textRenderer),
                button(confirmLabel, confirm, textRenderer),
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

    private static TranslucentButton button(Component label, Runnable action, Font font) {
        int width = Math.max(MIN_BUTTON_WIDTH, font.width(label) + BUTTON_TEXT_PADDING);
        return new TranslucentButton(0, 0, width, BUTTON_HEIGHT, label, action::run);
    }
}
