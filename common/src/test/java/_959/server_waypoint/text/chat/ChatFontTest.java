package _959.server_waypoint.text.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static net.kyori.adventure.text.Component.text;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFontTest {
    @Test
    void asciiAdvancesIncludeTheOnePixelGap() {
        assertEquals(4, ChatFont.advance(' '));
        assertEquals(4, ChatFont.advance('['));
        assertEquals(4, ChatFont.advance(']'));
        assertEquals(6, ChatFont.advance('a'));
        assertEquals(6, ChatFont.advance('W'));
        assertEquals(2, ChatFont.advance('i'));
        assertEquals(3, ChatFont.advance('l'));
        assertEquals(4, ChatFont.advance('t'));
        assertEquals(7, ChatFont.advance('~'));
    }

    @Test
    void glyphsHaveTheAdvancesTheSpecLists() {
        Map<Character, Integer> glyphs = Map.ofEntries(
                Map.entry('✔', 7), Map.entry('✘', 7), Map.entry('●', 5), Map.entry('⏷', 6),
                Map.entry('↑', 6), Map.entry('↓', 6), Map.entry('‹', 4), Map.entry('›', 4),
                Map.entry('«', 7), Map.entry('»', 7), Map.entry('…', 8), Map.entry('·', 2),
                Map.entry('■', 6), Map.entry('█', 9), Map.entry('✎', 8), Map.entry('×', 6),
                Map.entry('−', 6), Map.entry('+', 6), Map.entry('°', 5));
        glyphs.forEach((glyph, advance) -> {
            assertEquals(advance, ChatFont.advance(glyph), String.valueOf(glyph));
            assertTrue(ChatFont.isVanillaGlyph(glyph), String.valueOf(glyph));
        });
    }

    @Test
    void lettersBeyondAsciiHaveTheAdvancesOfTheirSheets() {
        Map<Character, Integer> glyphs = Map.ofEntries(
                Map.entry('ñ', 6), Map.entry('á', 6), Map.entry('í', 3), Map.entry('Í', 4),
                Map.entry('¿', 6), Map.entry('¡', 2), Map.entry('λ', 6), Map.entry('я', 6),
                Map.entry('א', 6), Map.entry('ג', 5), Map.entry('נ', 4), Map.entry('ו', 2),
                Map.entry('י', 2), Map.entry('ת', 6), Map.entry('▼', 6));
        glyphs.forEach((glyph, advance) -> {
            assertEquals(advance, ChatFont.advance(glyph), String.valueOf(glyph));
            assertTrue(ChatFont.isVanillaGlyph(glyph), String.valueOf(glyph));
        });
    }

    @Test
    void glyphsThatFallBackToUnifontAreNotVanilla() {
        for (char glyph : "⋯▸▾✓✗路".toCharArray()) {
            assertFalse(ChatFont.isVanillaGlyph(glyph), String.valueOf(glyph));
            assertEquals(9, ChatFont.advance(glyph));
        }
        assertFalse(ChatFont.isVanillaGlyph('\n'));
    }

    @Test
    void widthsAddUpTheAdvances() {
        assertEquals(185, ChatFont.width("Sort Default · Name · Distance · Color"));
        assertEquals(212, ChatFont.width("Server Waypoint   Open GUI · Help · Reload"));
        assertEquals(38, ChatFont.width("Añadido"));
        assertEquals(26, ChatFont.width("רשימה"));
    }

    @Test
    void theLayoutCheckRejectsWideTallBoldAndForeignText() {
        assertDoesNotThrow(() -> ChatAssert.assertFitsChat(text("Farms · 7")));
        assertThrows(AssertionError.class, () -> ChatAssert.assertFitsChat(text("x".repeat(60))));
        assertThrows(AssertionError.class, () -> ChatAssert.assertFitsChat(text("line\n".repeat(19) + "line")));
        assertThrows(AssertionError.class, () -> ChatAssert.assertFitsChat(text("Farms").decorate(TextDecoration.BOLD)));
        assertThrows(AssertionError.class, () -> ChatAssert.assertFitsChat(text("Farms ⋯")));
        assertThrows(AssertionError.class, () -> ChatAssert.assertFitsChat(Component.text("Farms").appendNewline()));
    }
}
