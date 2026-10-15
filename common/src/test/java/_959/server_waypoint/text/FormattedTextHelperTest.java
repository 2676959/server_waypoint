package _959.server_waypoint.text;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormattedTextHelperTest {
    @Test
    void parsesPlainAndMinecraftJsonText() {
        assertEquals("Plain", FormattedTextHelper.plainText("Plain"));
        assertEquals("JSON string", FormattedTextHelper.plainText("\"JSON string\""));
        assertEquals(
                "Red text",
                FormattedTextHelper.plainText(
                        "{\"text\":\"Red\",\"color\":\"red\",\"extra\":[{\"text\":\" text\"}]}"
                )
        );
    }

    @Test
    void validatesJsonLookingInputWithoutRejectingOrdinaryText() {
        assertTrue(FormattedTextHelper.isValidInput("ordinary text"));
        assertTrue(FormattedTextHelper.isValidInput("{\"text\":\"valid\"}"));
        assertFalse(FormattedTextHelper.isValidInput("{not valid json}"));
    }

    @Test
    void parseKeywordsSplitsOnCommasAndTrimsEachKeyword() {
        assertEquals(List.of("home", "base", "farm"), FormattedTextHelper.parseKeywords("home,  base ,farm"));
    }

    @Test
    void parseKeywordsDropsEmptyEntries() {
        assertEquals(List.of("home", "base"), FormattedTextHelper.parseKeywords(",home,, ,base,"));
    }

    @Test
    void parseKeywordsReturnsNoKeywordsForAnEmptyBlankOrMissingString() {
        assertEquals(List.of(), FormattedTextHelper.parseKeywords(""));
        assertEquals(List.of(), FormattedTextHelper.parseKeywords("   "));
        assertEquals(List.of(), FormattedTextHelper.parseKeywords(null));
    }

    @Test
    void parseKeywordsKeepsTheCaseAndInnerSpacesOfEachKeyword() {
        assertEquals(List.of("Home Base", "NETHER"), FormattedTextHelper.parseKeywords(" Home Base , NETHER"));
    }

    @Test
    void parseKeywordsReturnsAListThatCannotBeChanged() {
        List<String> keywords = FormattedTextHelper.parseKeywords("a, b");

        assertThrows(UnsupportedOperationException.class, () -> keywords.add("c"));
    }
}
