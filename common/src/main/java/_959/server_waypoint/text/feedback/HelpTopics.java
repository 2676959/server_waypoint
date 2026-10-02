package _959.server_waypoint.text.feedback;

import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/** What each help topic says (spec 12). Usage syntax and examples are commands, so they stay untranslated. */
public final class HelpTopics {
    private HelpTopics() {
    }

    public enum Topic {
        LIST, ADD, EDIT, REMOVE, TP, NAVIGATE, UPLOAD, DOWNLOAD, REMOTE;

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public Component label() {
            return translatable("wp.help.topic." + this.id());
        }

        /** Whether the viewer may use the commands this topic is about. */
        public boolean readableBy(Viewer viewer) {
            return switch (this) {
                case LIST, DOWNLOAD -> true;
                case ADD -> viewer.can(Viewer.Permission.ADD);
                case EDIT -> viewer.can(Viewer.Permission.EDIT);
                case REMOVE -> viewer.can(Viewer.Permission.REMOVE);
                case TP -> viewer.can(Viewer.Permission.TP);
                case NAVIGATE -> viewer.can(Viewer.Permission.NAVIGATE);
                case UPLOAD -> viewer.can(Viewer.Permission.UPLOAD);
                case REMOTE -> viewer.can(Viewer.Permission.REMOTE_LIST) || viewer.can(Viewer.Permission.REMOTE_TP);
            };
        }
    }

    /** A usage line: its syntax, the command a click suggests, and the tooltip that explains it. */
    public record Usage(String syntax, String suggestion, Tooltip tooltip) {
    }

    /**
     * An example command. Braces mark each value it fills in, starting with the name of the argument the
     * value fills: /wp upload {source xaero}.
     */
    public record Example(String markup, String descriptionKey) {
        /** The command as typed, without the braces and argument names. */
        public String command() {
            return this.markup.replaceAll("\\{[a-z]+ ", "").replace("}", "");
        }

        /** For each word of the command, split at spaces, the argument it fills, or null for the command's own words. */
        public List<@Nullable String> arguments() {
            List<@Nullable String> arguments = new ArrayList<>();
            String argument = null;
            for (String word : this.markup.split(" ")) {
                if (word.startsWith("{")) {
                    argument = word.substring(1);
                    continue;
                }
                arguments.add(argument);
                if (word.endsWith("}")) {
                    argument = null;
                }
            }
            return arguments;
        }
    }

    /**
     * The colour of an argument by its type: dimensions and IDs green, coordinates light purple, numbers gold,
     * choices from a fixed set of words dark purple, and text yellow.
     */
    static TextColor argumentColor(String name) {
        return switch (name) {
            case "dimension", "id" -> GREEN;
            case "position" -> LIGHT_PURPLE;
            case "yaw", "number" -> GOLD;
            case "mode", "direction", "view", "method", "source", "property", "topic", "color", "global" -> DARK_PURPLE;
            default -> YELLOW;
        };
    }

    public record Content(List<Usage> usages, List<Example> examples) {
    }

    public static List<Topic> readable(Viewer viewer) {
        return Arrays.stream(Topic.values()).filter(topic -> topic.readableBy(viewer)).toList();
    }

