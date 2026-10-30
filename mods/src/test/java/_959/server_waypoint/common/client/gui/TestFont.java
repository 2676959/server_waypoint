package _959.server_waypoint.common.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * A font for unit tests, which have no glyphs to measure: every character is
 * {@link #CHARACTER_WIDTH} pixels wide and a line is 9 pixels high. Text never wraps, except with
 * {@link #wrapping()}.
 */
public final class TestFont extends Font {
    public static final int CHARACTER_WIDTH = 6;
    private final boolean wraps;

    public TestFont() {
        this(false);
    }

    private TestFont(boolean wraps) {
        // No glyph source: every method that would read one is overridden below.
        //? if >= 1.21.9 {
        super((Font.Provider) null);
        //?} else {
        /*super(null, false);
        *///?}
        this.wraps = wraps;
    }

    /** A font that breaks text into lines of as many whole characters as fit, as a label wraps in game. */
    public static TestFont wrapping() {
        return new TestFont(true);
    }

    @Override
    public int width(String text) {
        return text.length() * CHARACTER_WIDTH;
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
        return characters[0] * CHARACTER_WIDTH;
    }

    @Override
    public String plainSubstrByWidth(String text, int maxWidth) {
        return this.plainSubstrByWidth(text, maxWidth, false);
    }

    /** The whole characters that fit, from the start of the text or, when reversed, from its end. */
    @Override
    public String plainSubstrByWidth(String text, int maxWidth, boolean reverse) {
        int fitting = Math.min(text.length(), Math.max(0, maxWidth) / CHARACTER_WIDTH);
        return reverse ? text.substring(text.length() - fitting) : text.substring(0, fitting);
    }

    /** The whole characters that fit, from the start, without their styles. */
    @Override
    public FormattedText substrByWidth(FormattedText text, int maxWidth) {
        String characters = text.getString();
        int fitting = Math.min(characters.length(), Math.max(0, maxWidth) / CHARACTER_WIDTH);
        return FormattedText.of(characters.substring(0, fitting));
    }

    @Override
    public List<FormattedCharSequence> split(FormattedText text, int maxWidth) {
        String characters = text.getString();
        if (!this.wraps || characters.isEmpty()) {
            return List.of(FormattedCharSequence.forward(characters, Style.EMPTY));
        }
        int perLine = Math.max(1, maxWidth / CHARACTER_WIDTH);
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (int start = 0; start < characters.length(); start += perLine) {
            lines.add(FormattedCharSequence.forward(
                    characters.substring(start, Math.min(characters.length(), start + perLine)), Style.EMPTY));
        }
        return lines;
    }
}
