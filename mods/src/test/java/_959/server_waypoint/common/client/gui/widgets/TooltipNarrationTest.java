//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
//? if >= 26.3
/*import net.minecraft.client.gui.narration.NarrationTrigger;*/
import net.minecraft.client.gui.narration.NarrationThunk;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TooltipNarrationTest {
    private static final Component TIP = Component.literal("tip");

    /** Skips constructors requiring the Minecraft client; the test only exercises narration. */
    private static <T> T allocate(Class<T> type) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return type.cast(unsafeClass.getMethod("allocateInstance", Class.class)
                    .invoke(unsafeField.get(null), type));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to create a narration test double", e);
        }
    }

    private static List<Component> hints(Consumer<NarrationElementOutput> narration) {
        RecordingOutput output = new RecordingOutput();
        narration.accept(output);
        return output.hints();
    }

    private static void assertNarratesTheTooltipExactlyWhenSet(ShiftableClickableWidget widget,
                                                              Consumer<NarrationElementOutput> narration) {
        assertEquals(List.of(), hints(narration));
        widget.setTooltip(TIP);
        assertEquals(List.of(TIP), hints(narration));
        widget.setTooltip((Component) null);
        assertEquals(List.of(), hints(narration));
    }

    @Test
    void translucentButtonNarratesItsTooltip() {
        // The constructor reads the game's font, which unit tests don't have, so skip it. updateNarration
        // would then read vanilla's tooltip holder, which the skipped constructor leaves unset, so call
        // the button's own narration instead.
        TranslucentButton button = allocate(TranslucentButton.class);
        assertNarratesTheTooltipExactlyWhenSet(button, button::updateWidgetNarration);
    }

    @Test
    void iconButtonNarratesItsTooltip() {
        IconButton button = new IconButton(0, 0, 16, 16, Component.literal("Add"), null, () -> {
        });
        assertNarratesTheTooltipExactlyWhenSet(button, button::updateNarration);
    }

    @Test
    void colorSquareButtonNarratesItsTooltip() {
        ColorSquareButton button = new ColorSquareButton(0, 0, 10, () -> {
        });
        assertNarratesTheTooltipExactlyWhenSet(button, button::updateNarration);
    }

    @Test
    void dropdownNarratesItsTooltipAfterItsButtonText() {
        TestDropdown dropdown = new TestDropdown();
        assertNarratesTheTooltipExactlyWhenSet(dropdown, dropdown::updateNarration);
        dropdown.setTooltip(TIP);
        RecordingOutput output = new RecordingOutput();
        dropdown.updateNarration(output);
        assertEquals(List.of(NarratedElementType.TITLE, NarratedElementType.USAGE, NarratedElementType.HINT), output.types());
    }

    @Test
    void menuItemNarratesItsTooltipAfterItsButtonText() {
        TestMenuItem item = new TestMenuItem();
        assertNarratesTheTooltipExactlyWhenSet(item, item::updateNarration);
        item.setTooltip(TIP);
        RecordingOutput output = new RecordingOutput();
        item.updateNarration(output);
        assertEquals(List.of(NarratedElementType.TITLE, NarratedElementType.USAGE, NarratedElementType.HINT), output.types());
    }

    /** Records each element's type and the component as given, without resolving any text. */
    private static final class RecordingOutput implements NarrationElementOutput {
        private final List<NarratedElementType> types = new ArrayList<>();
        private final List<Component> hints = new ArrayList<>();

        @Override
        public void add(NarratedElementType type, Component contents) {
            this.types.add(type);
            if (type == NarratedElementType.HINT) {
                this.hints.add(contents);
            }
        }

        @Override
        public void add(NarratedElementType type, NarrationThunk<?> contents) {
            throw new AssertionError("The narration was resolved to text");
        }

        @Override
        public NarrationElementOutput nest() {
            return this;
        }

        //? if >= 26.3 {
        /*@Override
        public NarrationTrigger narrationTrigger() {
            return NarrationTrigger.MOUSE;
        }
        *///?}

        List<NarratedElementType> types() {
            return this.types;
        }

        List<Component> hints() {
            return this.hints;
        }
    }

    private static final class TestDropdown extends AbstractDropdownMenuWidget {
        private TestDropdown() {
            super(0, 0, 16, 16, Component.literal("Dropdown"), LayoutFlow.Orientation.VERTICAL, LayoutFlow.Direction.FORWARD);
        }

        @Override
        protected void renderDropdownControl(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        }
    }

    private static final class TestMenuItem extends AbstractDropdownMenuWidget.AbstractMenuItem {
        private TestMenuItem() {
            super(16, 16, Component.literal("Item"));
        }

        @Override
        protected void onSelected() {
        }

        @Override
        protected void renderMenuItem(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        }
    }
}
