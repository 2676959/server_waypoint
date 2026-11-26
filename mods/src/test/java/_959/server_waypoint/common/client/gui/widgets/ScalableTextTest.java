package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScalableTextTest {
    /** {@link TestFont#wrapping()} fits ten of its 6-pixel characters on a line this wide. */
    private static final int TEN_CHARACTERS = 60;
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";

    @Test
    void textWidthWithoutWrappingIsTheWholeText() {
        assertEquals(24, new ScalableText(0, 0, Component.literal("abcd"), 0xFFFFFFFF, new TestFont()).getTextWidth());
    }

    @Test
    void textWidthIsScaled() {
        assertEquals(12, new ScalableText(0, 0, Component.literal("abcd"), 0.5F, 0xFFFFFFFF, new TestFont()).getTextWidth());
    }

    @Test
    void textWidthWhileWrappingIsTheWidestLineNotTheMaximumWidth() {
        ScalableText text = new ScalableText(0, 0, Component.literal("abcd"), 1.0F, 0xFFFFFFFF, 100, new TestFont());
        assertEquals(100, text.getWidth());
        assertEquals(24, text.getTextWidth());
    }

    @Test
    void textNeedingMoreLinesThanTheMaximumIsCutAndItsLastLineEndsWithAnEllipsis() {
        List<FormattedCharSequence> lines = ScalableText.wrap(
                TestFont.wrapping(), Component.literal(ALPHABET), TEN_CHARACTERS, 2);

        // Nine characters and the ellipsis fill the second line, and the rest of the alphabet is dropped.
        assertEquals(List.of("abcdefghij", "klmnopqrs…"), plain(lines));
    }

    @Test
    void textThatFitsTheMaximumLinesIsLeftAlone() {
        List<FormattedCharSequence> lines = ScalableText.wrap(
                TestFont.wrapping(), Component.literal("abcdefghijklmno"), TEN_CHARACTERS, 2);

        assertEquals(List.of("abcdefghij", "klmno"), plain(lines));
    }

    @Test
    void textExactlyFillingTheMaximumLinesIsLeftAlone() {
        List<FormattedCharSequence> lines = ScalableText.wrap(
                TestFont.wrapping(), Component.literal("abcdefghijklmnopqrst"), TEN_CHARACTERS, 2);

        assertEquals(List.of("abcdefghij", "klmnopqrst"), plain(lines));
    }

    @Test
    void aMaximumShortensTextThatIsAlreadySet() {
        ScalableText text = new ScalableText(0, 0, Component.literal(ALPHABET), 1.0F, 0xFFFFFFFF,
                TEN_CHARACTERS, TestFont.wrapping());
        assertEquals(3 * 9, text.getHeight());

        text.setMaxLines(2);

        assertEquals(2 * 9, text.getHeight());
    }

    @Test
    void theMaximumStillAppliesAfterTheTextChanges() {
        ScalableText text = new ScalableText(0, 0, Component.literal("abc"), 1.0F, 0xFFFFFFFF,
                TEN_CHARACTERS, TestFont.wrapping());
        text.setMaxLines(2);

        text.setText(Component.literal(ALPHABET));

        assertEquals(2 * 9, text.getHeight());
    }

    private static List<String> plain(List<FormattedCharSequence> lines) {
        List<String> text = new ArrayList<>();
        for (FormattedCharSequence line : lines) {
            StringBuilder characters = new StringBuilder();
            line.accept((index, style, codePoint) -> {
                characters.appendCodePoint(codePoint);
                return true;
            });
            text.add(characters.toString());
        }
        return text;
    }
}
