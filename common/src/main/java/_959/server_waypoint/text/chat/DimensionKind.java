package _959.server_waypoint.text.chat;

import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;

/** The family of a dimension type, which decides a dimension's colour and its converted coordinates. */
public enum DimensionKind {
    OVERWORLD("minecraft:overworld", NamedTextColor.GREEN),
    NETHER("minecraft:the_nether", NamedTextColor.RED),
    END("minecraft:the_end", NamedTextColor.LIGHT_PURPLE),
    MODDED(null, NamedTextColor.YELLOW);

    private final @Nullable String vanillaId;
    private final NamedTextColor color;

    DimensionKind(@Nullable String vanillaId, NamedTextColor color) {
        this.vanillaId = vanillaId;
        this.color = color;
    }

    public NamedTextColor color() {
        return this.color;
    }

    /** The kind of a dimension type ID, or of a dimension ID when its type is unknown. */
    public static DimensionKind of(String id) {
        for (DimensionKind kind : values()) {
            if (id.equals(kind.vanillaId)) {
                return kind;
            }
        }
        return MODDED;
    }
}
