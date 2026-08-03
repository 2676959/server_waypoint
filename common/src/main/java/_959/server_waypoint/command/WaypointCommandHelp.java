package _959.server_waypoint.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

final class WaypointCommandHelp {
    private static final String MAIN_HELP_COMMAND = "/wp help";
    private static final String ADD_COMMAND_PREFIX = "/wp add ";
    private static final String EDIT_COMMAND_PREFIX = "/wp edit ";
    private static final String LIST_COMMAND_PREFIX = "/wp list ";
    private static final String UPLOAD_COMMAND_PREFIX = "/wp upload ";
    private static final String NAVIGATE_COMMAND_PREFIX = "/wp navigate ";
    private static final TextColor DIMENSION_COLOR = TextColor.color(0x55FF55);
    private static final TextColor LIST_COLOR = TextColor.color(0xFFAA00);
    private static final TextColor POSITION_COLOR = TextColor.color(0x55AAFF);
    private static final TextColor WAYPOINT_COLOR = TextColor.color(0xFFFF55);
    private static final TextColor NEW_NAME_COLOR = TextColor.color(0xFF55FF);
    private static final TextColor INITIALS_COLOR = TextColor.color(0xAAAAFF);
    private static final TextColor COLOR_COLOR = TextColor.color(0xFF5555);
    private static final TextColor YAW_COLOR = TextColor.color(0x00D5A0);
    private static final TextColor GLOBAL_COLOR = TextColor.color(0xC77DFF);
    private static final TextColor KEYWORDS_COLOR = TextColor.color(0xFFB86C);
    private static final TextColor DESCRIPTION_COLOR = TextColor.color(0xBD93F9);
    private static final TextColor QUERY_COLOR = TextColor.color(0xFF79C6);
    private static final TextColor MODE_COLOR = TextColor.color(0xF1FA8C);
    private static final TextColor DIRECTION_COLOR = TextColor.color(0x8BE9FD);
    private static final TextColor NUMBER_COLOR = TextColor.color(0x50FA7B);

    private WaypointCommandHelp() {
    }

