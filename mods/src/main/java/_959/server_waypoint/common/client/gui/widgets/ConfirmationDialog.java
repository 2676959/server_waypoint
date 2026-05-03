package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public class ConfirmationDialog extends DialogWidget {
    private final Runnable confirm;
    private final Runnable cancel;

    public ConfirmationDialog(int x, int y, Component title, WidgetStack content, @NotNull Runnable confirm, @NotNull Runnable cancel, Font textRenderer) {
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
    protected @Unmodifiable List<AbstractWidget> createButtons() {
        TranslucentButton confirmButton = new TranslucentButton(0, 0, 50, 11, Component.translatable("server_waypoint.confirm.button"), this::runConfirm);
        TranslucentButton cancelButton = new TranslucentButton(0, 0, 50, 11, Component.translatable("server_waypoint.cancel.button"), this::runCancel);
        return List.of(cancelButton, confirmButton);
    }
}
