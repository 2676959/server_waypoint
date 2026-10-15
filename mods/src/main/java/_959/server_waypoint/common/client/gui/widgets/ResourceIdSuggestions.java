package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.util.NamespacedId;
import java.util.Locale;
import net.minecraft.commands.SharedSuggestionProvider;
import org.jetbrains.annotations.Nullable;

/** Vanilla resource-ID matching shared by item-icon and dimension inputs. */
final class ResourceIdSuggestions {
    private ResourceIdSuggestions() {
    }

    static boolean matches(String suggestion, String query) {
        try {
            return matches(NamespacedId.parse(suggestion), query);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    static boolean matches(NamespacedId id, String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        // Same namespace/path branches as vanilla SharedSuggestionProvider.filterResources.
        return needle.indexOf(':') >= 0
                ? SharedSuggestionProvider.matchesSubStr(needle, id.toString())
                : SharedSuggestionProvider.matchesSubStr(needle, id.namespace())
                        || SharedSuggestionProvider.matchesSubStr(needle, id.path());
    }

    static @Nullable NamespacedId parseInput(String value) {
        try {
            return NamespacedId.parse(value.indexOf(':') >= 0 ? value : "minecraft:" + value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