    static Component mainMenu(
            boolean withAdd,
            boolean withEdit,
            boolean withRemove,
            boolean withNavigate,
            boolean withTp,
            boolean withReload,
            boolean withUpload
    ) {
        Component help = translatable("waypoint.help.title", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .append(topicEntry(
                        "/wp list",
                        LIST_COMMAND_PREFIX,
                        "/wp help list",
                        "waypoint.help.list"
                ))
                .append(commandEntry(
                        "/wp download [<dimension> [<list-identifier> [<waypoint-identifier>]]]",
                        "/wp download ",
                        "waypoint.help.download"
                ));
        if (withUpload) {
            help = help.append(commandEntry(
                    "/wp upload [<dimension> [<list> [<waypoint>]]]",
                    UPLOAD_COMMAND_PREFIX,
                    "waypoint.help.upload"
            ));
        }
        if (withNavigate) {
            help = help.append(topicEntry(
                    "/wp navigate",
                    NAVIGATE_COMMAND_PREFIX,
                    "/wp help navigate",
                    "waypoint.help.navigate"
            ));
        }
        if (withAdd) {
            help = help.append(topicEntry(
                    "/wp add",
                    ADD_COMMAND_PREFIX,
                    "/wp help add",
                    "waypoint.help.add"
            ));
        }
        if (withEdit) {
            help = help.append(topicEntry(
                    "/wp edit",
                    EDIT_COMMAND_PREFIX,
                    "/wp help edit",
                    "waypoint.help.edit"
            ));
        }
        if (withRemove) {
            help = help.append(commandEntry(
                    "/wp remove <dimension> <list-identifier> [<waypoint-identifier>]",
                    "/wp remove ",
                    "waypoint.help.remove"
            ));
        }
        if (withTp) {
            help = help.append(commandEntry(
                    "/wp tp <dimension> <list-identifier> <waypoint-identifier>",
                    "/wp tp ",
                    "waypoint.help.tp"
            ));
        }
        if (withReload) {
            help = help.append(commandEntry(
                    "/wp reload",
                    "/wp reload",
                    "waypoint.help.reload"
            ));
        }
        return help;
    }

    static Component addHelp() {
        return topicHeader("waypoint.help.add.title", "waypoint.help.add.summary")
                .append(section("waypoint.help.section.usage"))
                .append(usageEntry(
                        "/wp add <dimension> <list-identifier>",
                        ADD_COMMAND_PREFIX,
                        "waypoint.help.add.usage.list"
                ))
                .append(usageEntry(
                        "/wp add <position> <list-identifier> <waypoint-identifier>",
                        ADD_COMMAND_PREFIX,
                        "waypoint.help.add.usage.quick"
                ))
                .append(usageEntry(
                        "/wp add <position> <list-identifier> <waypoint-identifier> <initials> <color> <yaw> <global> [<keywords> [<description>]]",
                        ADD_COMMAND_PREFIX,
                        "waypoint.help.add.usage.current"
                ))
                .append(usageEntry(
                        "/wp add <dimension> <list-identifier> <position> <waypoint-identifier> <initials> <color> <yaw> <global> [<keywords> [<description>]]",
                        ADD_COMMAND_PREFIX,
                        "waypoint.help.add.usage.dimension"
                ))
                .append(section("waypoint.help.section.arguments"))
                .append(argumentEntry("<dimension>", "waypoint.help.argument.dimension"))
                .append(argumentEntry("<position>", "waypoint.help.argument.position"))
                .append(argumentEntry("<list-identifier> / <waypoint-identifier>", "waypoint.help.argument.names"))
                .append(argumentEntry("<initials>", "waypoint.help.argument.initials"))
                .append(argumentEntry("<color>", "waypoint.help.argument.color"))
                .append(argumentEntry("<yaw>", "waypoint.help.argument.yaw"))
                .append(argumentEntry("<global>", "waypoint.help.argument.global"))
                .append(argumentEntry("<keywords>", "waypoint.help.argument.keywords"))
                .append(argumentEntry("<description>", "waypoint.help.argument.description"))
                .append(section("waypoint.help.section.examples"))
                .append(exampleEntry(
                        "/wp add minecraft:overworld \"Home Bases\"",
                        "waypoint.help.add.example.list",
                        exampleArgument("minecraft:overworld", DIMENSION_COLOR),
                        exampleArgument("\"Home Bases\"", LIST_COLOR)
                ))
                .append(exampleEntry(
                        "/wp add ~ ~ ~ \"Home Bases\" \"Main Home\"",
                        "waypoint.help.add.example.quick",
                        exampleArgument("~ ~ ~", POSITION_COLOR),
                        exampleArgument("\"Home Bases\"", LIST_COLOR),
                        exampleArgument("\"Main Home\"", WAYPOINT_COLOR)
                ))
                .append(exampleEntry(
                        "/wp add minecraft:overworld \"Home Bases\" ~ ~ ~ \"Main Home\" MH gold 0 true",
                        "waypoint.help.add.example.full",
                        exampleArgument("minecraft:overworld", DIMENSION_COLOR),
                        exampleArgument("\"Home Bases\"", LIST_COLOR),
                        exampleArgument("~ ~ ~", POSITION_COLOR),
                        exampleArgument("\"Main Home\"", WAYPOINT_COLOR),
                        exampleArgument("MH", INITIALS_COLOR),
                        exampleArgument("gold", COLOR_COLOR),
                        exampleArgument("0", YAW_COLOR),
                        exampleArgument("true", GLOBAL_COLOR)
                ))
                .append(backButton());
    }

    static Component editHelp() {
        return topicHeader("waypoint.help.edit.title", "waypoint.help.edit.summary")
                .append(section("waypoint.help.section.usage"))
                .append(usageEntry(
                        "/wp edit list <dimension> <list-identifier> set identifier <identifier>",
                        EDIT_COMMAND_PREFIX,
                        "waypoint.help.edit.usage"
                ))
                .append(usageEntry(
                        "/wp edit list <dimension> <list-identifier> set|clear display-name [<display-name>]",
                        EDIT_COMMAND_PREFIX,
                        "waypoint.help.edit.usage"
                ))
                .append(usageEntry(
                        "/wp edit waypoint <dimension> <list-identifier> <waypoint-identifier> set <property> <value>",
                        EDIT_COMMAND_PREFIX,
                        "waypoint.help.edit.usage"
                ))
                .append(usageEntry(
                        "/wp edit waypoint <dimension> <list-identifier> <waypoint-identifier> clear <display-name|keywords|description>",
                        EDIT_COMMAND_PREFIX,
                        "waypoint.help.edit.usage"
                ))
                .append(section("waypoint.help.section.arguments"))
                .append(argumentEntry("<dimension>", "waypoint.help.argument.dimension"))
                .append(argumentEntry(
                        "<list-identifier> / <waypoint-identifier>",
                        "waypoint.help.argument.names"
                ))
                .append(section("waypoint.help.section.examples"))
                .append(exampleEntry(
                        "/wp edit waypoint minecraft:overworld \"Home Bases\" \"Main Home\" set identifier \"Mountain Home\"",
                        "waypoint.help.edit.example.full",
                        exampleArgument("minecraft:overworld", DIMENSION_COLOR),
                        exampleArgument("\"Home Bases\"", LIST_COLOR),
                        exampleArgument("\"Main Home\"", WAYPOINT_COLOR),
                        exampleArgument("\"Mountain Home\"", NEW_NAME_COLOR)
                ))
                .append(backButton());
    }

    static Component listHelp() {
        return topicHeader("waypoint.help.list.title", "waypoint.help.list.summary")
                .append(section("waypoint.help.section.usage"))
                .append(usageEntry(
                        "/wp list",
                        LIST_COMMAND_PREFIX,
                        "waypoint.help.list.usage.current"
                ))
                .append(usageEntry(
                        "/wp list all",
                        LIST_COMMAND_PREFIX,
                        "waypoint.help.list.usage.all"
                ))
                .append(usageEntry(
                        "/wp list <dimension>",
                        LIST_COMMAND_PREFIX,
                        "waypoint.help.list.usage.dimension"
                ))
                .append(usageEntry(
                        "/wp list <dimension> <list>",
                        LIST_COMMAND_PREFIX,
                        "waypoint.help.list.usage.list"
                ))
                .append(usageEntry(
                        "[search <query>] [sort <mode> [order <direction>]] [page <number>] [limit <number>] [view <view>]",
                        LIST_COMMAND_PREFIX,
                        "waypoint.help.list.usage.options"
                ))
                .append(section("waypoint.help.section.arguments"))
                .append(argumentEntry("<query>", "waypoint.help.list.argument.query"))
                .append(argumentEntry("<mode>", "waypoint.help.list.argument.mode"))
                .append(argumentEntry("<direction>", "waypoint.help.list.argument.direction"))
                .append(argumentEntry("<number>", "waypoint.help.list.argument.number"))
                .append(argumentEntry("<view>", "waypoint.help.list.argument.view"))
                .append(argumentEntry(
                        "search → sort → order → page → limit → view, or sort → order → page → limit → view → search",
                        "waypoint.help.list.argument.order"
                ))
                .append(section("waypoint.help.section.examples"))
                .append(exampleEntry(
                        "/wp list",
                        "waypoint.help.list.example.current"
                ))
                .append(exampleEntry(
                        "/wp list all search home sort distance order ascending page 1 limit 10 view flat",
                        "waypoint.help.list.example.all",
                        exampleArgument("home", QUERY_COLOR),
                        exampleArgument("distance", MODE_COLOR),
                        exampleArgument("ascending", DIRECTION_COLOR),
                        exampleArgument("1", NUMBER_COLOR),
                        exampleArgument("10", NUMBER_COLOR),
                        exampleArgument("flat", MODE_COLOR)
                ))
                .append(exampleEntry(
                        "/wp list minecraft:overworld \"Home Bases\" sort name order descending limit 20",
                        "waypoint.help.list.example.list",
                        exampleArgument("minecraft:overworld", DIMENSION_COLOR),
                        exampleArgument("\"Home Bases\"", LIST_COLOR),
                        exampleArgument("name", MODE_COLOR),
                        exampleArgument("descending", DIRECTION_COLOR),
                        exampleArgument("20", NUMBER_COLOR)
                ))
                .append(backButton());
    }

    static Component navigateHelp(boolean withTextDisplay) {
        Component help = topicHeader("waypoint.help.navigate.title", "waypoint.help.navigate.summary")
                .append(section("waypoint.help.section.usage"))
                .append(usageEntry(
                        "/wp navigate <dimension> <list> <waypoint>",
                        NAVIGATE_COMMAND_PREFIX,
                        "waypoint.help.navigate.usage.start"
                ))
                .append(usageEntry(
                        "/wp navigate <dimension> <list> <waypoint> [default|all|<method>]",
                        NAVIGATE_COMMAND_PREFIX,
                        "waypoint.help.navigate.usage.methods"
                ))
                .append(usageEntry(
                        "/wp navigate use <method>",
                        NAVIGATE_COMMAND_PREFIX + "use ",
                        "waypoint.help.navigate.usage.use"
                ))
                .append(usageEntry(
                        "/wp navigate disable [<method>]",
                        NAVIGATE_COMMAND_PREFIX + "disable ",
                        "waypoint.help.navigate.usage.disable"
                ))
                .append(usageEntry(
                        "/wp navigate status",
                        NAVIGATE_COMMAND_PREFIX + "status",
                        "waypoint.help.navigate.usage.status"
                ));
        if (withTextDisplay) {
            help = help.append(usageEntry(
                            "/wp navigate config text_display transformation translation <x> <y> <z>",
                            NAVIGATE_COMMAND_PREFIX + "config text_display transformation translation ",
                            "waypoint.help.navigate.usage.transformation.translation"
                    ))
                    .append(usageEntry(
                            "/wp navigate config text_display transformation rotation <x> <y> <z>",
                            NAVIGATE_COMMAND_PREFIX + "config text_display transformation rotation ",
                            "waypoint.help.navigate.usage.transformation.rotation"
                    ))
                    .append(usageEntry(
                            "/wp navigate config text_display transformation scale <x> <y> <z>",
                            NAVIGATE_COMMAND_PREFIX + "config text_display transformation scale ",
                            "waypoint.help.navigate.usage.transformation.scale"
                    ))
                    .append(usageEntry(
                            "/wp navigate config text_display transformation reset",
                            NAVIGATE_COMMAND_PREFIX + "config text_display transformation reset",
                            "waypoint.help.navigate.usage.transformation.reset"
                    ));
        }
        help = help
                .append(section("waypoint.help.section.arguments"))
                .append(argumentEntry(
                        "[default|all|<method>]",
                        "waypoint.help.navigate.argument.target_methods"
                ))
                .append(argumentEntry("<method>", "waypoint.help.navigate.argument.method"));
        if (withTextDisplay) {
            help = help.append(argumentEntry(
                    "<x> <y> <z>",
                    "waypoint.help.navigate.argument.transformation.vector"
            ));
        }
        help = help
                .append(section("waypoint.help.navigate.section.methods"))
                .append(argumentEntry("compass", "waypoint.help.navigate.method.compass"))
                .append(argumentEntry("map", "waypoint.help.navigate.method.map"))
                .append(argumentEntry("bossbar", "waypoint.help.navigate.method.bossbar"))
                .append(argumentEntry("actionbar", "waypoint.help.navigate.method.actionbar"));
        if (withTextDisplay) {
            help = help.append(argumentEntry(
                    "text_display",
                    "waypoint.help.navigate.method.text_display"
            ));
        }
        help = help
                .append(section("waypoint.help.navigate.section.inventory"))
                .append(argumentEntry("compass / map", "waypoint.help.navigate.inventory"))
                .append(section("waypoint.help.section.examples"))
                .append(exampleEntry(
                        "/wp navigate minecraft:overworld \"Villages\" \"Oak Village\"",
                        "waypoint.help.navigate.example.default",
                        exampleArgument("minecraft:overworld", DIMENSION_COLOR),
                        exampleArgument("\"Villages\"", LIST_COLOR),
                        exampleArgument("\"Oak Village\"", WAYPOINT_COLOR)
                ))
                .append(exampleEntry(
                        "/wp navigate minecraft:overworld \"Villages\" \"Oak Village\" all",
                        "waypoint.help.navigate.example.all",
                        exampleArgument("minecraft:overworld", DIMENSION_COLOR),
                        exampleArgument("\"Villages\"", LIST_COLOR),
                        exampleArgument("\"Oak Village\"", WAYPOINT_COLOR),
                        exampleArgument("all", MODE_COLOR)
                ))
                .append(exampleEntry(
                        "/wp navigate minecraft:overworld \"Villages\" \"Oak Village\" bossbar",
                        "waypoint.help.navigate.example.method",
                        exampleArgument("minecraft:overworld", DIMENSION_COLOR),
                        exampleArgument("\"Villages\"", LIST_COLOR),
                        exampleArgument("\"Oak Village\"", WAYPOINT_COLOR),
                        exampleArgument("bossbar", MODE_COLOR)
                ))
                .append(exampleEntry(
                        "/wp navigate use bossbar",
                        "waypoint.help.navigate.example.use",
                        exampleArgument("bossbar", MODE_COLOR)
                ));
        if (withTextDisplay) {
            help = help.append(exampleEntry(
                            "/wp navigate config text_display transformation translation 0 0.1 0",
                            "waypoint.help.navigate.example.transformation.translation",
                            exampleArgument("0 0.1 0", NUMBER_COLOR)
                    ))
                    .append(exampleEntry(
                            "/wp navigate config text_display transformation rotation 5 0 0",
                            "waypoint.help.navigate.example.transformation.rotation",
                            exampleArgument("5 0 0", NUMBER_COLOR)
                    ))
                    .append(exampleEntry(
                            "/wp navigate config text_display transformation scale 1.35 1.35 1.35",
                            "waypoint.help.navigate.example.transformation.scale",
                            exampleArgument("1.35 1.35 1.35", NUMBER_COLOR)
                    ));
        }
        return help.append(backButton());
    }

    private static Component topicHeader(String titleKey, String summaryKey) {
        return translatable(titleKey, NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .append(newline())
                .append(translatable(summaryKey, NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false));
    }

    private static Component section(String translationKey) {
        return newline()
                .append(newline())
                .append(translatable(translationKey, NamedTextColor.YELLOW)
                        .decorate(TextDecoration.BOLD));
    }

    private static Component topicEntry(
            String usage,
            String suggestion,
            String helpCommand,
            String descriptionKey
    ) {
        Component label = text("")
                .append(suggestCommand(usage, suggestion))
                .appendSpace()
                .append(detailButton(helpCommand));
        return describedLine(label, descriptionKey);
    }

    private static Component commandEntry(String usage, String suggestion, String descriptionKey) {
        return describedLine(suggestCommand(usage, suggestion), descriptionKey);
    }

    private static Component usageEntry(String usage, String suggestion, String descriptionKey) {
        return describedLine(suggestCommand(usage, suggestion), descriptionKey);
    }

    private static Component argumentEntry(String argument, String descriptionKey) {
        return describedLine(colorArguments(argument), descriptionKey);
    }

    private static Component exampleEntry(
            String command,
            String descriptionKey,
            ExampleArgument... arguments
    ) {
        return describedLine(
                suggestCommand(colorExampleArguments(command, arguments), command),
                descriptionKey
        );
    }

    private static Component describedLine(Component label, String descriptionKey) {
        return newline()
                .append(text("  "))
                .append(label.decoration(TextDecoration.BOLD, false))
                .append(text(" - ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.BOLD, false))
                .append(translatable(descriptionKey, NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false));
    }

    private static Component suggestCommand(String label, String command) {
        return suggestCommand(colorArguments(label), command);
    }

    private static Component suggestCommand(Component label, String command) {
        return label
                .decoration(TextDecoration.BOLD, false)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(translatable("waypoint.help.click_to_suggest")));
    }

    private static ExampleArgument exampleArgument(String value, TextColor color) {
        return new ExampleArgument(value, color);
    }

    private static Component colorExampleArguments(
            String command,
            ExampleArgument... arguments
    ) {
        Component result = text("");
        int offset = 0;
        for (ExampleArgument argument : arguments) {
            int argumentStart = command.indexOf(argument.value(), offset);
            if (argumentStart < 0) {
                throw new IllegalArgumentException(
                        "Example argument not found in command: " + argument.value()
                );
            }
            if (argumentStart > offset) {
                result = result.append(text(
                        command.substring(offset, argumentStart),
                        NamedTextColor.AQUA
                ));
            }
            result = result.append(text(argument.value(), argument.color()));
            offset = argumentStart + argument.value().length();
        }
        if (offset < command.length()) {
            result = result.append(text(command.substring(offset), NamedTextColor.AQUA));
        }
        return result;
    }

    private static Component colorArguments(String command) {
        Component result = text("");
        int offset = 0;
        while (offset < command.length()) {
            int argumentStart = command.indexOf('<', offset);
            if (argumentStart < 0) {
                return result.append(text(command.substring(offset), NamedTextColor.AQUA));
            }
            if (argumentStart > offset) {
                result = result.append(text(
                        command.substring(offset, argumentStart),
                        NamedTextColor.AQUA
                ));
            }
            int argumentEnd = command.indexOf('>', argumentStart + 1);
            if (argumentEnd < 0) {
                return result.append(text(command.substring(argumentStart), NamedTextColor.AQUA));
            }
            String argumentName = command.substring(argumentStart + 1, argumentEnd);
            result = result.append(text(
                    command.substring(argumentStart, argumentEnd + 1),
                    argumentColor(argumentName)
            ));
            offset = argumentEnd + 1;
        }
        return result;
    }

    private static TextColor argumentColor(String argumentName) {
        return switch (argumentName) {
            case "dimension" -> DIMENSION_COLOR;
            case "list", "list-identifier" -> LIST_COLOR;
            case "position" -> POSITION_COLOR;
            case "waypoint", "waypoint-identifier" -> WAYPOINT_COLOR;
            case "new name", "identifier" -> NEW_NAME_COLOR;
            case "initials" -> INITIALS_COLOR;
            case "color" -> COLOR_COLOR;
            case "yaw" -> YAW_COLOR;
            case "global" -> GLOBAL_COLOR;
            case "keywords" -> KEYWORDS_COLOR;
            case "description" -> DESCRIPTION_COLOR;
            case "query" -> QUERY_COLOR;
            case "mode", "selection", "method" -> MODE_COLOR;
            case "direction" -> DIRECTION_COLOR;
            case "number" -> NUMBER_COLOR;
            default -> NamedTextColor.WHITE;
        };
    }

    private static Component detailButton(String helpCommand) {
        return text("[?]", NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(helpCommand))
                .hoverEvent(HoverEvent.showText(translatable("waypoint.help.click_for_details")));
    }

    private static Component backButton() {
        return newline()
                .append(newline())
                .append(text("[←]", NamedTextColor.YELLOW)
                        .decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand(MAIN_HELP_COMMAND))
                        .hoverEvent(HoverEvent.showText(translatable("waypoint.help.back.hover"))))
                .appendSpace()
                .append(translatable("waypoint.help.back", NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false));
    }

    private record ExampleArgument(String value, TextColor color) {
    }
}
