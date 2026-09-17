package _959.server_waypoint.common.client.gui.render;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Semantic color roles shared by the project's GUI components.
 */
public enum WidgetThemeVariable {
    TEXT_PRIMARY("text.primary"),
    TEXT_MUTED("text.muted"),
    TEXT_DISABLED("text.disabled"),
    TEXT_PLACEHOLDER("text.placeholder"),
    TEXT_ON_ACCENT("text.onAccent"),
    SCREEN_BACKGROUND("background.screen"),
    PANEL_BACKGROUND("background.panel"),
    POPUP_BACKGROUND("background.popup"),
    DIALOG_BACKGROUND("background.dialog"),
    CONTROL_BACKGROUND("control.background"),
    CONTROL_HOVER_BACKGROUND("control.hoverBackground"),
    CONTROL_DISABLED_BACKGROUND("control.disabledBackground"),
    CONTROL_SELECTED_BACKGROUND("control.selectedBackground"),
    BORDER("border.default"),
    FOCUS_RING("border.focusRing"),
    ACCENT("accent.default"),
    ACCENT_HOVER("accent.hover"),
    SELECTION_BACKGROUND("selection.background"),
    ROW_HOVER_BACKGROUND("row.hoverBackground"),
    SCROLLBAR_TRACK("scrollbar.track"),
    SCROLLBAR_THUMB("scrollbar.thumb"),
    SCROLLBAR_THUMB_ACTIVE("scrollbar.thumbActive"),
    SCROLLBAR_THUMB_DISABLED("scrollbar.thumbDisabled"),
    SLIDER_THUMB_DISABLED("slider.thumbDisabled"),
    SUCCESS("status.success"),
    WARNING("status.warning"),
    DANGER("status.danger"),
    SUCCESS_BACKGROUND("status.successBackground"),
    WARNING_BACKGROUND("status.warningBackground"),
    DANGER_BACKGROUND("status.dangerBackground");

    private static final Map<String, WidgetThemeVariable> BY_JSON_NAME = createJsonNameLookup();

    private final String jsonName;

    WidgetThemeVariable(String jsonName) {
        this.jsonName = jsonName;
    }

    public String getJsonName() {
        return this.jsonName;
    }

    public static Optional<WidgetThemeVariable> fromJsonName(String jsonName) {
        return Optional.ofNullable(BY_JSON_NAME.get(Objects.requireNonNull(jsonName, "jsonName")));
    }

    private static Map<String, WidgetThemeVariable> createJsonNameLookup() {
        Map<String, WidgetThemeVariable> variables = new HashMap<>();
        for (WidgetThemeVariable variable : values()) {
            WidgetThemeVariable previous = variables.put(variable.jsonName, variable);
            if (previous != null) {
                throw new IllegalStateException("Duplicate widget theme JSON name: " + variable.jsonName);
            }
        }
        return Map.copyOf(variables);
    }
}
