package _959.server_waypoint.perf;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public record ProbeCommand(String runId, String attemptId, String operation, JsonObject data) {
    public static ProbeCommand parse(String[] args) {
        if (args.length != 4) {
            throw new IllegalArgumentException("runId attemptId operation base64-json required");
        }
        UUID.fromString(args[0]);
        UUID.fromString(args[1]);
        JsonObject data = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(args[3]), StandardCharsets.UTF_8)).getAsJsonObject();
        if (!data.has("schemaVersion") || data.get("schemaVersion").getAsInt() != 1) {
            throw new IllegalArgumentException("unknown schemaVersion");
        }
        return new ProbeCommand(args[0], args[1], args[2], data);
    }
}
