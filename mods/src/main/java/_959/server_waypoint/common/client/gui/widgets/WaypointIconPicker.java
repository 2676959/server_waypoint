package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.render.WaypointIconRenderer;
import _959.server_waypoint.common.client.gui.render.WidgetTextures;
import _959.server_waypoint.common.client.integrations.VoxelMapIconIds;
import _959.server_waypoint.util.NamespacedId;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
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
    private static final int CLEAR_BUTTON_SIZE = 13;

    private final ComboBoxWidget menu;
    private final IconButton clearButton;
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
        this.catalog = available.stream().distinct().sorted(Comparator.comparing(NamespacedId::toString)).toList();
        this.menu = new ComboBoxWidget(0, 0, width,
                Component.translatable("waypoint.icon.label"), Minecraft.getInstance().font,
                this.catalog.stream().map(NamespacedId::toString).toList(), "", this::onTextChanged);
        this.menu.setSuggestionsMatcher(WaypointIconPicker::matchesSuggestion);
        this.menu.setTextColorProvider(() -> inputColor(this.catalog, this.menu.getValue(), this.menu.isFocused()));
        this.menu.setRenderPopupSeparately(true);
        this.menu.setPlaceholder(() -> Component.translatable("waypoint.form.no_icon"));
        Component clearLabel = Component.translatable("waypoint.icon.clear");
        this.clearButton = new IconButton(0, 0, CLEAR_BUTTON_SIZE, CLEAR_BUTTON_SIZE, clearLabel,
                WidgetTextures.CLEAR_ICON, () -> select(null, true))
                .withoutBackground()
                .withIconPadding(3)
                .withIconRegion(10, 9, 28, 29, 48, 48);
        this.clearButton.setTooltip(clearLabel);
        this.clearButton.active = false;
    }

    private void onTextChanged(String value) {
        NamespacedId id = resolveInput(this.catalog, value);
        if (id != null) {
            this.selectedIcon = id;
            this.clearButton.active = true;
            // Keep the typed text so entering "diamond_sword" is not interrupted at "diamond".
            this.callback.accept(id);
        }
    }

    private void select(@Nullable NamespacedId id, boolean notify) {
        this.selectedIcon = id;
        // There is nothing to remove while no icon is selected.
        this.clearButton.active = id != null;
        this.menu.setValue(id == null ? "" : id.toString());
        if (notify) this.callback.accept(id);
    }

    public @Nullable NamespacedId getSelectedIcon() {
        return this.selectedIcon;
    }

    public void setSelectedIcon(@Nullable NamespacedId icon) {
        select(icon, false);
    }

    public WaypointIconRenderer.ResolvedIcon preview(int mouseX, int mouseY) {
        return WaypointIconRenderer.resolve(previewId(this.catalog, this.menu.getValue(),
                this.menu.getHoveredValue(mouseX, mouseY)));
    }

    public ComboBoxWidget menu() {
        return this.menu;
    }

    public IconButton clearButton() {
        return this.clearButton;
    }

    public static List<NamespacedId> filter(List<NamespacedId> ids, String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        return ids.stream().distinct().sorted(Comparator.comparing(NamespacedId::toString))
                .filter(id -> matches(id, needle)).toList();
    }

    static boolean matchesSuggestion(String suggestion, String query) {
        return matches(NamespacedId.parse(suggestion), query.toLowerCase(Locale.ROOT));
    }

    private static boolean matches(NamespacedId id, String query) {
        if (id.namespace().equals("voxelmap")) {
            return !query.isEmpty() && (query.indexOf(':') >= 0
                    ? SharedSuggestionProvider.matchesSubStr(query, id.toString())
                    : SharedSuggestionProvider.matchesSubStr(query, id.namespace()));
        }
        return ResourceIdSuggestions.matches(id, query);
    }

    static @Nullable NamespacedId resolveInput(List<NamespacedId> catalog, String value) {
        NamespacedId id = ResourceIdSuggestions.parseInput(value);
        return id != null && catalog.contains(id) ? id : null;
    }

    static int inputColor(List<NamespacedId> catalog, String value, boolean focused) {
        return WidgetThemeState.matchingInputText(value, resolveInput(catalog, value) != null, focused);
    }

    static @Nullable NamespacedId previewId(List<NamespacedId> catalog, String value, @Nullable String hovered) {
        return resolveInput(catalog, hovered == null ? value : hovered);
    }
}
