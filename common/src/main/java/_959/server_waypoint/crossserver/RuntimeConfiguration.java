package _959.server_waypoint.crossserver;

import _959.server_waypoint.crossserver.transport.*;
import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;

/** Bounded GSON configuration shared by backend and coordinator lifecycle owners. */
public final class RuntimeConfiguration {
    private RuntimeConfiguration() { }
    public static JsonObject read(Path directory, Set<String> fields) throws IOException {
        Files.createDirectories(directory);
        Path path = directory.resolve("cross-server.json");
        if (!Files.exists(path)) Files.writeString(path, "{\n    \"enabled\": false,\n    \"transportMode\": \"NOISE_KK\"\n}\n", StandardOpenOption.CREATE_NEW);
        if (Files.size(path) > 1024 * 1024) throw new IOException("Cross-server configuration too large");
        JsonObject config = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        if (!fields.containsAll(config.keySet())) throw new IOException("Unknown cross-server configuration field");
        if (config.has("protocolVersion") && config.get("protocolVersion").getAsInt() != CrossServerProtocol.PROTOCOL_VERSION) throw new IOException("Unsupported cross-server protocol");
        return config;
    }
    public static boolean enabled(JsonObject config) { return config.has("enabled") && config.get("enabled").getAsBoolean(); }
    public static String text(JsonObject config, String key, String fallback) {
        return config.has(key) ? config.get(key).getAsString() : fallback;
    }
    public static TransportMode mode(JsonObject config) { return TransportMode.valueOf(text(config, "transportMode", "NOISE_KK")); }
    public static TcpEndpoint endpoint(JsonObject config, String key) throws IOException {
        String address = text(config, key, "127.0.0.1:25580");
        int colon = address.lastIndexOf(':');
        if (colon <= 0) throw new IOException("Endpoint requires host and port");
        TcpEndpoint endpoint = new TcpEndpoint(address.substring(0, colon), Integer.parseInt(address.substring(colon + 1)));
        endpoint.validate(mode(config)); return endpoint;
    }
    public static void rejectCryptoInPlaintext(JsonObject config) throws IOException {
        if (mode(config) == TransportMode.PLAINTEXT && (config.has("coordinatorPublicKey") || config.has("credentialsDirectory") || config.has("requiredSuite"))) {
            throw new IOException("Plaintext cannot configure cryptographic fields");
        }
        if (config.has("requiredSuite") && !text(config, "requiredSuite", "").equals("Noise_KK_25519_AESGCM_SHA256")) throw new IOException("Unsupported suite");
    }
}
