package _959.server_waypoint.crossserver;

import _959.server_waypoint.crossserver.transport.*;
import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Bounded GSON configuration shared by backend and coordinator lifecycle owners. */
public final class RuntimeConfiguration {
    private static final Set<String> BACKEND_FIELDS = Set.of("enabled", "transportMode", "serverId", "coordinator",
            "protocolVersion", "requiredSuite", "credentialsDirectory", "coordinatorPublicKey",
            "catalogExport", "serverIconItem");
    private static final Set<String> COORDINATOR_FIELDS = Set.of("enabled", "transportMode", "listen",
            "protocolVersion", "requiredSuite", "credentialsDirectory", "backends", "proxyPermission");

    private RuntimeConfiguration() { }

    public static JsonObject readBackend(Path directory) throws IOException {
        return read(directory, BACKEND_FIELDS, """
                {
                    "enabled": false,
                    "transportMode": "NOISE_KK",
                    "serverId": "replace-with-server-id",
                    "coordinator": "127.0.0.1:25580",
                    "protocolVersion": %d,
                    "requiredSuite": "Noise_KK_25519_AESGCM_SHA256",
                    "credentialsDirectory": "credentials",
                    "coordinatorPublicKey": "PASTE_COORDINATOR_PUBLIC_KEY_HERE",
                    "catalogExport": "PUBLIC",
                    "serverIconItem": "%s"
                }
                """.formatted(CrossServerProtocol.PROTOCOL_VERSION, ServerIcon.DEFAULT));
    }

    public static JsonObject readCoordinator(Path directory) throws IOException {
        return read(directory, COORDINATOR_FIELDS, """
                {
                    "enabled": false,
                    "transportMode": "NOISE_KK",
                    "listen": "127.0.0.1:25580",
                    "protocolVersion": %d,
                    "requiredSuite": "Noise_KK_25519_AESGCM_SHA256",
                    "credentialsDirectory": "credentials",
                    "proxyPermission": "",
                    "backends": {
                        "replace-with-server-id": {
                            "enabled": false,
                            "velocityServer": "replace-with-velocity-server-name",
                            "publicKey": "PASTE_BACKEND_PUBLIC_KEY_HERE"
                        }
                    }
                }
                """.formatted(CrossServerProtocol.PROTOCOL_VERSION));
    }

    private static JsonObject read(Path directory, Set<String> fields, String defaultContent) throws IOException {
        Files.createDirectories(directory);
        Path path = directory.resolve("cross-server.json");
        if (!Files.exists(path)) Files.writeString(path, defaultContent, StandardOpenOption.CREATE_NEW);
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
    public static TransportMode mode(JsonObject config) {
        String value = text(config, "transportMode", "NOISE_KK");
        try {
            return TransportMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid transportMode '" + value
                    + "' in cross-server.json; use NOISE_KK or PLAINTEXT", exception);
        }
    }
    public static TcpEndpoint endpoint(JsonObject config, String key) throws IOException {
        String address = text(config, key, "127.0.0.1:25580");
        int colon = address.lastIndexOf(':');
        if (colon <= 0) throw new IOException("Endpoint requires host and port");
        TcpEndpoint endpoint = new TcpEndpoint(address.substring(0, colon), Integer.parseInt(address.substring(colon + 1)));
        endpoint.validate(mode(config)); return endpoint;
    }
    public static void rejectCryptoInPlaintext(JsonObject config) throws IOException {
        if (mode(config) == TransportMode.PLAINTEXT) {
            List<String> conflictingFields = Stream.of("coordinatorPublicKey", "credentialsDirectory", "requiredSuite")
                    .filter(config::has).toList();
            if (!conflictingFields.isEmpty()) {
                throw new IOException("transportMode PLAINTEXT conflicts with " + String.join(", ", conflictingFields)
                        + " in cross-server.json; remove these fields for plaintext, or set transportMode to NOISE_KK");
            }
        }
        if (config.has("requiredSuite") && !text(config, "requiredSuite", "").equals("Noise_KK_25519_AESGCM_SHA256")) {
            throw new IOException("Unsupported requiredSuite in cross-server.json; use Noise_KK_25519_AESGCM_SHA256 or remove the field");
        }
    }
}
