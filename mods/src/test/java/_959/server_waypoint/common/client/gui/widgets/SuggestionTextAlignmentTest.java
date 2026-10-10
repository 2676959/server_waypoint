//~ gui_graphics_26
//? if >=26 {
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.mixin.EditBoxAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionTextAlignmentTest {
    @Test
    void completionStartsAfterTheVisibleInputWhenTheFieldHasScrolled() throws Exception {
        SuggestingTextInput input = input();
        input.setValue("minecraft:");
        RecordingGraphics graphics = RecordingGraphics.create();

        input.renderTextField(graphics, -100, -100, 0);

        assertTrue(graphics.texts.contains(new TextDraw("craft:", 10, 20)),
                "the real EditBox scrolls off the first four characters");
        assertTrue(graphics.texts.contains(new TextDraw("overworld", 46, 20)),
                "completion must follow the six visible characters, not all ten characters");
    }

    @Test
    void popupTextSharesTheInputViewportAndRestoresItsPrefixAtTheStart() throws Exception {
        SuggestingTextInput input = input();
        input.setValue("minecraft:");
        RecordingGraphics graphics = RecordingGraphics.create();

        input.renderSuggestions(graphics, -100, -100);

        assertEquals(new TextDraw("craft:overworld", 10, 31), graphics.texts.get(0));
        input.moveCursorToStart(false);
        graphics.texts.clear();
        input.renderSuggestions(graphics, -100, -100);
        assertEquals(new TextDraw("minecraft:overworld", 10, 31), graphics.texts.get(0));
        assertTrue(input.acceptHighlightedSuggestion());
        assertEquals("minecraft:overworld", input.getValue(), "clipping must preserve the accepted full value");
    }

    private static SuggestingTextInput input() {
        SuggestingTextInput input = new TestInput();
        input.setMaxLength(Integer.MAX_VALUE);
        input.setSuggestionsProvider(() -> List.of("minecraft:overworld", "minecraft:the_end"));
        return input;
    }

    /** Unit tests run without Mixin; read the real vanilla viewport for the accessor contract. */
    private static final class TestInput extends SuggestingTextInput implements EditBoxAccessor {
        private TestInput() {
            super(10, 20, 36, Component.empty(), new TestFont());
        }

        @Override
        public boolean isFocused() {
            return true;
        }

        @Override
        public boolean canConsumeInput() {
            // 26.3 rendering also updates the live client's IME area; this fixture has no client.
            return false;
        }

        @Override
        public int sw$getDisplayPos() {
            try {
                var field = EditBox.class.getDeclaredField("displayPos");
                field.setAccessible(true);
                return field.getInt(this);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }
    }

    private record TextDraw(String text, int x, int y) {
    }

    /** Records text drawing without needing the Minecraft atlas or a GPU. */
    private static final class RecordingGraphics extends GuiGraphicsExtractor {
        private List<TextDraw> texts;
        private Matrix3x2fStack matrices;

        private RecordingGraphics() {
            super(null, null, 0, 0);
        }

        private static RecordingGraphics create() throws Exception {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            var field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            RecordingGraphics graphics = (RecordingGraphics) unsafeClass.getMethod("allocateInstance", Class.class)
                    .invoke(field.get(null), RecordingGraphics.class);
            graphics.texts = new ArrayList<>();
            graphics.matrices = new Matrix3x2fStack(16);
            return graphics;
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
        }

        @Override
        public Matrix3x2fStack pose() {
            return this.matrices;
        }

        @Override
        public boolean containsPointInScissor(int x, int y) {
            return true;
        }

        @Override
        public void nextStratum() {
        }

        @Override
        public void enableScissor(int left, int top, int right, int bottom) {
        }

        @Override
        public void disableScissor() {
        }

        @Override
        public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
            this.texts.add(new TextDraw(text.getString(), x, y));
        }

        @Override
        public void text(Font font, String text, int x, int y, int color, boolean shadow) {
            this.texts.add(new TextDraw(text, x, y));
        }

        @Override
        public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
            StringBuilder characters = new StringBuilder();
            text.accept((index, style, codePoint) -> {
                characters.appendCodePoint(codePoint);
                return true;
            });
            this.texts.add(new TextDraw(characters.toString(), x, y));
        }
    }
}
//?}
