package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.render.WaypointIconRenderer;
import _959.server_waypoint.common.client.integrations.VoxelMapIconIds;
import _959.server_waypoint.util.NamespacedId;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Searchable icon selection backed by the current client's item registry. */
public final class WaypointIconPicker {
    private final ComboBoxWidget menu;
    private final TranslucentButton clearButton;
    private final List<NamespacedId> catalog;
    private final Consumer<@Nullable NamespacedId> callback;
    private @Nullable NamespacedId selectedIcon;

    public WaypointIconPicker(int width, Consumer<@Nullable NamespacedId> callback) {
        this.callback = Objects.requireNonNull(callback);
        ArrayList<NamespacedId> available = new ArrayList<>(VoxelMapIconIds.ids());
        BuiltInRegistries.ITEM.keySet().forEach(key -> {
            if (BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR) != Items.AIR) {
                available.add(new NamespacedId(key.getNamespace(), key.getPath()));
            }
        });
        this.catalog = filter(available, "");
        this.menu = new ComboBoxWidget(0, 0, width,
                Component.translatable("waypoint.icon.label"), Minecraft.getInstance().font,
                this.catalog.stream().map(NamespacedId::toString).toList(), "", this::onTextChanged);
        this.menu.setSuggestionsProvider(() -> filter(this.catalog, this.menu.getValue()).stream()
                .limit(64).map(NamespacedId::toString).toList());
        this.menu.setRenderPopupSeparately(true);
        this.clearButton = new TranslucentButton(0, 0, 42, 11,
                Component.translatable("waypoint.icon.clear"), () -> select(null, true));
    }

    private void onTextChanged(String value) {
        try {
            NamespacedId id = NamespacedId.parse(value);
            if (this.catalog.contains(id)) {
                select(id, true);
            }
        } catch (IllegalArgumentException ignored) {
            // A partial search query does not change the saved selection.
        }
    }

    private void select(@Nullable NamespacedId id, boolean notify) {
        this.selectedIcon = id;
        this.menu.setValue(id == null ? "" : id.toString());
        if (notify) this.callback.accept(id);
    }

    public @Nullable NamespacedId getSelectedIcon() {
        return this.selectedIcon;
    }

    public void setSelectedIcon(@Nullable NamespacedId icon) {
        select(icon, false);
    }

    public WaypointIconRenderer.ResolvedIcon preview() {
        return WaypointIconRenderer.resolve(this.selectedIcon);
    }

    public ComboBoxWidget menu() {
        return this.menu;
    }

    public TranslucentButton clearButton() {
        return this.clearButton;
    }

    public static List<NamespacedId> filter(List<NamespacedId> ids, String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        return ids.stream().distinct().sorted(Comparator.comparing(NamespacedId::toString))
                .filter(id -> id.toString().contains(needle)).toList();
    }
}
