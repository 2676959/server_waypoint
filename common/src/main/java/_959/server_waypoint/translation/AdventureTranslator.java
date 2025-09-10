package _959.server_waypoint.translation;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.Translator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Locale;

import static _959.server_waypoint.translation.LanguageFilesManager.getTranslation;

public class AdventureTranslator implements Translator {
    @Override
    public @NotNull Key name() {
        return Key.key("server_waypoint:lang");
    }

    @Override
    public @Nullable MessageFormat translate(@NotNull String key, @NotNull Locale locale) {
        String translation = getTranslation(locale, key);
        if (translation == null) {
            return null;
        }
        return new MessageFormat(translation, locale);
    }
}
