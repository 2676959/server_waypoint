package _959.server_waypoint.common.util;

//? if <= 1.20.1
/*import _959.server_waypoint.access.PlayerLocaleAccessor;*/
import net.kyori.adventure.translation.Translator;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class PlayerLocaleHelper {
    private PlayerLocaleHelper() {
    }

    public static Locale forPlayer(ServerPlayer player) {
        //? if <= 1.20.1 {
        /*String language = ((PlayerLocaleAccessor) player).sw$getLocale();
        *///?} else {
        String language = player.clientInformation().language();
        //?}
        return fromLanguage(language);
    }

    static Locale fromLanguage(@Nullable String language) {
        if (language == null || language.isBlank()) {
            return Locale.getDefault();
        }
        Locale locale = Translator.parseLocale(language);
        return locale == null ? Locale.getDefault() : locale;
    }
}
