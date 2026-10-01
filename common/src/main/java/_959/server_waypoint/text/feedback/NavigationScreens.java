package _959.server_waypoint.text.feedback;

import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.TextDisplayTransformation;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static _959.server_waypoint.util.StringCommandBuilder.navigateCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/** The navigation panel and the text display panel (spec 10). */
public final class NavigationScreens {
    public static final float MOVE_STEP = 0.05F;
    public static final float TURN_STEP = 5F;
    public static final float SIZE_STEP = 0.05F;
    private static final String TRANSFORMATION = "/wp navigate config text_display transformation ";
    private static final String[] AXES = {"X", "Y", "Z"};

    private NavigationScreens() {
    }

    /** Navigating to [MH] Main Home · 25 m, the methods as toggles, then Stop · Change target. */
    public static Component panel(DimensionStyle dims, PlacedWaypoint target, Set<NavigationMethod> enabled,
                                  Set<NavigationMethod> supported, @Nullable Component updated) {
        Viewer viewer = dims.viewer();
        ChatLines lines = new ChatLines();
        if (updated != null) {
            lines.add(Chat.ok(updated));
        }
        Component where = WaypointRefs.rowDetail(viewer, target.dimension(), target.waypoint().pos());
        lines.add(Chat.join(translatable("wp.navigation.title", GOLD, target.reference(dims)),
                where == null ? dims.name(target.dimension()) : where));
        List<Component> methods = new ArrayList<>();
        for (NavigationMethod method : NavigationMethod.values()) {
            if (!supported.contains(method)) {
                continue;
            }
            Component name = methodName(method);
            methods.add(enabled.contains(method)
                    ? Chat.link(viewer, Chat.concat(text(Chat.CHECK + " "), name), GREEN,
                    Click.run("/wp navigate disable " + method.id()),
                    Tooltip.of("wp.navigation.method.on", name).hint("wp.hint.turn_off"))
                    : Chat.link(viewer, name, GRAY, Click.run("/wp navigate use " + method.id()),
                    Tooltip.of("wp.navigation.method.off", name).hint("wp.hint.turn_on")));
        }
        lines.add(Chat.join(methods));
        List<Component> actions = new ArrayList<>();
        actions.add(Chat.control(viewer, translatable("wp.navigation.stop"), RED, Click.run("/wp navigate disable"),
                Tooltip.of("wp.navigation.stop.tooltip")));
        actions.add(Chat.control(viewer, translatable("wp.navigation.change_target"), AQUA, Click.run("/wp list"),
                Tooltip.of("wp.navigation.change_target.tooltip")));
        if (enabled.contains(NavigationMethod.TEXT_DISPLAY)) {
            actions.add(Chat.control(viewer, translatable("wp.navigation.adjust"), YELLOW,
                    Click.run("/wp navigate config text_display"), Tooltip.of("wp.navigation.adjust.tooltip")));
        }
        if (!Chat.isEmpty(actions)) {
            lines.add(Chat.join(actions));
        }
        return lines.build();
    }

    /** Not navigating, then where to start. */
    public static Component idle(DimensionStyle dims) {
        Viewer viewer = dims.viewer();
        ChatLines lines = new ChatLines().add(translatable("wp.navigation.none", GOLD));
        if (viewer.plainText()) {
            return lines.build();
        }
        String here = Objects.requireNonNullElse(viewer.dimension(), "minecraft:overworld");
        return lines.line(translatable("wp.navigation.choose", GRAY), text("  "), Chat.join(
                Chat.link(viewer, translatable("wp.menu.this_dimension"), AQUA, Click.run("/wp list"),
                        Tooltip.of(translatable("wp.menu.this_dimension.tooltip", dims.name(here)))),
                Chat.link(viewer, translatable("wp.all"), AQUA, Click.run("/wp list all"), Tooltip.of("wp.all.tooltip"))))
                .build();
    }

    /** ✔ Stopped navigating   Resume */
    public static Component stopped(DimensionStyle dims, @Nullable PlacedWaypoint previous) {
        Viewer viewer = dims.viewer();
        Component resume = previous == null ? null : Chat.control(viewer, translatable("wp.navigation.resume"), LIGHT_PURPLE,
                Click.run(navigateCmd(previous.dimension(), previous.list().name(), previous.waypoint().name())),
                Tooltip.of("wp.navigation.resume.tooltip",
                        WaypointRefs.label(previous.waypoint().displayName(), previous.waypoint().name())));
        return Chat.ok(translatable("wp.navigation.stopped"), java.util.Arrays.asList(resume));
    }

    /** ✘ You aren't navigating. Browse waypoints */
    public static Component notNavigating(Viewer viewer) {
        return Chat.error(translatable("wp.error.not_navigating"), Chat.control(viewer,
                translatable("wp.navigation.browse"), AQUA, Click.run("/wp list"),
                Tooltip.of("wp.navigation.change_target.tooltip")));
    }

    /** The error line for a navigation result that failed. */
    public static Component failure(NavigationResult result) {
        return switch (result.code()) {
            case INSUFFICIENT_INVENTORY -> Errors.of("wp.error.navigation.inventory",
                    text(result.requiredSlots()), text(result.availableSlots()));
            case TARGET_UNAVAILABLE -> Errors.of("wp.error.navigation.target");
            case INVALID_SELECTION -> Errors.of("wp.error.navigation.no_methods");
            default -> result.method() == null
                    ? Errors.of("wp.error.navigation.failed")
                    : Errors.of("wp.error.navigation.method", methodName(result.method()));
        };
    }

