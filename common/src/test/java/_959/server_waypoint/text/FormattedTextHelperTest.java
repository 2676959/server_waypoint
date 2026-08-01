package _959.server_waypoint.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
