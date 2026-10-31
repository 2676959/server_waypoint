package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >= 1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
//?}
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The real shared form, built and laid out without client data, key mappings or rendering. Construct it
 * inside {@link #withClient}, which supplies the font and the empty camera that construction reads.
 */
final class WaypointFormTestForm extends AbstractWaypointPropertiesScreen {
    private static final SimpleWaypoint WAYPOINT = new SimpleWaypoint("Base", "Base", "B",
            new WaypointPos(1, 2, 3), 0x123456, 0, true, List.of(), "");

    private final @Nullable Component subtitle;
    private final List<LeadingRow> leadingRows;

    WaypointFormTestForm(@Nullable String subtitle, List<LeadingRow> leadingRows) {
        super(null, Component.literal("Edit"), "minecraft:overworld", "Base", WAYPOINT,
                WaypointFormTestAccess.ACCESS, WaypointFormTestAccess.selection());
        this.subtitle = subtitle == null ? null : Component.literal(subtitle);
        this.leadingRows = leadingRows;
    }

    @FunctionalInterface
    interface Check {
        void run() throws Exception;
    }

    /** Runs {@code check} with a game client that has only {@code font} and an empty camera. */
    static void withClient(Font font, Check check) throws Exception {
        Field instance = clientField();
        Object previous = instance.get(null);
        Minecraft client = allocate(Minecraft.class);
        Field fontField = Minecraft.class.getDeclaredField("font");
        fontField.setAccessible(true);
        fontField.set(client, font);
        GameRenderer renderer = allocate(GameRenderer.class);
        Field camera = GameRenderer.class.getDeclaredField("mainCamera");
        camera.setAccessible(true);
        camera.set(renderer, allocate(Camera.class));
        Field gameRenderer = Minecraft.class.getDeclaredField("gameRenderer");
        gameRenderer.setAccessible(true);
        gameRenderer.set(client, renderer);
        instance.set(null, client);
        try {
            check.run();
        } finally {
            instance.set(null, previous);
        }
    }

    /** Builds the form and lays it out on a screen this big. */
    WaypointFormTestForm layOut(int width, int height) throws Exception {
        this.width = width;
        this.height = height;
        invoke("buildForm");
        invoke("layoutForm");
        return this;
    }

    /** A left click at a point of the screen, as the game dispatches it to the form. */
    boolean click(double x, double y) throws Exception {
        Field instance = clientField();
        Object client = instance.get(null);
        // A click plays its sound through the game client, which this fixture doesn't have.
        instance.set(null, null);
        try {
            //? if >= 1.21.9 {
            return this.mouseClicked(new MouseButtonEvent(x, y,
                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            //?} else {
            /*return this.mouseClicked(x, y, InputConstants.MOUSE_BUTTON_LEFT);
            *///?}
        } finally {
            instance.set(null, client);
        }
    }

    private void invoke(String name) throws Exception {
        Method method = AbstractWaypointPropertiesScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(this);
    }

    private static Field clientField() throws ReflectiveOperationException {
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        return instance;
    }

    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(unsafeClass.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }

    /** The form is never initialized, so there are no movement key bindings for a click to press. */
    @Override
    boolean testMovementKeysDown(int keyCode) {
        return false;
    }

    @Override
    protected List<LeadingRow> leadingRows() {
        return this.leadingRows;
    }

    @Override
    protected @Nullable Component subtitle() {
        return this.subtitle;
    }

    @Override
    protected boolean hasDisplayNameRow() {
        return true;
    }

    @Override
    protected List<TranslucentButton> footerButtons() {
        return List.of();
    }

    @Override
    protected TranslucentButton primaryButton() {
        return null;
    }

    @Override
    protected WaypointFormCheck.Input checkInput() {
        return null;
    }

    @Override
    protected void submit() {
    }

    @Override
    protected Component pendingMessage() {
        return null;
    }

    @Override
    protected void refreshButtons(boolean modal, boolean locked, boolean canSubmit, boolean changed) {
    }
}
