package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.local.LocalTeleportCommand;
import _959.server_waypoint.common.client.local.LocalTeleportCommand.Placeholder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER;

/**
 * Colors for the teleport command: one fixed color per placeholder, and the theme's danger color
 * struck through for mistakes. Bold is never used, because it changes glyph advances.
 */
final class TeleportCommandStyle {
    private TeleportCommandStyle() { }

    static int color(Placeholder placeholder) {
        return switch (placeholder) {
            case X -> 0xFF7B72;
            case Y -> 0x7EE787;
            case Z -> 0x79B8FF;
            case YAW -> 0xFFC66D;
            case DIMENSION -> 0xD2A8FF;
        };
    }

    /**
     * Styles {@code shown}, the part of {@code value} vanilla draws starting at {@code offset}. The
     * whole value is scanned, so a placeholder cut by the caret or the scroll keeps its color.
     */
    static FormattedCharSequence format(String value, String shown, int offset) {
        int end = offset + shown.length();
        List<FormattedCharSequence> pieces = new ArrayList<>();
        for (LocalTeleportCommand.Token token : LocalTeleportCommand.tokens(value)) {
            int start = Math.max(token.start(), offset);
            int stop = Math.min(token.end(), end);
            if (start < stop) {
                pieces.add(FormattedCharSequence.forward(shown.substring(start - offset, stop - offset), style(token)));
            }
        }
        return FormattedCharSequence.composite(pieces);
    }

    /** "Placeholders: {dimension} {x} {y} {z} {yaw}", each placeholder in its color. */
    static Component legend() {
        MutableComponent list = Component.empty();
        for (Placeholder placeholder : Placeholder.values()) {
            if (placeholder.ordinal() > 0) list.append(Component.literal(" "));
            list.append(Component.literal(placeholder.token()).withStyle(style -> style.withColor(color(placeholder))));
        }
        return Component.translatable("waypoint.profile.placeholders", list);
    }

    private static Style style(LocalTeleportCommand.Token token) {
        if (token.mistake()) return Style.EMPTY.withColor(getColor(DANGER) & 0xFFFFFF).withStrikethrough(true);
        return token.placeholder() == null ? Style.EMPTY : Style.EMPTY.withColor(color(token.placeholder()));
    }
}
