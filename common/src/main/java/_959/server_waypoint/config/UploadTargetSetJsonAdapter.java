package _959.server_waypoint.config;

import _959.server_waypoint.core.network.upload.UploadTarget;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class UploadTargetSetJsonAdapter
        implements JsonSerializer<Set<UploadTarget>>, JsonDeserializer<Set<UploadTarget>> {
    @Override
    public JsonElement serialize(Set<UploadTarget> targets, Type type, JsonSerializationContext context) {
        JsonArray array = new JsonArray();
        for (UploadTarget target : targets) {
            array.add(target.id());
        }
        return array;
    }

    @Override
    public Set<UploadTarget> deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
        if (!json.isJsonArray()) {
            throw new JsonParseException("waypointUploadPlatforms must be a JSON array");
        }

        EnumSet<UploadTarget> targets = EnumSet.noneOf(UploadTarget.class);
        for (JsonElement element : json.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("Each waypoint upload platform must be a string");
            }

            String id = element.getAsString();
            UploadTarget target = UploadTarget.fromId(id).orElseThrow(
                    () -> new JsonParseException("Unknown waypoint upload platform: " + id)
            );
            if (!targets.add(target)) {
                throw new JsonParseException("Duplicate waypoint upload platform: " + id);
            }
        }
        return Collections.unmodifiableSet(targets);
    }
}
