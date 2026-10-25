package _959.server_waypoint.perf.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Read-only owner-thread observations of the installed release, never a copied implementation. */
final class NavigationAssertions {
    private NavigationAssertions() {
    }

    static JsonObject snapshot() throws Exception {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Paper navigation snapshot requires server owner thread");
        }
        var plugin = Bukkit.getPluginManager().getPlugin("ServerWaypoint");
        if (plugin == null) {
            throw new IllegalStateException("Published plugin absent");
        }
        Object service = field(plugin, "navigationService");
        Object itemManager = field(plugin, "navigationItemManager");
        Object mapCache = field(plugin, "navigationMapCache");
        Map<?, ?> sessions = map(service, "sessions");
        Map<?, ?> handlers = map(service, "handlers");
        Map<?, ?> cachedMaps = map(mapCache, "maps");
        JsonObject result = new JsonObject();
        result.addProperty("origin", "installed-release-native-navigation-state");
        result.addProperty("tick", Bukkit.getCurrentTick());
        result.add("supportedMethods", methods((Collection<?>) call(service, "supportedNavigationMethods")));
        int displayCount = 0;
        int bossbarCount = 0;
        int mapTargets = 0;
        for (var handler : handlers.entrySet()) {
            String method = call(handler.getKey(), "id").toString();
            if (method.equals("text_display")) {
                displayCount += map(handler.getValue(), "displays").size();
            } else if (method.equals("bossbar")) {
                bossbarCount += map(handler.getValue(), "bossbars").size();
            }
        }
        for (Object cached : cachedMaps.values()) {
            mapTargets += map(field(cached, "renderer"), "targets").size();
        }
        int tasks = 0;
        Set<Object> listeners = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var registration : HandlerList.getRegisteredListeners(plugin)) {
            Object listener = registration.getListener();
            if (listener.getClass().getName().endsWith(".NavigationProtectionListener") && listeners.add(listener)) {
                tasks += map(listener, "navigationTickTasks").size();
            }
        }
        if (listeners.size() != 1) {
            throw new IllegalStateException("Expected exactly one installed navigation protection listener");
        }
        JsonArray players = new JsonArray();
        int itemCount = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            JsonObject value = new JsonObject();
            value.addProperty("name", player.getName());
            value.addProperty("uuid", uuid.toString());
            value.addProperty("dimension", player.getWorld().getKey().toString());
            value.addProperty("dead", player.isDead());
            value.addProperty("health", player.getHealth());
            value.addProperty("entityId", player.getEntityId());
            value.add("position", position(player.getLocation()));
            Object session = sessions.get(uuid);
            value.add("methods", session == null ? new JsonArray() : methods((Collection<?>) call(session, "enabledMethods")));
            if (session != null) {
                Object target = call(session, "target");
                JsonObject targetValue = new JsonObject();
                targetValue.addProperty("dimension", call(target, "dimensionName").toString());
                targetValue.addProperty("list", call(target, "listName").toString());
                targetValue.addProperty("waypoint", call(target, "waypointName").toString());
                targetValue.addProperty("displayName", call(target, "waypointDisplayName").toString());
                targetValue.addProperty("rgb", (Number) call(target, "rgb"));
                Object targetPosition = call(target, "position");
                JsonArray coordinates = new JsonArray();
                for (String coordinate : new String[]{"x", "y", "z"}) {
                    coordinates.add((Number) call(targetPosition, coordinate));
                }
                targetValue.add("position", coordinates);
                value.add("target", targetValue);
            }
            JsonArray items = new JsonArray();
            Map<String, Integer> ordinary = new TreeMap<>();
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack stack = contents[slot];
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                boolean tagged = (Boolean) itemManager.getClass().getMethod("isNavigationItem", ItemStack.class).invoke(itemManager, stack);
                if (!tagged) {
                    ItemStack identity = stack.clone();
                    identity.setAmount(1);
                    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.serializeAsBytes()));
                    ordinary.merge(stack.getType().getKey() + ":" + hash, stack.getAmount(), Integer::sum);
                    continue;
                }
                itemCount += stack.getAmount();
                Optional<?> method = (Optional<?>) itemManager.getClass().getMethod("method", ItemStack.class).invoke(itemManager, stack);
                JsonObject item = new JsonObject();
                item.addProperty("slot", slot);
                item.addProperty("amount", stack.getAmount());
                item.addProperty("method", method.isPresent() ? call(method.get(), "id").toString() : "unknown");
                var meta = stack.getItemMeta();
                item.addProperty("caption", plain(meta.displayName()));
                if (meta instanceof CompassMeta compass) {
                    Location lodestone = compass.getLodestone();
                    if (lodestone != null) {
                        JsonObject destination = new JsonObject();
                        destination.addProperty("dimension", lodestone.getWorld().getKey().toString());
                        destination.add("position", position(lodestone));
                        item.add("compassTarget", destination);
                    }
                    item.addProperty("lodestoneTracked", compass.isLodestoneTracked());
                } else if (meta instanceof MapMeta mapMeta && mapMeta.hasMapView()) {
                    MapView view = mapMeta.getMapView();
                    JsonObject state = mapState(view);
                    Object cached = cachedMaps.get(uuid);
                    if (cached != null) {
                        Object marker = map(field(cached, "renderer"), "targets").get(uuid);
                        if (marker != null) {
                            state.addProperty("cursorX", (Number) call(marker, "x"));
                            state.addProperty("cursorZ", (Number) call(marker, "z"));
                            state.addProperty("caption", plain((Component) call(marker, "caption")));
                        }
                    }
                    item.add("mapState", state);
                }
                items.add(item);
            }
            JsonObject ordinaryValue = new JsonObject();
            ordinary.forEach(ordinaryValue::addProperty);
            value.add("ordinary", ordinaryValue);
            value.add("items", items);
            for (var handler : handlers.entrySet()) {
                String method = call(handler.getKey(), "id").toString();
                if (method.equals("bossbar")) {
                    BossBar bossbar = (BossBar) map(handler.getValue(), "bossbars").get(uuid);
                    if (bossbar != null) {
                        value.addProperty("bossbar", plain(bossbar.name()));
                    }
                } else if (method.equals("text_display")) {
                    Object state = map(handler.getValue(), "displays").get(uuid);
                    if (state != null) {
                        Object display = call(state, "display");
                        Object text = call(display, "getText");
                        value.addProperty("text", call(text, "getString").toString());
                        value.addProperty("textDisplayId", (Number) call(display, "getId"));
                    }
                }
            }
            players.add(value);
        }
        result.add("players", players);
        JsonObject resources = new JsonObject();
        resources.addProperty("sessions", sessions.size());
        resources.addProperty("tasks", tasks);
        resources.addProperty("entities", displayCount);
        resources.addProperty("bossbars", bossbarCount);
        resources.addProperty("items", itemCount);
        resources.addProperty("mapTargets", mapTargets);
        resources.addProperty("mapCacheEntries", cachedMaps.size());
        resources.addProperty("targetIndexEntries", map(service, "sessionPlayerUuidsByTarget").size());
        resources.addProperty("tickCounters", map(service, "tickCounters").size());
        result.add("resources", resources);
        return result;
    }

    private static JsonObject mapState(MapView view) throws Exception {
        JsonObject value = new JsonObject();
        value.addProperty("id", view.getId());
        value.addProperty("dimension", view.getWorld().getKey().toString());
        value.addProperty("centerX", view.getCenterX());
        value.addProperty("centerZ", view.getCenterZ());
        value.addProperty("scale", view.getScale().ordinal());
        var saved = ((CraftWorld) view.getWorld()).getHandle().getMapData(new MapId(view.getId()));
        if (saved == null || saved.colors.length != 16384) {
            throw new IllegalStateException("Native map terrain data absent/invalid");
        }
        // Copy the actual saved-data pixels on their owner thread. The
        // controller compares these bytes with independently saved regions.
        byte[] colors = saved.colors.clone();
        value.addProperty("colorsBase64", Base64.getEncoder().encodeToString(colors));
        value.addProperty("colorsSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(colors)));
        var checksum = new java.util.zip.CRC32();
        checksum.update(colors);
        value.addProperty("colorsCrc32", checksum.getValue());
        return value;
    }

    private static JsonArray position(Location location) {
        JsonArray value = new JsonArray();
        value.add(location.getBlockX());
        value.add(location.getBlockY());
        value.add(location.getBlockZ());
        return value;
    }

    private static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static JsonArray methods(Collection<?> methods) throws ReflectiveOperationException {
        JsonArray result = new JsonArray();
        for (Object method : methods) {
            result.add(call(method, "id").toString());
        }
        return result;
    }

    private static Object call(Object object, String name) throws ReflectiveOperationException {
        var method = object.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(object);
    }

    private static Map<?, ?> map(Object object, String name) throws ReflectiveOperationException {
        return (Map<?, ?>) field(object, name);
    }

    static Object field(Object object, String name) throws ReflectiveOperationException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
                // Inspect only this pinned release's known class hierarchy.
            }
        }
        throw new NoSuchFieldException(object.getClass().getName() + "." + name);
    }
}
