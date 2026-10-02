package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.util.NamespacedId;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the patch an Edit form sends: a field is set or cleared only when the form's value differs
 * from the saved waypoint. It holds no Minecraft classes, so it can be unit tested.
 */
final class WaypointFormPatch {
    private WaypointFormPatch() {
    }

    /**
     * The waypoint as saved. {@code displayNameOverride} is null when it has no override, and empty
     * when its marker deliberately shows no name.
     */
    record Saved(
            String name,
            @Nullable String displayNameOverride,
            String initials,
            WaypointPos position,
            int rgb,
            int yaw,
            boolean global,
            List<String> keywords,
            String description,
            @Nullable NamespacedId icon
    ) {
        /** What the Edit title shows: the override, or the name when there is none or it is empty. */
        String titleName() {
            return this.displayNameOverride == null || this.displayNameOverride.isEmpty() ? this.name : this.displayNameOverride;
        }

        static Saved of(SimpleWaypoint waypoint) {
            return new Saved(
                    waypoint.name(),
                    waypoint.displayNameOverride(),
                    waypoint.initials(),
                    waypoint.pos(),
                    waypoint.rgb(),
                    waypoint.yaw(),
                    waypoint.global(),
                    waypoint.keywords(),
                    waypoint.description(),
                    waypoint.icon()
            );
        }
    }

    /** What the form holds. {@code displayName} is the Display name field, where empty means no override. */
    record Values(
            String name,
            String displayName,
            String initials,
            WaypointPos position,
            int rgb,
            int yaw,
            boolean global,
            List<String> keywords,
            String description,
            @Nullable NamespacedId icon
    ) {
    }

    static WaypointPatch build(Saved saved, Values values) {
        return new WaypointPatch(
                changed(saved.name(), values.name()),
                displayName(saved.displayNameOverride(), values.displayName()),
                changed(saved.initials(), values.initials()),
                changed(saved.position(), values.position()),
                changed(saved.rgb() & 0xFFFFFF, values.rgb() & 0xFFFFFF),
                changed(saved.yaw(), values.yaw()),
                changed(saved.global(), values.global()),
                changed(saved.keywords(), values.keywords()),
                changed(saved.description(), values.description()),
                icon(saved.icon(), values.icon())
        );
    }

    /**
     * The display-name part. The field holds only the override, so an empty field over a saved override
     * clears it, and over no override or an empty one changes nothing.
     */
    static PatchField<String> displayName(@Nullable String savedOverride, String field) {
        if (savedOverride == null) {
            return field.isEmpty() ? PatchField.unchanged() : PatchField.set(field);
        }
        if (field.equals(savedOverride)) {
            return PatchField.unchanged();
        }
        return field.isEmpty() ? PatchField.clear() : PatchField.set(field);
    }

    /** Whether the patch sets or clears any field. */
    static boolean changesAnything(WaypointPatch patch) {
        return !patch.identifier().isUnchanged()
                || !patch.displayName().isUnchanged()
                || !patch.initials().isUnchanged()
                || !patch.position().isUnchanged()
                || !patch.color().isUnchanged()
                || !patch.yaw().isUnchanged()
                || !patch.visibility().isUnchanged()
                || !patch.keywords().isUnchanged()
                || !patch.description().isUnchanged()
                || !patch.icon().isUnchanged();
    }

    private static PatchField<NamespacedId> icon(@Nullable NamespacedId saved, @Nullable NamespacedId selected) {
        if (Objects.equals(saved, selected)) {
            return PatchField.unchanged();
        }
        return selected == null ? PatchField.clear() : PatchField.set(selected);
    }

    private static <T> PatchField<T> changed(T saved, T value) {
        return Objects.equals(saved, value) ? PatchField.unchanged() : PatchField.set(value);
    }
}
