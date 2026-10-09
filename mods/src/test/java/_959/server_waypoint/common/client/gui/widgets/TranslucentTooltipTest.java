package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslucentTooltipTest {
    @Test
    void wrapsAt170Pixels() {
        WrappingFont font = new WrappingFont();
        TranslucentTooltip tooltip = new TranslucentTooltip(font);
        tooltip.setMessage(Component.literal("x".repeat(60)));
        assertEquals(170, font.lastMaxWidth);
        // 28 characters fit in 170 pixels: lines of 28, 28 and 4.
        assertEquals(168, tooltip.getWidth());
        assertEquals(26, tooltip.getHeight());
    }

    @Test
    void newlineStartsANewLine() {
        TranslucentTooltip tooltip = new TranslucentTooltip(new WrappingFont());
        tooltip.setMessage(Component.literal("abc\ndefgh"));
        assertEquals(30, tooltip.getWidth());
        assertEquals(17, tooltip.getHeight());
    }

    @Test
    void oneLineIsEightPixelsHigh() {
        TranslucentTooltip tooltip = new TranslucentTooltip(new WrappingFont());
        tooltip.setMessage(Component.literal("abc"));
        assertEquals(18, tooltip.getWidth());
        assertEquals(8, tooltip.getHeight());
    }

    @Test
    void boxExtendsFourPixelsPastTheText() {
        TranslucentTooltip tooltip = new TranslucentTooltip(new WrappingFont());
        tooltip.setMessage(Component.literal("abc"));
        tooltip.setPosition(20, 30);
        assertEquals(16, tooltip.getVisualX());
        assertEquals(26, tooltip.getVisualY());
        assertEquals(26, tooltip.getVisualWidth());
        assertEquals(16, tooltip.getVisualHeight());
    }

    @Test
    void equalMessageIsNotWrappedAgain() {
        WrappingFont font = new WrappingFont();
        TranslucentTooltip tooltip = new TranslucentTooltip(font);
        tooltip.setMessage(Component.literal("abc"));
        int splits = font.splitCalls;
        tooltip.setMessage(Component.literal("abc"));
        assertEquals(splits, font.splitCalls);
        tooltip.setMessage(Component.literal("abd"));
        assertEquals(splits + 1, font.splitCalls);
    }

    @Test
    void languageChangeWrapsAnEqualMessageAgain() {
        WrappingFont font = new WrappingFont();
        TranslucentTooltip tooltip = new TranslucentTooltip(font);
        tooltip.setMessage(Component.literal("abc"));
        int splits = font.splitCalls;
        Language original = Language.getInstance();
        Language.inject(new DelegatingLanguage(original));
        try {
            tooltip.setMessage(Component.literal("abc"));
            assertEquals(splits + 1, font.splitCalls);
        } finally {
            Language.inject(original);
        }
    }

    @Test
    void messagesWithoutCharactersAreEmpty() {
        TranslucentTooltip tooltip = new TranslucentTooltip(new WrappingFont());
        tooltip.setMessage(Component.empty());
        assertTrue(tooltip.isEmpty());
        tooltip.setMessage(Component.literal("\n"));
        assertTrue(tooltip.isEmpty());
        tooltip.setMessage(Component.literal("a"));
        assertFalse(tooltip.isEmpty());
    }

    /**
     * A font whose characters are {@link TestFont#CHARACTER_WIDTH} pixels wide and which wraps: at every
     * newline, and into lines of whole characters that fit the maximum width. It counts its splits.
     */
    private static final class WrappingFont extends Font {
        int splitCalls;
        int lastMaxWidth;

        private WrappingFont() {
            // No glyph source: every method that would read one is overridden below.
            //? if >= 1.21.9 {
            super((Font.Provider) null);
            //?} else {
            /*super(null, false);
            *///?}
        }

        @Override
        public int width(String text) {
            return text.length() * TestFont.CHARACTER_WIDTH;
        }

        @Override
        public int width(FormattedText text) {
            return this.width(text.getString());
        }

        @Override
        public int width(FormattedCharSequence text) {
            int[] characters = {0};
            text.accept((index, style, codePoint) -> {
                characters[0]++;
                return true;
            });
            return characters[0] * TestFont.CHARACTER_WIDTH;
        }

        @Override
        public List<FormattedCharSequence> split(FormattedText text, int maxWidth) {
            this.splitCalls++;
            this.lastMaxWidth = maxWidth;
            String characters = text.getString();
            if (characters.isEmpty()) {
                return List.of();
            }
            int perLine = Math.max(1, maxWidth / TestFont.CHARACTER_WIDTH);
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (String piece : characters.split("\n", -1)) {
                if (piece.isEmpty()) {
                    lines.add(FormattedCharSequence.EMPTY);
                    continue;
                }
                for (int start = 0; start < piece.length(); start += perLine) {
                    String chunk = piece.substring(start, Math.min(piece.length(), start + perLine));
                    lines.add(FormattedCharSequence.forward(chunk, Style.EMPTY));
                }
            }
            return lines;
        }
    }

    /** A language that answers like the one it wraps but is another instance. */
    private static final class DelegatingLanguage extends Language {
        private final Language delegate;

        private DelegatingLanguage(Language delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getOrDefault(String key, String defaultValue) {
            return this.delegate.getOrDefault(key, defaultValue);
        }

        @Override
        public boolean has(String key) {
            return this.delegate.has(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return this.delegate.isDefaultRightToLeft();
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return this.delegate.getVisualOrder(text);
        }
    }
}
