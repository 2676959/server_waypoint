package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.gui.widgets.TranslucentTextField;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointFormStateTest {
    @BeforeAll
    static void bootstrapRegistries() {
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        } catch (Throwable failure) {
            // Forge-family registries need a loader that plain JUnit does not supply.
            Assumptions.abort("Minecraft could not be bootstrapped in this test runtime: " + failure);
        }
    }

    @Test
    void openingEditKeepsSavedTextLongerThanVanillasDefaultLimit() throws ReflectiveOperationException {
        String name = "Waypoint name with more than thirty two characters";
        String displayName = "Display name with more than thirty two characters";
        List<String> keywords = List.of("first keyword with many characters", "second keyword");
        String description = "A description with more than thirty two characters";
        SimpleWaypoint waypoint = new SimpleWaypoint(name, displayName, "X", new WaypointPos(1, 2, 3),
                0x123456, 0, true, keywords, description);

        // Supply the font and an empty camera used by construction, without starting a game client.
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        Minecraft client = allocate(Minecraft.class);
        Field font = Minecraft.class.getDeclaredField("font");
        font.setAccessible(true);
        font.set(client, new TestFont());
        GameRenderer renderer = allocate(GameRenderer.class);
        Field camera = GameRenderer.class.getDeclaredField("mainCamera");
        camera.setAccessible(true);
        camera.set(renderer, allocate(Camera.class));
        Field gameRenderer = Minecraft.class.getDeclaredField("gameRenderer");
        gameRenderer.setAccessible(true);
        gameRenderer.set(client, renderer);
        instance.set(null, client);
        try {
            TestForm form = new TestForm(waypoint);
            assertAll(
                    () -> assertEquals(name, form.nameEditBox.getValue()),
                    () -> assertEquals(displayName, form.displayNameEditBox.getValue()),
                    () -> assertEquals(String.join(", ", keywords), form.keywordsEditBox.getValue()),
                    () -> assertEquals(description, form.descriptionEditBox.getValue())
            );
        } finally {
            instance.set(null, previous);
        }
    }

    @Test
    void aPendingFormPreventsTypingEvenWhenVanillaIgnoresActive() {
        LegacyFocusedField field = new LegacyFocusedField();
        field.setValue("Home");

        AbstractWaypointPropertiesScreen.setControlActive(field, false);
        field.charTyped('x', 0);
        assertEquals("Home", field.getValue());

        AbstractWaypointPropertiesScreen.setControlActive(field, true);
        field.charTyped('x', 0);
        assertEquals("Homex", field.getValue());
    }

    /** Uses the real EditBox input path with 1.20.1's predicate that ignores active. */
    private static final class LegacyFocusedField extends TranslucentTextField {
        private boolean changingEditability;

        private LegacyFocusedField() {
            super(0, 0, 60, Component.literal("Name"), new TestFont());
        }

        @Override
        public boolean isFocused() {
            return !this.changingEditability;
        }

        /** Skip the game's IME notification while retaining vanilla's editability state. */
        @Override
        public void setEditable(boolean editable) {
            this.changingEditability = true;
            try {
                super.setEditable(editable);
            } finally {
                this.changingEditability = false;
            }
        }

        @Override
        public boolean canConsumeInput() {
            boolean wasActive = this.active;
            this.active = true;
            try {
                return super.canConsumeInput();
            } finally {
                this.active = wasActive;
            }
        }
    }

    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(unsafeClass.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }

    /** Runs the real shared form constructor without client data, key mappings or rendering. */
    private static final class TestForm extends AbstractWaypointPropertiesScreen {
        private TestForm(SimpleWaypoint waypoint) {
            super(null, Component.literal("Edit"), "minecraft:overworld", "Base", waypoint);
        }

        @Override
        protected List<LeadingRow> leadingRows() {
            return List.of();
        }

        @Override
        protected Component subtitle() {
            return null;
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
}
