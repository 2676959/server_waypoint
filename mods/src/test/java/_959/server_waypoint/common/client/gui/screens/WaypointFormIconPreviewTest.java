//? if >=26 {
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.util.NamespacedId;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaypointFormIconPreviewTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void longInitialsGrowLeftAndLeaveTheInputGapClear() throws Exception {
        withForm(form -> {
            form.initialsEditBox.setValue("ABCD");
            RecordingGraphics graphics = draw(form);
            Fill badge = graphics.fills.get(0);
            SpacerElement slot = previewSlot(form);
            assertEquals(26, badge.right - badge.left, 0.001F);
            assertEquals(slot.getX() + slot.getWidth(), badge.right, 0.001F);
            assertTrue(badge.left < slot.getX(), "extra width must use space left of the preview");
        });
    }

    @Test
    void badgeChoosesTextColorFromItsCurrentBackground() throws Exception {
        withForm(form -> {
            form.initialsEditBox.setValue("A");
            form.colorPickerButton.setColor(0xFF112233);
            RecordingGraphics graphics = draw(form);
            assertEquals(0xFFFFFFFF, graphics.texts.get(0).color);
            assertEquals(9, graphics.fills.get(0).bottom - graphics.fills.get(0).top, 0.001F);
            assertEquals(0xFF112233, graphics.fills.get(0).color);
            form.colorPickerButton.setColor(0xFFFFFFFF);
            graphics = draw(form);
            assertEquals(0xFF000000, graphics.texts.get(0).color, "light badges need world contrast");
        });
    }

    @Test
    void selectedItemShowsOnlyTheIconInTheOriginalPreviewSlot() throws Exception {
        withForm(form -> {
            // The game binds item components after loading data packs; drawing bounds need no components.
            var holder = (Holder.Reference<Item>)
                    BuiltInRegistries.ITEM.wrapAsHolder(Items.DIAMOND);
            Field components = Holder.Reference.class.getDeclaredField("components");
            components.setAccessible(true);
            Object previous = components.get(holder);
            holder.bindComponents(DataComponentMap.EMPTY);
            try {
                form.initialsEditBox.setValue("ABCD");
                form.iconPicker.setSelectedIcon(new NamespacedId("minecraft", "diamond"));
                RecordingGraphics graphics = draw(form);
                assertTrue(graphics.texts.isEmpty(), "selected icons must hide the initials badge");
                assertTrue(graphics.fills.isEmpty());
                assertEquals(1, graphics.items.size());
                Fill item = graphics.items.get(0);
                assertEquals(100, item.left, 0.001F);
                assertEquals(40, item.top, 0.001F);
                assertEquals(11, item.right - item.left, 0.001F);
                assertEquals(11, item.bottom - item.top, 0.001F);
                assertEquals(11, previewSlot(form).getHeight());
            } finally {
                components.set(holder, previous);
            }
        });
    }

    private static RecordingGraphics draw(TestForm form) throws Exception {
        SpacerElement slot = previewSlot(form);
        slot.setX(100);
        slot.setY(40);
        RecordingGraphics graphics = RecordingGraphics.create();
        var draw = AbstractWaypointPropertiesScreen.class.getDeclaredMethod("drawIconPreview",
                GuiGraphicsExtractor.class, int.class, int.class);
        draw.setAccessible(true);
        draw.invoke(form, graphics, -100, -100);
        return graphics;
    }

    private static SpacerElement previewSlot(TestForm form) throws Exception {
        Field field = AbstractWaypointPropertiesScreen.class.getDeclaredField("iconPreview");
        field.setAccessible(true);
        return (SpacerElement) field.get(form);
    }

    @FunctionalInterface
    private interface Check {
        void run(TestForm form) throws Exception;
    }

    private static void withForm(Check check) throws Exception {
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
            check.run(new TestForm());
        } finally {
            instance.set(null, previous);
        }
    }

    private record Fill(float left, float top, float right, float bottom, int color) { }
    private record Text(String text, int color, boolean shadow) { }

    /** Records transformed draw bounds at the GPU boundary; the form and its controls stay real. */
    private static final class RecordingGraphics extends GuiGraphicsExtractor {
        private List<Fill> fills;
        private List<Fill> items;
        private List<Text> texts;
        private Matrix3x2fStack matrices;

        private RecordingGraphics() {
            super(null, null, 0, 0);
        }

        private static RecordingGraphics create() throws Exception {
            RecordingGraphics graphics = allocate(RecordingGraphics.class);
            graphics.fills = new ArrayList<>();
            graphics.items = new ArrayList<>();
            graphics.texts = new ArrayList<>();
            graphics.matrices = new Matrix3x2fStack(16);
            return graphics;
        }

        private Fill bounds(int left, int top, int right, int bottom, int color) {
            Vector2f start = matrices.transformPosition(left, top, new Vector2f());
            Vector2f end = matrices.transformPosition(right, bottom, new Vector2f());
            return new Fill(start.x, start.y, end.x, end.y, color);
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            fills.add(bounds(left, top, right, bottom, color));
        }

        @Override
        public void text(Font font, String text, int x, int y, int color, boolean shadow) {
            texts.add(new Text(text, color, shadow));
        }

        @Override
        public void fakeItem(ItemStack stack, int x, int y) {
            items.add(bounds(x, y, x + 16, y + 16, 0));
        }

        @Override
        public Matrix3x2fStack pose() {
            return matrices;
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
        private TestForm() {
            super(null, Component.literal("Edit"), "minecraft:overworld", "Base", null);
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
//?}
