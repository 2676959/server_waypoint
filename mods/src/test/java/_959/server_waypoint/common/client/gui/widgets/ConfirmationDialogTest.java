package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfirmationDialogTest {
    private Field clientInstance;
    private Object previousClient;

    /** Dialog buttons measure their labels with the game client's font. */
    @BeforeEach
    void installClientFont() throws ReflectiveOperationException {
        this.clientInstance = Minecraft.class.getDeclaredField("instance");
        this.clientInstance.setAccessible(true);
        this.previousClient = this.clientInstance.get(null);
        Minecraft client = allocate(Minecraft.class);
        Field font = Minecraft.class.getDeclaredField("font");
        font.setAccessible(true);
        font.set(client, new TestFont());
        this.clientInstance.set(null, client);
    }

    @AfterEach
    void restoreClient() throws ReflectiveOperationException {
        this.clientInstance.set(null, this.previousClient);
    }

    @Test
    void theConfirmButtonIsTheFooterButtonBesideCancel() {
        var dialog = new ConfirmationDialog(0, 0, Component.literal("Title"), new WidgetStack(0, 0, 8, true, false),
                Component.literal("Go"), () -> { }, () -> { }, new TestFont());
        List<AbstractWidget> buttons = new ArrayList<>();
        dialog.visitWidgets(buttons::add);
        assertEquals(List.of(dialog.getCancelButton(), dialog.getConfirmButton()), buttons);
        assertEquals(Component.literal("Go"), dialog.getConfirmButton().getMessage());
    }

    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(unsafeClass.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }
}
