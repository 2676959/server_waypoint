package _959.server_waypoint.text.chat;

/**
 * Advances of the vanilla bitmap font in GUI pixels, including the 1 px gap, as the 26.3 client
 * draws them (the 1.20.2 font is the same). Every glyph of its sheets is known: ASCII, the glyphs the
 * chat kit uses and the letters of other languages, such as accented Latin and Hebrew. Anything else,
 * such as Chinese, falls back to Unifont and is assumed to be 9 px wide, the advance of a full-width
 * Unifont glyph.
 */
public final class ChatFont {
    public static final int CHAT_WIDTH = 320;
    public static final int CHAT_LINES = 20;
    public static final int UNKNOWN_ADVANCE = 9;
    /** Advances of ' ' through '~'. */
    private static final String ASCII_ADVANCES =
            "42466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247";

    private ChatFont() {
    }

    public static boolean isVanillaGlyph(int codePoint) {
        return codePoint >= ' ' && codePoint <= '~' || ChatFontSheets.ADVANCES.containsKey(codePoint);
    }

    public static int advance(int codePoint) {
        if (codePoint >= ' ' && codePoint <= '~') {
            return ASCII_ADVANCES.charAt(codePoint - ' ') - '0';
        }
        return ChatFontSheets.ADVANCES.getOrDefault(codePoint, UNKNOWN_ADVANCE);
    }

    public static int width(String text) {
        return text.codePoints().map(ChatFont::advance).sum();
    }
}
