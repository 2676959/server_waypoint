package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class ConfirmationDialog extends DialogWidget {
    private final Runnable confirm;
    private final Runnable cancel;

    public ConfirmationDialog(int x, int y, Text title, WidgetStack content, @NotNull Runnable confirm, @NotNull Runnable cancel, TextRenderer textRenderer) {
        super(x, y, title, content, textRenderer);
        this.confirm = confirm;
        this.cancel = cancel;
    }

    private void runConfirm() {
        this.confirm.run();
    }

    private void runCancel() {
        this.cancel.run();
    }

    @Override
    protected @Unmodifiable List<ClickableWidget> createButtons() {
        TranslucentButton confirmButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("server_waypoint.confirm.button"), this::runConfirm);
        TranslucentButton cancelButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("server_waypoint.cancel.button"), this::runCancel);
        return List.of(cancelButton, confirmButton);
    }
}
