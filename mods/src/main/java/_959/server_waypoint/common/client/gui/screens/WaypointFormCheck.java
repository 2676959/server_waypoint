package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.text.FormattedTextHelper;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.text.FormattedTextHelper.MAX_KEYWORDS;
import static _959.server_waypoint.text.FormattedTextHelper.MAX_KEYWORD_LENGTH;

/**
 * What the waypoint form checks before it sends anything, with the server's rules. It reports the
 * first problem in the order the messages are listed; the screens turn it into a translated message
 * and highlight its field. It holds no Minecraft classes, so it can be unit tested.
 */
final class WaypointFormCheck {
    /** The keywords field's length: 32 keywords of 64 characters, with a comma and a space between them. */
    static final int MAX_KEYWORDS_TEXT_LENGTH = MAX_KEYWORDS * MAX_KEYWORD_LENGTH + (MAX_KEYWORDS - 1) * 2;

    private WaypointFormCheck() {
    }

    /** How a problem shows: a hint or an error stops the form from being sent, a note doesn't. */
    enum Kind {
        HINT,
        ERROR,
        NOTE
    }

    /** The field an error points at, drawn with a danger outline. */
    enum Field {
        NONE,
        NAME,
        DISPLAY_NAME,
        KEYWORDS,
        DESCRIPTION
    }

    /** The translation each problem is shown with, in the order the checks run. */
    enum Message {
        CHOOSE_DIMENSION("waypoint.form.status.choose_dimension"),
        ENTER_LIST("waypoint.form.status.enter_list"),
        ENTER_NAME("waypoint.form.status.enter_name"),
        NAME_TAKEN("waypoint.form.status.name_taken"),
        INVALID_DISPLAY_NAME("waypoint.edit.error.invalid_display_text"),
        DUPLICATE_KEYWORD("waypoint.form.status.duplicate_keyword"),
        TOO_MANY_KEYWORDS("waypoint.form.status.too_many_keywords"),
        KEYWORD_TOO_LONG("waypoint.form.status.keyword_too_long"),
        INVALID_DESCRIPTION("waypoint.form.status.invalid_description"),
        NEW_LIST("waypoint.form.status.new_list");

        private final String translationKey;

        Message(String translationKey) {
            this.translationKey = translationKey;
        }

        String translationKey() {
            return this.translationKey;
        }
    }

    /**
     * The values in the form. {@code displayName} matters only on Edit. {@code savedName} is the name
     * the edited waypoint has now, which it may keep; it is null on Add.
     */
    record Input(
            boolean add,
            String dimension,
            String list,
            String name,
            String displayName,
            String keywords,
            String description,
            @Nullable String savedName
    ) {
    }

    /** The waypoint data a check reads, from the client's synced or integrated-server files. */
    interface Lookup {
        boolean listExists(String dimension, String list);

        /** Whether the list has a waypoint with exactly this name: the server's rule, so case counts. */
        boolean hasWaypoint(String dimension, String list, String name);

        /** The list's display name, or {@code list} itself when it has none or doesn't exist. */
        String listDisplayName(String dimension, String list);
    }

    /** One problem: its message, how it shows, the field to highlight and the message's arguments. */
    record Problem(Message message, Kind kind, Field field, List<String> arguments) {
        boolean blocks() {
            return this.kind != Kind.NOTE;
        }
    }

    /**
     * The first hint or error, or else the note, or null when the form can be sent. Checks run on every
     * edit and every tick, because the synced data can change while the form is open.
     */
    static @Nullable Problem firstProblem(Input input, Lookup data) {
        if (input.add()) {
            if (input.dimension().isEmpty()) {
                return hint(Message.CHOOSE_DIMENSION);
            }
            if (input.list().isEmpty()) {
                return hint(Message.ENTER_LIST);
            }
        }
        if (input.name().isEmpty()) {
            return hint(Message.ENTER_NAME);
        }
        if (!input.name().equals(input.savedName()) && data.hasWaypoint(input.dimension(), input.list(), input.name())) {
            return error(Message.NAME_TAKEN, Field.NAME,
                    data.listDisplayName(input.dimension(), input.list()), input.name());
        }
        if (!input.add() && !FormattedTextHelper.isValidInput(input.displayName())) {
            return error(Message.INVALID_DISPLAY_NAME, Field.DISPLAY_NAME);
        }
        List<String> keywords = FormattedTextHelper.parseKeywords(input.keywords());
        if (FormattedTextHelper.hasDuplicateKeywords(keywords)) {
            return error(Message.DUPLICATE_KEYWORD, Field.KEYWORDS, firstRepeated(keywords));
        }
        if (keywords.size() > MAX_KEYWORDS) {
            return error(Message.TOO_MANY_KEYWORDS, Field.KEYWORDS, Integer.toString(MAX_KEYWORDS));
        }
        for (String keyword : keywords) {
            if (keyword.length() > MAX_KEYWORD_LENGTH) {
                return error(Message.KEYWORD_TOO_LONG, Field.KEYWORDS, Integer.toString(MAX_KEYWORD_LENGTH));
            }
        }
        if (!FormattedTextHelper.isValidInput(input.description())) {
            return error(Message.INVALID_DESCRIPTION, Field.DESCRIPTION);
        }
        if (input.add() && !data.listExists(input.dimension(), input.list())) {
            return new Problem(Message.NEW_LIST, Kind.NOTE, Field.NONE, List.of(input.list()));
        }
        return null;
    }

    /** The first keyword that repeats an earlier one, ignoring case, as {@code hasDuplicateKeywords} does. */
    private static String firstRepeated(List<String> keywords) {
        Set<String> seen = new HashSet<>();
        for (String keyword : keywords) {
            if (!seen.add(keyword.toLowerCase(Locale.ROOT))) {
                return keyword;
            }
        }
        return "";
    }

    private static Problem hint(Message message) {
        return new Problem(message, Kind.HINT, Field.NONE, List.of());
    }

    private static Problem error(Message message, Field field, String... arguments) {
        return new Problem(message, Kind.ERROR, field, List.of(arguments));
    }
}
