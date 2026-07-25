package _959.server_waypoint.config;

import _959.server_waypoint.navigation.NavigationMethod;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class NavigationMethodSetJsonAdapter
        implements JsonSerializer<Set<NavigationMethod>>, JsonDeserializer<Set<NavigationMethod>> {
    @Override
    public JsonElement serialize(
            Set<NavigationMethod> methods,
            Type type,
            JsonSerializationContext context
    ) {
        validateNotEmpty(methods);
        JsonArray array = new JsonArray();
        for (NavigationMethod method : methods) {
            if (method == null) {
                throw new JsonParseException("Navigation method list cannot contain null");
            }
            array.add(method.id());
        }
        return array;
    }

    @Override
    public Set<NavigationMethod> deserialize(
            JsonElement json,
            Type type,
            JsonDeserializationContext context
    ) throws JsonParseException {
        if (json == null || !json.isJsonArray()) {
            throw new JsonParseException("Navigation methods must be a JSON array");
        }

        EnumSet<NavigationMethod> methods = EnumSet.noneOf(NavigationMethod.class);
        for (JsonElement element : json.getAsJsonArray()) {
            if (element == null
                    || !element.isJsonPrimitive()
                    || !((JsonPrimitive) element).isString()) {
                throw new JsonParseException("Each navigation method must be a string");
            }

            String id = element.getAsString();
            NavigationMethod method = NavigationMethod.fromId(id).orElseThrow(
                    () -> new JsonParseException("Unknown navigation method: " + id)
            );
            if (!methods.add(method)) {
                throw new JsonParseException("Duplicate navigation method: " + id);
            }
        }

        validateNotEmpty(methods);
        return Collections.unmodifiableSet(methods);
    }

    private static void validateNotEmpty(Set<NavigationMethod> methods) {
        if (methods == null || methods.isEmpty()) {
            throw new JsonParseException("At least one default navigation method is required");
        }
    }
}
