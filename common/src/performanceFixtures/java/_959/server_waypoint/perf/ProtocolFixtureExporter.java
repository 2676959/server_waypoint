package _959.server_waypoint.perf;

import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.network.*;
import _959.server_waypoint.core.network.buffer.*;
import _959.server_waypoint.core.network.codec.*;
import _959.server_waypoint.core.network.message.*;
import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import com.google.gson.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import java.util.function.Consumer;

/** Runs only with published production classes and external dependencies on its classpath. */
public final class ProtocolFixtureExporter {
    private static final String PAPER_RELEASE_SHA256 = "0a1d2ddc07dff13dee1af78c8e1f3b0a50024f5d3b8686f733a512db0fa8ad57";
    private static final UUID TRANSFER = UUID.fromString("00000000-0000-0000-0000-000000000959");

    public static void verifyArtifact(Path artifact) throws Exception {
        if (!Files.isRegularFile(artifact) || !hash(Files.readAllBytes(artifact)).equals(PAPER_RELEASE_SHA256)) {
            throw new IllegalArgumentException("Expected immutable published Paper 4.0.0 artifact; production class hash/provenance mismatch");
        }
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] bytes(Consumer<ByteBuf> encoder) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            encoder.accept(buffer);
            byte[] result = new byte[buffer.readableBytes()];
            buffer.readBytes(result);
            return result;
        } finally { buffer.release(); }
    }

    private static void vector(JsonArray vectors, String name, String kind, byte[] bytes, boolean accepted) {
        JsonObject value = new JsonObject();
        value.addProperty("name", name);
        value.addProperty("kind", kind);
        value.addProperty("hex", HexFormat.of().formatHex(bytes));
        value.addProperty("accepted", accepted);
        vectors.add(value);
    }

    public static JsonObject vectors() throws Exception {
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", 1);
        result.addProperty("protocolVersion", Class.forName("_959.server_waypoint.ProtocolVersion").getField("PROTOCOL_VERSION").getInt(null));
        JsonArray channels = new JsonArray();
        for (MessageChannelID channel : MessageChannelID.values()) { channels.add(channel.ID); }
        result.add("channels", channels);
        JsonArray vectors = new JsonArray();
        byte[] client = bytes(out -> ClientHandshakeCodec.encode(out, new ClientHandshakeBuffer(2)));
        ByteBuf input = Unpooled.wrappedBuffer(client);
        try {
            if (ClientHandshakeCodec.decode(input).version() != 2 || input.isReadable()) { throw new IllegalStateException("handshake round trip"); }
        } finally { input.release(); }
        vector(vectors, "client-v2", "client-handshake", client, true);
        vector(vectors, "client-incompatible", "client-handshake", bytes(out -> ClientHandshakeCodec.encode(out, new ClientHandshakeBuffer(999))), true);
        vector(vectors, "client-truncated", "client-handshake", new byte[] {0, 0, 2}, false);
        vector(vectors, "server-v2", "server-handshake", bytes(out -> ServerHandshakeCodec.encode(out, new ServerHandshakeBuffer(2, 959, true))), true);
        var request = new UploadRequestBuffer(TRANSFER, List.of("minecraft:overworld"), "perf", "origin", UploadTarget.values()[0]);
        vector(vectors, "upload-request", "upload-request", bytes(out -> request.encode(out)), true);
        List<ChunkedMessage> messages = List.of(
                new ClientUpdateRequestMessage(Collections.nCopies(4096, new DimensionSyncIdentifier("minecraft:overworld", List.of()))),
                new WaypointEditRequestMessage(959L, "minecraft:overworld", "perf", "origin", 17, WaypointPatch.empty()),
                new RemoteCatalogMessage(TRANSFER, RemoteCatalogState.AVAILABLE, Map.of()));
        int messageIndex = 0;
        for (ChunkedMessage message : messages) {
            for (boolean compressed : new boolean[] {false, true}) {
                var frames = ChunkedMessageManager.createTransfer(message, compressed);
                var manager = new ChunkedMessageManager<String>();
                int[] applied = {0};
                var invalidManager = new ChunkedMessageManager<String>();
                boolean rejectedChecksum = false;
                for (var original : frames) {
                    var frame = MessageChunkBuffer.chunk(TRANSFER, original.messageTypeId(), original.sequence(), original.chunkCount(), original.compressed(), original.uncompressedSize(), original.checksum(), original.data());
                    byte[] encoded = bytes(out -> MessageChunkCodec.encode(out, frame));
                    ByteBuf frameInput = Unpooled.wrappedBuffer(encoded);
                    MessageChunkBuffer decoded;
                    try { decoded = MessageChunkCodec.decode(frameInput); } finally { frameInput.release(); }
                    manager.receiveAndApply("fixture", decoded, value -> {
                        if (value.getType().id() != message.getType().id()) { throw new IllegalStateException("message type mismatch"); }
                        applied[0]++;
                    });
                    vector(vectors, "message-" + messageIndex + "-" + compressed + "-" + frame.sequence(), "message-chunk", encoded, true);
                    byte[] bad = encoded.clone();
                    bad[33] ^= 1;
                    ByteBuf badInput = Unpooled.wrappedBuffer(bad);
                    try {
                        MessageChunkBuffer rejected = MessageChunkCodec.decode(badInput);
                        try {
                            invalidManager.receiveAndApply("bad", rejected, value -> { throw new IllegalStateException("bad checksum applied"); });
                        } catch (IllegalArgumentException expected) { rejectedChecksum = true; }
                    } finally { badInput.release(); }
                    vector(vectors, "bad-checksum-" + messageIndex + "-" + compressed + "-" + frame.sequence(), "message-chunk", bad, false);
                }
                if (applied[0] != 1) { throw new IllegalStateException("release chunk round trip did not apply exactly once"); }
                if (!rejectedChecksum) { throw new IllegalStateException("bad checksum not rejected"); }
                manager.clearAll();
                invalidManager.clearAll();
            }
            messageIndex++;
        }
        result.add("vectors", vectors);
        return result;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) { throw new IllegalArgumentException("published JAR and new output directory required"); }
        Path artifact = Path.of(args[0]).toRealPath();
        verifyArtifact(artifact);
        Path output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output)) { throw new IllegalArgumentException("output must be new"); }
        JsonObject result = vectors();
        if (args.length == 3) {
            JsonArray rust = JsonParser.parseString(Files.readString(Path.of(args[2]))).getAsJsonArray();
            int verified = 0;
            for (var entry : rust) {
                JsonObject group = entry.getAsJsonObject();
                boolean accepted = false;
                int[] applied = {0};
                var receiver = new ChunkedMessageManager<String>();
                try {
                    for (var frame : group.getAsJsonArray("frames")) {
                        ByteBuf input = Unpooled.wrappedBuffer(HexFormat.of().parseHex(frame.getAsString()));
                        try {
                            if (group.has("kind") && group.get("kind").getAsString().equals("upload-request")) {
                                UploadRequestBuffer request = UploadRequestCodec.decode(input, new DecodingContext(64 * 1024 * 1024, 1_000_000));
                                if (!"perf".equals(request.listName()) || !"origin".equals(request.waypointName())) { throw new IllegalStateException("Rust upload semantic mismatch"); }
                                if (input.isReadable()) { throw new IllegalArgumentException("trailing Rust upload bytes"); }
                                applied[0]++;
                            } else {
                                MessageChunkBuffer decoded = MessageChunkCodec.decode(input);
                                receiver.receiveAndApply("rust", decoded, value -> {
                                    if (value instanceof WaypointEditRequestMessage edit && (edit.expectedListRevision() != 17 || edit.requestId() != 959 || !edit.waypointIdentifier().equals("origin"))) { throw new IllegalStateException("Rust edit semantic mismatch"); }
                                    applied[0]++;
                                });
                            }
                        } finally { input.release(); }
                    }
                    accepted = applied[0] == 1;
                } catch (IllegalArgumentException exception) { accepted = false; }
                finally { receiver.clearAll(); }
                if (accepted != group.get("accepted").getAsBoolean()) { throw new IllegalStateException("Rust frame outcome differs in release codec"); }
                verified++;
            }
            if (verified < 2) { throw new IllegalArgumentException("Rust valid/invalid proof missing"); }
            result.addProperty("rustFramesVerified", verified);
        }
        JsonObject classes = new JsonObject();
        try (JarFile jar = new JarFile(artifact.toFile())) {
            for (String name : List.of("_959.server_waypoint.ProtocolVersion", ClientHandshakeCodec.class.getName(), ServerHandshakeCodec.class.getName(), MessageChunkCodec.class.getName(), ChunkedMessageManager.class.getName(), UploadRequestCodec.class.getName(), WaypointEditRequestMessageCodec.class.getName(), RemoteCatalogMessageCodec.class.getName())) {
                Class<?> loaded = Class.forName(name);
                Path source = Path.of(loaded.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
                if (!source.equals(artifact)) { throw new IllegalArgumentException("current/project production class loaded: " + source); }
                String entry = name.replace('.', '/') + ".class";
                byte[] expected;
                try (var stream = jar.getInputStream(jar.getJarEntry(entry))) { expected = stream.readAllBytes(); }
                try (var stream = loaded.getResourceAsStream("/" + entry)) {
                    if (stream == null || !Arrays.equals(expected, stream.readAllBytes())) { throw new IllegalArgumentException("loaded production class hash mismatch"); }
                }
                classes.addProperty(name, hash(expected));
            }
        }
        result.addProperty("artifactSha256", PAPER_RELEASE_SHA256);
        result.add("productionClasses", classes);
        Files.createDirectory(output);
        Files.writeString(output.resolve("vectors.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
    }
}
