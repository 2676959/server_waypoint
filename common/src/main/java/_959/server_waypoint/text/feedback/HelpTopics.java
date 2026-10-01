package _959.server_waypoint.text.feedback;

import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static net.kyori.adventure.text.Component.translatable;

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

    public record Example(String command, String descriptionKey) {
    }

    public record Content(List<Usage> usages, List<Example> examples) {
    }

    public static List<Topic> readable(Viewer viewer) {
        return Arrays.stream(Topic.values()).filter(topic -> topic.readableBy(viewer)).toList();
    }

    public static Content content(Topic topic, boolean textDisplay) {
        return switch (topic) {
            case LIST -> new Content(List.of(
                    usage("/wp list [<dimension> [<list>]]", "/wp list ", "wp.help.list.browse", "wp.help.list.browse.note"),
                    usage("/wp list all", "/wp list all", "wp.help.list.all"),
                    usage("/wp list dimensions", "/wp list dimensions", "wp.help.list.dimensions"),
                    usage("[search <text>] [sort <mode> [order <direction>]] [limit <number>] [view <view>] [page <number>]",
                            "/wp list ", "wp.help.list.options", "wp.help.list.options.mode",
                            "wp.help.list.options.direction", "wp.help.list.options.view", "wp.help.list.options.number")),
                    List.of(new Example("/wp list all search farm", "wp.help.list.example.search"),
                            new Example("/wp list minecraft:overworld \"Home Bases\" sort name", "wp.help.list.example.sort")));
            case ADD -> new Content(List.of(
                    usage("/wp add", "/wp add", "wp.help.add.picker"),
                    usage("/wp add <dimension> <list>", "/wp add ", "wp.help.add.list", "wp.help.add.list.note"),
                    usage("/wp add <position> <list> <name> [<initials> <color> <yaw> <global> [<keywords> [<description>]] [icon <id>]]",
                            "/wp add ~ ~ ~ ", "wp.help.add.here", "wp.help.add.here.note", "wp.help.add.color.note",
                            "wp.help.add.global.note"),
                    usage("/wp add <dimension> <list> <position> <name> [<initials> <color> <yaw> <global> [<keywords> [<description>]] [icon <id>]]",
                            "/wp add ", "wp.help.add.elsewhere", "wp.help.add.here.note")),
                    List.of(new Example("/wp add ~ ~ ~ \"Home Bases\" \"Main Home\"", "wp.help.add.example.here"),
                            new Example("/wp add minecraft:overworld \"Home Bases\"", "wp.help.add.example.list")));
            case EDIT -> new Content(List.of(
                    usage("/wp edit waypoint <dimension> <list> <waypoint> set <property> [<value>]", "/wp edit waypoint ",
                            "wp.help.edit.waypoint", "wp.help.edit.properties", "wp.help.edit.pickers"),
                    usage("/wp edit waypoint <dimension> <list> <waypoint> clear <property>", "/wp edit waypoint ",
                            "wp.help.edit.clear"),
                    usage("/wp edit list <dimension> <list> set <property> <value>", "/wp edit list ", "wp.help.edit.list"),
                    usage("/wp edit list <dimension> <list> clear display-name", "/wp edit list ", "wp.help.edit.list.clear")),
                    List.of(new Example("/wp edit waypoint minecraft:overworld \"Home Bases\" \"Main Home\" set color gold",
                            "wp.help.edit.example")));
            case REMOVE -> new Content(List.of(
                    usage("/wp remove <dimension> <list> <waypoint>", "/wp remove ", "wp.help.remove.waypoint"),
                    usage("/wp remove <dimension> <list>", "/wp remove ", "wp.help.remove.list"),
                    usage("/wp restore <token>", "/wp restore ", "wp.help.remove.restore", "wp.help.remove.restore.note")),
                    List.of(new Example("/wp remove minecraft:overworld \"Home Bases\" \"Main Home\"", "wp.help.remove.example")));
            case TP -> new Content(List.of(
                    usage("/wp tp <dimension> <list> <waypoint>", "/wp tp ", "wp.help.tp.waypoint")),
                    List.of(new Example("/wp tp minecraft:overworld \"Home Bases\" \"Main Home\"", "wp.help.tp.example")));
            case NAVIGATE -> {
                List<Usage> usages = new ArrayList<>(List.of(
                        usage("/wp navigate", "/wp navigate", "wp.help.navigate.panel"),
                        usage("/wp navigate <dimension> <list> <waypoint> [default|all|<method>]", "/wp navigate ",
                                "wp.help.navigate.start", "wp.help.navigate.start.note",
                                textDisplay ? "wp.help.navigate.methods.text_display" : "wp.help.navigate.methods"),
                        usage("/wp navigate use <method>", "/wp navigate use ", "wp.help.navigate.use"),
                        usage("/wp navigate disable [<method>]", "/wp navigate disable", "wp.help.navigate.disable")));
                if (textDisplay) {
                    usages.add(usage("/wp navigate config text_display", "/wp navigate config text_display",
                            "wp.help.navigate.text_display"));
                }
                yield new Content(usages, List.of(new Example(
                        "/wp navigate minecraft:overworld Villages \"Oak Village\" bossbar", "wp.help.navigate.example")));
            }
            case UPLOAD -> new Content(List.of(
                    usage("/wp upload", "/wp upload", "wp.help.upload.panel"),
                    usage("/wp upload <source> [force server|local [delete]] [<dimension> [<list> [<waypoint>]]]",
                            "/wp upload ", "wp.help.upload.run", "wp.help.upload.source", "wp.help.upload.force")),
                    List.of(new Example("/wp upload xaero", "wp.help.upload.example")));
            case DOWNLOAD -> new Content(List.of(
                    usage("/wp download [<dimension> [<list> [<waypoint>]]]", "/wp download ", "wp.help.download.run")),
                    List.of(new Example("/wp download minecraft:overworld", "wp.help.download.example")));
            case REMOTE -> new Content(List.of(
                    usage("/wp remote", "/wp remote", "wp.help.remote.servers"),
                    usage("/wp remote list [<server> [<dimension> [<list>]]]", "/wp remote list ", "wp.help.remote.list",
                            "wp.help.remote.list.note"),
                    usage("/wp remote tp <server> <dimension> <list> <waypoint>", "/wp remote tp ", "wp.help.remote.tp")),
                    List.of(new Example("/wp remote list survival", "wp.help.remote.example")));
        };
    }

    private static Usage usage(String syntax, String suggestion, String descriptionKey, String... noteKeys) {
        Tooltip tooltip = Tooltip.of(translatable(descriptionKey));
        for (String note : noteKeys) {
            tooltip = tooltip.line(note);
        }
        return new Usage(syntax, suggestion, tooltip);
    }
}
