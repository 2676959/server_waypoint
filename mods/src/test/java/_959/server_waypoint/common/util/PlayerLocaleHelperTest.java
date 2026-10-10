package _959.server_waypoint.common.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerLocaleHelperTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void missingLanguageUsesTheJvmDefault(String language) {
        assertEquals(Locale.getDefault(), PlayerLocaleHelper.fromLanguage(language));
    }

    @ParameterizedTest
    @CsvSource({"en_us,en-US", "zh_tw,zh-TW", "en_US_POSIX,en-US-POSIX"})
    void preservesTheReportedLanguageCountryAndVariant(String input, String languageTag) {
        assertEquals(Locale.forLanguageTag(languageTag),
                PlayerLocaleHelper.fromLanguage(input));
    }
}
