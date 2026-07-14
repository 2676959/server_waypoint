package _959.server_waypoint.common.client.gui.render;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable values for every {@link WidgetThemeVariable}.
 */
public final class WidgetTheme {
    private final EnumMap<WidgetThemeVariable, Integer> colors;

    private WidgetTheme(EnumMap<WidgetThemeVariable, Integer> colors) {
        this.colors = new EnumMap<>(colors);
        for (WidgetThemeVariable variable : WidgetThemeVariable.values()) {
            if (!this.colors.containsKey(variable)) {
                throw new IllegalArgumentException("Missing widget theme variable: " + variable);
            }
        }
    }

    public static WidgetTheme modernDark() {
        return WidgetThemes.MODERN_DARK;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(WidgetTheme baseTheme) {
        return new Builder(Objects.requireNonNull(baseTheme, "baseTheme"));
    }

    public int getColor(WidgetThemeVariable variable) {
        return this.colors.get(Objects.requireNonNull(variable, "variable"));
    }

    public WidgetTheme withColor(WidgetThemeVariable variable, int color) {
        return builder(this).setColor(variable, color).build();
    }

    public Map<WidgetThemeVariable, Integer> getColors() {
        return Collections.unmodifiableMap(new EnumMap<>(this.colors));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof WidgetTheme other)) {
            return false;
        }
        return this.colors.equals(other.colors);
    }

    @Override
    public int hashCode() {
        return this.colors.hashCode();
    }

    @Override
    public String toString() {
        return "WidgetTheme" + this.colors;
    }

    public static final class Builder {
        private final EnumMap<WidgetThemeVariable, Integer> colors = new EnumMap<>(WidgetThemeVariable.class);

        private Builder() {
        }

        private Builder(WidgetTheme baseTheme) {
            this.colors.putAll(baseTheme.colors);
        }

        public Builder setColor(WidgetThemeVariable variable, int color) {
            this.colors.put(Objects.requireNonNull(variable, "variable"), color);
            return this;
        }

        public WidgetTheme build() {
            return new WidgetTheme(this.colors);
        }
    }
}