    public static Content content(Topic topic, boolean textDisplay) {
        return switch (topic) {
            case LIST -> new Content(List.of(
                    usage("/wp list [<dimension> [<list>]]", "/wp list ", "wp.help.list.browse",
                            note("wp.help.list.browse.note")),
                    usage("/wp list all", "/wp list all", "wp.help.list.all"),
                    usage("/wp list dimensions", "/wp list dimensions", "wp.help.list.dimensions"),
                    usage("[search <text>] [sort <mode> [order <direction>]] [limit <number>] [view <view>] [page <number>]",
                            "/wp list ", "wp.help.list.options", param("mode", "wp.help.list.options.mode"),
                            param("direction", "wp.help.list.options.direction"), param("view", "wp.help.list.options.view"),
                            param("number", "wp.help.list.options.number"))),
                    List.of(new Example("/wp list all search {text farm}", "wp.help.list.example.search"),
                            new Example("/wp list {dimension minecraft:overworld} {list \"Home Bases\"} sort {mode name}",
                                    "wp.help.list.example.sort")));
            case ADD -> new Content(List.of(
                    usage("/wp add", "/wp add", "wp.help.add.picker"),
                    usage("/wp add <dimension> <list>", "/wp add ", "wp.help.add.list", note("wp.help.add.list.note")),
                    usage("/wp add <position> <list> <name> [<initials> <color> <yaw> <global> [<keywords> [<description>]] [icon <id>]]",
                            "/wp add ~ ~ ~ ", "wp.help.add.here", note("wp.help.add.here.note"),
                            param("color", "wp.help.add.color.note"), param("global", "wp.help.add.global.note")),
                    usage("/wp add <dimension> <list> <position> <name> [<initials> <color> <yaw> <global> [<keywords> [<description>]] [icon <id>]]",
                            "/wp add ", "wp.help.add.elsewhere", note("wp.help.add.here.note"))),
                    List.of(new Example("/wp add {position ~ ~ ~} {list \"Home Bases\"} {name \"Main Home\"}",
                                    "wp.help.add.example.here"),
                            new Example("/wp add {dimension minecraft:overworld} {list \"Home Bases\"}", "wp.help.add.example.list")));
            case EDIT -> new Content(List.of(
                    usage("/wp edit waypoint <dimension> <list> <waypoint> set <property> [<value>]", "/wp edit waypoint ",
                            "wp.help.edit.waypoint", param("property", "wp.help.edit.properties"),
                            note("wp.help.edit.pickers")),
                    usage("/wp edit waypoint <dimension> <list> <waypoint> clear <property>", "/wp edit waypoint ",
                            "wp.help.edit.clear"),
                    usage("/wp edit list <dimension> <list> set <property> <value>", "/wp edit list ", "wp.help.edit.list"),
                    usage("/wp edit list <dimension> <list> clear display-name", "/wp edit list ", "wp.help.edit.list.clear")),
                    List.of(new Example("/wp edit waypoint {dimension minecraft:overworld} {list \"Home Bases\"}"
                            + " {waypoint \"Main Home\"} set {property color} {value gold}", "wp.help.edit.example")));
            case REMOVE -> new Content(List.of(
                    usage("/wp remove <dimension> <list> <waypoint>", "/wp remove ", "wp.help.remove.waypoint"),
                    usage("/wp remove <dimension> <list>", "/wp remove ", "wp.help.remove.list"),
                    usage("/wp restore <token>", "/wp restore ", "wp.help.remove.restore",
                            note("wp.help.remove.restore.note"))),
                    List.of(new Example(
                            "/wp remove {dimension minecraft:overworld} {list \"Home Bases\"} {waypoint \"Main Home\"}",
                            "wp.help.remove.example")));
            case TP -> new Content(List.of(
                    usage("/wp tp <dimension> <list> <waypoint>", "/wp tp ", "wp.help.tp.waypoint")),
                    List.of(new Example(
                            "/wp tp {dimension minecraft:overworld} {list \"Home Bases\"} {waypoint \"Main Home\"}",
                            "wp.help.tp.example")));
            case NAVIGATE -> {
                List<Usage> usages = new ArrayList<>(List.of(
                        usage("/wp navigate", "/wp navigate", "wp.help.navigate.panel"),
                        usage("/wp navigate <dimension> <list> <waypoint> [default|all|<method>]", "/wp navigate ",
                                "wp.help.navigate.start", note("wp.help.navigate.start.note"),
                                param("method", textDisplay ? "wp.help.navigate.methods.text_display" : "wp.help.navigate.methods")),
                        usage("/wp navigate use <method>", "/wp navigate use ", "wp.help.navigate.use"),
                        usage("/wp navigate disable [<method>]", "/wp navigate disable", "wp.help.navigate.disable")));
                if (textDisplay) {
                    usages.add(usage("/wp navigate config text_display", "/wp navigate config text_display",
                            "wp.help.navigate.text_display"));
                }
                yield new Content(usages, List.of(new Example("/wp navigate {dimension minecraft:overworld} {list Villages}"
                        + " {waypoint \"Oak Village\"} {method bossbar}", "wp.help.navigate.example")));
            }
            case UPLOAD -> new Content(List.of(
                    usage("/wp upload", "/wp upload", "wp.help.upload.panel"),
                    usage("/wp upload <source> [force server|local [delete]] [<dimension> [<list> [<waypoint>]]]",
                            "/wp upload ", "wp.help.upload.run", param("source", "wp.help.upload.source"),
                            note("wp.help.upload.force"))),
                    List.of(new Example("/wp upload {source xaero}", "wp.help.upload.example")));
            case DOWNLOAD -> new Content(List.of(
                    usage("/wp download [<dimension> [<list> [<waypoint>]]]", "/wp download ", "wp.help.download.run")),
                    List.of(new Example("/wp download {dimension minecraft:overworld}", "wp.help.download.example")));
            case REMOTE -> new Content(List.of(
                    usage("/wp remote", "/wp remote", "wp.help.remote.servers"),
                    usage("/wp remote list [<server> [<dimension> [<list>]]]", "/wp remote list ", "wp.help.remote.list",
                            note("wp.help.remote.list.note")),
                    usage("/wp remote tp <server> <dimension> <list> <waypoint>", "/wp remote tp ", "wp.help.remote.tp")),
                    List.of(new Example("/wp remote list {server survival}", "wp.help.remote.example")));
        };
    }

    private static Usage usage(String syntax, String suggestion, String descriptionKey, Component... notes) {
        Tooltip tooltip = Tooltip.of(translatable(descriptionKey));
        for (Component note : notes) {
            tooltip = tooltip.line(note);
        }
        return new Usage(syntax, suggestion, tooltip);
    }

    private static Component note(String key) {
        return translatable(key);
    }

    /** A note about one argument, such as "<mode>: default, name, distance or color"; {0} is <mode> in its colour. */
    private static Component param(String name, String key) {
        return translatable(key, text("<" + name + ">", argumentColor(name)));
    }
}
