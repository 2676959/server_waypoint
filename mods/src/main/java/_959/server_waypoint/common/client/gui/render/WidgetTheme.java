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
    private final EnumMap<WidgetThemeVariable, Boolean> booleans;

    private WidgetTheme(EnumMap<WidgetThemeVariable, Integer> colors,
                        EnumMap<WidgetThemeVariable, Boolean> booleans) {
        this.colors = new EnumMap<>(colors);
        this.booleans = new EnumMap<>(booleans);
        for (WidgetThemeVariable variable : WidgetThemeVariable.values()) {
            if (!(variable.isBoolean() ? this.booleans : this.colors).containsKey(variable)) {
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
        requireType(variable, false);
        return this.colors.get(variable);
    }

    public boolean getBoolean(WidgetThemeVariable variable) {
        requireType(variable, true);
        return this.booleans.get(variable);
    }

    public WidgetTheme withBoolean(WidgetThemeVariable variable, boolean value) {
        return builder(this).setBoolean(variable, value).build();
    }

    public Map<WidgetThemeVariable, Boolean> getBooleans() {
        return Collections.unmodifiableMap(new EnumMap<>(this.booleans));
    }

    private static void requireType(WidgetThemeVariable variable, boolean booleanVariable) {
        if (Objects.requireNonNull(variable, "variable").isBoolean() != booleanVariable) {
            throw new IllegalArgumentException("Wrong widget theme value type: " + variable);
        }
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
        return this.colors.equals(other.colors) && this.booleans.equals(other.booleans);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.colors, this.booleans);
    }

    @Override
    public String toString() {
        return "WidgetTheme" + this.colors + this.booleans;
    }

    public static final class Builder {
        private final EnumMap<WidgetThemeVariable, Integer> colors = new EnumMap<>(WidgetThemeVariable.class);
        private final EnumMap<WidgetThemeVariable, Boolean> booleans = new EnumMap<>(WidgetThemeVariable.class);

        private Builder() {
        }

        private Builder(WidgetTheme baseTheme) {
            this.colors.putAll(baseTheme.colors);
            this.booleans.putAll(baseTheme.booleans);
        }

        public Builder setColor(WidgetThemeVariable variable, int color) {
            requireType(variable, false);
            this.colors.put(variable, color);
            return this;
        }

        public Builder setBoolean(WidgetThemeVariable variable, boolean value) {
            requireType(variable, true);
            this.booleans.put(variable, value);
            return this;
        }

        public WidgetTheme build() {
            return new WidgetTheme(this.colors, this.booleans);
        }
    }
}