    public static Component methodName(NavigationMethod method) {
        return translatable("wp.navigation.method." + method.id());
    }

    /** /wp navigate config text_display: Move, Turn and Size with nudges and their exact values. */
    public static Component textDisplay(Viewer viewer, TextDisplayTransformation transformation,
                                        @Nullable Component updated) {
        ChatLines lines = new ChatLines();
        if (updated != null) {
            lines.add(Chat.ok(updated));
        }
        lines.line(translatable("wp.text_display.title", GOLD), text("  "), translatable("wp.text_display.subtitle", GRAY));
        lines.add(vectorRow(viewer, "wp.text_display.move", "translation", transformation.translation(), MOVE_STEP,
                -TextDisplayTransformation.MAX_TRANSLATION, TextDisplayTransformation.MAX_TRANSLATION, ""));
        lines.add(vectorRow(viewer, "wp.text_display.turn", "rotation", transformation.rotation(), TURN_STEP,
                -TextDisplayTransformation.MAX_ROTATION_DEGREES, TextDisplayTransformation.MAX_ROTATION_DEGREES, "°"));
        lines.add(sizeRow(viewer, transformation.scale()));
        List<Component> buttons = java.util.Arrays.asList(
                Chat.button(viewer, translatable("wp.text_display.reset.button"), AQUA, Click.run(TRANSFORMATION + "reset"),
                        Tooltip.of("wp.text_display.reset.tooltip")),
                Chat.button(viewer, translatable("wp.action.back"), GRAY, Click.run("/wp navigate"),
                        Tooltip.of("wp.text_display.back.tooltip")));
        if (!Chat.isEmpty(buttons)) {
            lines.add(Chat.spaced(buttons));
        }
        return lines.build();
    }

    /** Move  X [−][+]  Y [−][+]  Z [−][+]  0, 0, 0 [✎] */
    private static Component vectorRow(Viewer viewer, String labelKey, String component, Vector3f value, float step,
                                       float minimum, float maximum, String unit) {
        float[] values = {value.x(), value.y(), value.z()};
        List<Component> pieces = new ArrayList<>();
        pieces.add(translatable(labelKey, GRAY));
        if (!viewer.plainText()) {
            for (int axis = 0; axis < 3; axis++) {
                pieces.add(text("  " + AXES[axis] + " ", GRAY));
                pieces.add(nudge(viewer, "−", component, values, axis, -step, minimum, maximum, unit));
                pieces.add(nudge(viewer, "+", component, values, axis, step, minimum, maximum, unit));
            }
        }
        String shown = format(values[0]) + unit + ", " + format(values[1]) + unit + ", " + format(values[2]) + unit;
        pieces.add(text("  "));
        pieces.add(text(shown, WHITE));
        pieces.add(exact(viewer, component, values));
        return Chat.concat(pieces);
    }

    /** Size  [−][+]  1× [✎]; a size that differs per axis shows each axis. */
    private static Component sizeRow(Viewer viewer, Vector3f value) {
        float[] values = {value.x(), value.y(), value.z()};
        boolean even = values[0] == values[1] && values[1] == values[2];
        List<Component> pieces = new ArrayList<>();
        pieces.add(translatable("wp.text_display.size", GRAY));
        if (!viewer.plainText()) {
            pieces.add(text("  "));
            for (String sign : List.of("−", "+")) {
                float size = clamp(values[0] + (sign.equals("+") ? SIZE_STEP : -SIZE_STEP), SIZE_STEP,
                        TextDisplayTransformation.MAX_SCALE_MULTIPLIER);
                pieces.add(Chat.button(viewer, text(sign), AQUA,
                        Click.run(TRANSFORMATION + "scale " + format(size) + " " + format(size) + " " + format(size)),
                        Tooltip.of(text(sign + " " + format(SIZE_STEP) + "×"))));
            }
        }
        String shown = even ? format(values[0]) + "×"
                : format(values[0]) + "×, " + format(values[1]) + "×, " + format(values[2]) + "×";
        pieces.add(text("  "));
        pieces.add(text(shown, WHITE));
        pieces.add(exact(viewer, "scale", values));
        return Chat.concat(pieces);
    }

    private static @Nullable Component nudge(Viewer viewer, String sign, String component, float[] values, int axis,
                                             float step, float minimum, float maximum, String unit) {
        float[] nudged = values.clone();
        nudged[axis] = clamp(values[axis] + step, minimum, maximum);
        return Chat.button(viewer, text(sign), AQUA, Click.run(TRANSFORMATION + component + " " + vector(nudged)),
                Tooltip.of(text(AXES[axis] + " " + sign + " " + format(Math.abs(step)) + unit)));
    }

    /** The yellow [✎] suggesting the exact values, after a space. */
    private static @Nullable Component exact(Viewer viewer, String component, float[] values) {
        Component button = Chat.button(viewer, text("✎"), YELLOW, Click.suggest(TRANSFORMATION + component + " " + vector(values)),
                Tooltip.of("wp.text_display.exact").hint("wp.hint.edit"));
        return button == null ? null : Chat.concat(text(" "), button);
    }

    private static String vector(float[] values) {
        return format(values[0]) + " " + format(values[1]) + " " + format(values[2]);
    }

    /** At most two decimals, without trailing zeros: 0, 0.05, -2.5, 355. */
    static String format(float value) {
        return BigDecimal.valueOf(Math.round(value * 100) / 100.0).stripTrailingZeros().toPlainString();
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
