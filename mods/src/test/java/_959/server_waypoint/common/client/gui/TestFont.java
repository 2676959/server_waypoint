package _959.server_waypoint.common.client.gui;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * A font for unit tests, which have no glyphs to measure: every character is
 * {@link #CHARACTER_WIDTH} pixels wide, a line is 9 pixels high, and text never wraps.
 */
public final class TestFont extends Font {
    public static final int CHARACTER_WIDTH = 6;

    public TestFont() {
        // No glyph source: every method that would read one is overridden below.
        //? if >= 1.21.9 {
        super((Font.Provider) null);
        //?} else {
        /*super(null, false);
        *///?}
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

    @Override
    public List<FormattedCharSequence> split(FormattedText text, int maxWidth) {
        return List.of(FormattedCharSequence.forward(text.getString(), Style.EMPTY));
    }
}
