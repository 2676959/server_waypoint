package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.util.BlockPosConverter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

/**
 * How dimensions look to one viewer: names, colours, converted coordinates, order and tooltips.
 * A local style knows which dimensions are loaded and their dimension type IDs; a remote style
 * only knows dimension IDs, so colours follow the ID.
 */
public final class DimensionStyle {
    private static final List<String> VANILLA = List.of(
            "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    private final Viewer viewer;
    private final @Nullable Map<String, String> loadedTypes;

    private DimensionStyle(Viewer viewer, @Nullable Map<String, String> loadedTypes) {
        this.viewer = viewer;
        this.loadedTypes = loadedTypes;
    }

    /** Dimensions of this server; loadedTypes maps each loaded dimension to its dimension type ID. */
    public static DimensionStyle local(Viewer viewer, Map<String, String> loadedTypes) {
        return new DimensionStyle(viewer, Map.copyOf(loadedTypes));
    }

    /**
     * Dimensions of another server's catalog. The viewer stands on this server, so in none of
     * them: no dimension comes first or carries the gold ●, and no distance is known.
     */
    public static DimensionStyle remote(Viewer viewer) {
        return new DimensionStyle(new Viewer(viewer.permissions(), viewer.hasMod(), viewer.plainText(), null, null,
                viewer.yaw(), viewer.icons()), null);
    }

    public Viewer viewer() {
        return this.viewer;
    }

    /** A local dimension with waypoint files but no loaded level or world. */
    public boolean isUnloaded(String id) {
        return this.loadedTypes != null && !this.loadedTypes.containsKey(id);
    }

    public DimensionKind kind(String id) {
        String type = this.loadedTypes == null ? null : this.loadedTypes.get(id);
        return DimensionKind.of(type == null ? id : type);
    }

    public TextColor color(String id) {
        return this.isUnloaded(id) ? NamedTextColor.GRAY : this.kind(id).color();
    }

    /** The colour of a dimension by its ID alone, for places without a viewer such as the GUI. */
    public static NamedTextColor colorOf(String id) {
        return DimensionKind.of(id).color();
    }

    /** Overworld, Nether and End, or the ID's path in title case, without colour. */
    public static Component displayName(String id) {
        return switch (id) {
            case "minecraft:overworld" -> translatable("wp.dimension.overworld");
            case "minecraft:the_nether" -> translatable("wp.dimension.nether");
            case "minecraft:the_end" -> translatable("wp.dimension.end");
            default -> text(titleCase(id));
        };
    }

    /** twilightforest:twilight_forest becomes Twilight Forest: the path split on _, - and /. */
    public static String titleCase(String id) {
        StringBuilder name = new StringBuilder();
        for (String word : id.substring(id.indexOf(':') + 1).split("[_\\-/]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (name.length() > 0) {
                name.append(' ');
            }
            int first = word.codePointAt(0);
            name.appendCodePoint(Character.toUpperCase(first)).append(word.substring(Character.charCount(first)));
        }
        return name.length() == 0 ? id : name.toString();
    }

    /** The display name without colour, for a link that colours it. Plain-text viewers read "Display (id)". */
    public Component label(String id) {
        Component name = displayName(id);
        return this.viewer.plainText() ? Chat.concat(name, text(" (" + id + ")")) : name;
    }

    /** The name in its colour. Plain-text viewers read "Display (id)". */
    public Component name(String id) {
        return Chat.colored(this.label(id), this.color(id));
    }

    /** The viewer's dimension, then Overworld, Nether and End, then the rest A–Z by display name. */
    public Comparator<String> order() {
        return Comparator.comparingInt(this::rank)
                .thenComparing(DimensionStyle::titleCase, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Comparator.naturalOrder());
    }

    private int rank(String id) {
        if (this.viewer.isIn(id)) {
            return -1;
        }
        int vanilla = VANILLA.indexOf(id);
        return vanilla < 0 ? VANILLA.size() : vanilla;
    }

    /** Nether coordinates for Overworld types in red, Overworld ones for Nether types in green. */
    public @Nullable Component pairedCoordinates(String id, WaypointPos pos) {
        return switch (this.kind(id)) {
            case OVERWORLD -> translatable("wp.coordinates.nether", NamedTextColor.RED,
                    text(coordinates(BlockPosConverter.overWorldToNether(pos))));
            case NETHER -> translatable("wp.coordinates.overworld", NamedTextColor.GREEN,
                    text(coordinates(BlockPosConverter.netherToOverWorld(pos))));
            default -> null;
        };
    }

    public static String coordinates(WaypointPos pos) {
        return pos.x() + ", " + pos.y() + ", " + pos.z();
    }

    /** "12 waypoints in 3 lists", or "No lists yet". */
    public static Component counts(int waypoints, int lists) {
        if (lists == 0) {
            return translatable("wp.dimension.no_lists");
        }
        return translatable("wp.in", Chat.count("wp.count.waypoint", waypoints), Chat.count("wp.count.list", lists));
    }

    /** Name, ID, type or "Not loaded", counts, "● You are here", and a click hint. */
    public Tooltip tooltip(String id, @Nullable Component counts, @Nullable String hint) {
        Tooltip tooltip = Tooltip.of(Chat.colored(displayName(id), this.color(id))).line(text(id));
        if (this.loadedTypes != null) {
            String type = this.loadedTypes.get(id);
            tooltip = type == null ? tooltip.line("wp.dimension.not_loaded") : tooltip.line(typeLine(type));
        }
        if (counts != null) {
            tooltip = tooltip.line(counts);
        }
        if (this.viewer.isIn(id)) {
            tooltip = tooltip.line(hereLine());
        }
        return hint == null ? tooltip : tooltip.hint(hint);
    }

    private static Component typeLine(String type) {
        return switch (DimensionKind.of(type)) {
            case OVERWORLD -> translatable("wp.dimension.type.overworld");
            case NETHER -> translatable("wp.dimension.type.nether");
            case END -> translatable("wp.dimension.type.end");
            case MODDED -> translatable("wp.dimension.type.modded", text(type));
        };
    }

    /** The gold "● You are here". */
    public static Component hereLine() {
        return Chat.colored(Chat.concat(text(Chat.DOT + " "), translatable("wp.dimension.here")), NamedTextColor.GOLD);
    }

    /** The gold ● after the viewer's own dimension, or null for any other dimension and in plain text. */
    public @Nullable Component hereMark(String id) {
        if (!this.viewer.isIn(id) || this.viewer.plainText()) {
            return null;
        }
        return Chat.hover(this.viewer, text(Chat.DOT, NamedTextColor.GOLD), Tooltip.of(hereLine()));
    }
}
