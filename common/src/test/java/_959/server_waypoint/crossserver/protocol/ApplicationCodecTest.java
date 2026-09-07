package _959.server_waypoint.crossserver.protocol;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static _959.server_waypoint.crossserver.protocol.ApplicationMessage.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationCodecTest {
    private static final ApplicationCodec CODEC = new ApplicationCodec(ProtocolLimits.DEFAULT);
    private static final RemoteServerId SOURCE = new RemoteServerId("a");
    private static final RemoteServerId DESTINATION = new RemoteServerId("b");
    private static final RemoteRevision REVISION = new RemoteRevision(9);
    private static final UUID REQUEST = new UUID(1, 2);
    private static final UUID PLAYER = new UUID(3, 4);
    private static final UUID HANDOFF = new UUID(5, 6);
    private static final RemoteWaypointKey KEY = new RemoteWaypointKey(DESTINATION, "世界", " List ", "Café");
    private static final HandoffBinding BINDING = new HandoffBinding(HANDOFF, PLAYER, SOURCE, KEY,
            Action.TELEPORT, 2_000_000_000_000L);

    static Stream<ApplicationMessage> messages() {
        byte[] catalog = CODEC.encodeCatalog(catalog());
        return Stream.of(
                new RegisterServer(SOURCE, 1, Set.of(1, 42)),
                new RegisterResult(SOURCE, Result.SUCCESS),
                new Heartbeat(),
                new CatalogMetadata(DESTINATION, "Other world", REVISION, CatalogExportPolicy.PUBLIC),
                new CatalogSnapshot(DESTINATION, REVISION, HANDOFF, 0, catalog.length, new Bytes(catalog)),
                new CatalogDelta(DESTINATION, new RemoteRevision(8), REVISION, catalog().dimensions(),
                        Map.of("removed", Set.of("gone", "")), Set.of("deleted")),
                new CatalogInvalidate(DESTINATION, REVISION, RemoteCatalogState.UNAVAILABLE),
                new PrepareHandoff(PLAYER, SOURCE, KEY, Action.TELEPORT, REVISION, new RemoteRevision(3)),
                new HandoffPrepared(BINDING),
                new HandoffRejected(Result.UNAUTHORIZED),
                new ClaimHandoff(HANDOFF, PLAYER, DESTINATION),
                new HandoffClaimed(BINDING),
                new CompleteHandoff(HANDOFF, PLAYER, DESTINATION, Result.SUCCESS),
                new CancelHandoff(HANDOFF, Result.CANCELLED),
                new ApplicationMessage.Error(Result.INVALID_REQUEST));
    }

    @ParameterizedTest
    @MethodSource("messages")
    void everyMessageRoundTripsDeterministically(ApplicationMessage message) {
        var envelope = envelope(message);
        byte[] bytes = CODEC.encode(envelope);
        assertEquals(envelope, CODEC.decode(bytes));
        assertArrayEquals(bytes, CODEC.encode(CODEC.decode(bytes)));
    }

    @ParameterizedTest
    @MethodSource("messages")
    void everyTruncationAndTrailingByteAreRejected(ApplicationMessage message) {
        byte[] bytes = CODEC.encode(envelope(message));
        for (int size = 0; size < bytes.length; size++) {
            byte[] truncated = Arrays.copyOf(bytes, size);
            assertThrows(IllegalArgumentException.class, () -> CODEC.decode(truncated), "length=" + size);
        }
        assertThrows(IllegalArgumentException.class, () -> CODEC.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        byte[] trailingPayload = Arrays.copyOf(bytes, bytes.length + 1);
        ByteBuffer.wrap(trailingPayload).putInt(32, bytes.length + 1 - ApplicationCodec.HEADER_BYTES);
        assertThrows(IllegalArgumentException.class, () -> CODEC.decode(trailingPayload));
    }

    @Test
    void heartbeatHasStableKnownBytesAndMessageIds() {
        assertEquals("000000010000000300000000000000070000000000000001000000000000000200000000",
                HexFormat.of().formatHex(CODEC.encode(envelope(new Heartbeat()))));
        assertEquals(List.of(1, 2, 3, 10, 11, 12, 13, 20, 21, 22, 23, 24, 25, 26, 30),
                messages().map(ApplicationCodec::typeId).toList());
        for (Result result : Result.values()) {
            byte[] bytes = CODEC.encode(envelope(new RegisterResult(SOURCE, result)));
            assertEquals(envelope(new RegisterResult(SOURCE, result)), CODEC.decode(bytes));
        }
    }

    @Test
    void rejectsUnknownVersionTypeAndEnvelopeValues() {
        byte[] heartbeat = CODEC.encode(envelope(new Heartbeat()));
        for (int offset : new int[]{0, 4, 32}) {
            byte[] bad = heartbeat.clone();
            ByteBuffer.wrap(bad).putInt(offset, 999);
            assertThrows(IllegalArgumentException.class, () -> CODEC.decode(bad));
        }
        byte[] negativeSequence = heartbeat.clone();
        ByteBuffer.wrap(negativeSequence).putLong(8, -1);
        assertThrows(IllegalArgumentException.class, () -> CODEC.decode(negativeSequence));
        byte[] nilRequest = heartbeat.clone();
        Arrays.fill(nilRequest, 16, 32, (byte) 0);
        assertThrows(IllegalArgumentException.class, () -> CODEC.decode(nilRequest));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationEnvelope(-1, REQUEST, new Heartbeat()));
        assertEquals(Long.MAX_VALUE, CODEC.decode(CODEC.encode(
                new ApplicationEnvelope(Long.MAX_VALUE, REQUEST, new Heartbeat()))).sequence());
    }

    @Test
    void catalogPreservesExactValuesAndReceiverTime() {
        var original = catalog();
        byte[] encoded = CODEC.encodeCatalog(original);
        var decoded = CODEC.decodeCatalog(encoded, Instant.MAX);
        assertEquals(original.dimensions(), decoded.dimensions());
        assertEquals(original.serverId(), decoded.serverId());
        assertEquals(original.catalogRevision(), decoded.catalogRevision());
        assertEquals(Instant.MAX, decoded.receivedAt());
        assertArrayEquals(encoded, CODEC.encodeCatalog(decoded));
        assertTrue(decoded.find(KEY).isPresent());
        assertTrue(decoded.find(new RemoteWaypointKey(DESTINATION, "世界", " List ", "Cafe\u0301")).isPresent());
        for (int size = 0; size < encoded.length; size++) {
            byte[] truncated = Arrays.copyOf(encoded, size);
            assertThrows(IllegalArgumentException.class, () -> CODEC.decodeCatalog(truncated, Instant.EPOCH));
        }
        assertThrows(IllegalArgumentException.class,
                () -> CODEC.decodeCatalog(Arrays.copyOf(encoded, encoded.length + 1), Instant.EPOCH));
    }

    @Test
    void mapAndSetInsertionOrderDoesNotChangeBytes() {
        var first = new LinkedHashMap<String, Map<String, RemoteListSnapshot>>();
        first.put("z", Map.of());
        first.put("a", catalog().dimensions().get("世界"));
        var second = new LinkedHashMap<String, Map<String, RemoteListSnapshot>>();
        second.put("a", catalog().dimensions().get("世界"));
        second.put("z", Map.of());
        assertArrayEquals(CODEC.encodeCatalog(new RemoteCatalogSnapshot(SOURCE, REVISION, first, Instant.EPOCH)),
                CODEC.encodeCatalog(new RemoteCatalogSnapshot(SOURCE, REVISION, second, Instant.EPOCH)));
        assertArrayEquals(CODEC.encode(envelope(new RegisterServer(SOURCE, 1, new LinkedHashSet<>(List.of(42, 1))))),
                CODEC.encode(envelope(new RegisterServer(SOURCE, 1, new LinkedHashSet<>(List.of(1, 42))))));
    }

    @Test
    void rejectsDuplicateAndOutOfOrderMapKeysAndCapabilities() {
        byte[] catalog = CODEC.encodeCatalog(new RemoteCatalogSnapshot(SOURCE, REVISION,
                Map.of("a", Map.of(), "b", Map.of()), Instant.EPOCH));
        // version(4), server string(5), revision(8), dimension count(4), first entry(9).
        for (byte replacement : new byte[]{'a', '0'}) {
            byte[] bad = catalog.clone();
            bad[34] = replacement;
            assertThrows(IllegalArgumentException.class, () -> CODEC.decodeCatalog(bad, Instant.EPOCH));
        }
        byte[] register = CODEC.encode(envelope(new RegisterServer(SOURCE, 1, Set.of(1, 42))));
        for (int replacement : new int[]{0, 1, -1}) {
            byte[] bad = register.clone();
            ByteBuffer.wrap(bad).putInt(bad.length - 4, replacement);
            assertThrows(IllegalArgumentException.class, () -> CODEC.decode(bad));
        }
    }

    @Test
    void rejectsMalformedUtf8AndUnpairedSurrogatesWithoutNormalization() {
        byte[] register = CODEC.encode(envelope(new RegisterServer(SOURCE, 1, Set.of())));
        register[40] = (byte) 0xFF;
        assertThrows(IllegalArgumentException.class, () -> CODEC.decode(register));
        for (String malformed : List.of("\uD800", "\uDC00", "x\uD800x")) {
            assertThrows(IllegalArgumentException.class, () -> CODEC.encode(envelope(
                    new CatalogMetadata(SOURCE, malformed, REVISION, CatalogExportPolicy.PUBLIC))));
        }
        var supplementary = envelope(new CatalogMetadata(SOURCE, "😀", REVISION, CatalogExportPolicy.PUBLIC));
        assertEquals(supplementary, CODEC.decode(CODEC.encode(supplementary)));
    }

    @Test
    void rejectsInvalidStringCollectionAndScalarDeclarations() {
        byte[] register = CODEC.encode(envelope(new RegisterServer(SOURCE, 1, Set.of())));
        for (int size : new int[]{-1, Integer.MAX_VALUE, 65_537}) {
            byte[] bad = register.clone();
            ByteBuffer.wrap(bad).putInt(36, size);
            assertThrows(IllegalArgumentException.class, () -> CODEC.decode(bad));
        }
        for (int size : new int[]{-1, Integer.MAX_VALUE, 16_385}) {
            byte[] bad = register.clone();
            ByteBuffer.wrap(bad).putInt(bad.length - 4, size);
            assertThrows(IllegalArgumentException.class, () -> CODEC.decode(bad));
        }
        byte[] result = CODEC.encode(envelope(new RegisterResult(SOURCE, Result.SUCCESS)));
        ByteBuffer.wrap(result).putInt(result.length - 4, 999);
        assertThrows(IllegalArgumentException.class, () -> CODEC.decode(result));
        byte[] metadata = CODEC.encode(envelope(new CatalogMetadata(SOURCE, "", REVISION, CatalogExportPolicy.PUBLIC)));
        ByteBuffer.wrap(metadata).putInt(metadata.length - 4, 2);
        assertThrows(IllegalArgumentException.class, () -> CODEC.decode(metadata));
        byte[] prepare = CODEC.encode(envelope(new PrepareHandoff(PLAYER, SOURCE, KEY, Action.TELEPORT, REVISION, REVISION)));
        ByteBuffer.wrap(prepare).putInt(prepare.length - 20, 2);
        assertThrows(IllegalArgumentException.class, () -> CODEC.decode(prepare));
    }

    @Test
    void rejectsMalformedWaypointRatherThanClampingValues() {
        var waypoint = new RemoteWaypointSnapshot("", "", new WaypointPos(0, 0, 0), 0, 0, false, List.of(), "");
        byte[] bytes = CODEC.encodeCatalog(new RemoteCatalogSnapshot(SOURCE, REVISION,
                Map.of("d", Map.of("l", new RemoteListSnapshot("", REVISION, Map.of("w", waypoint)))), Instant.EPOCH));
        // Empty description and keyword count occupy the final 8 bytes; global precedes them.
        byte[] global = bytes.clone();
        global[global.length - 9] = 2;
        assertThrows(IllegalArgumentException.class, () -> CODEC.decodeCatalog(global, Instant.EPOCH));
        for (int yaw : new int[]{-181, 181, Integer.MAX_VALUE}) {
            byte[] bad = bytes.clone();
            ByteBuffer.wrap(bad).putInt(bad.length - 13, yaw);
            assertThrows(IllegalArgumentException.class, () -> CODEC.decodeCatalog(bad, Instant.EPOCH));
        }
        byte[] rgb = bytes.clone();
        ByteBuffer.wrap(rgb).putInt(rgb.length - 17, 0x1000000);
        assertThrows(IllegalArgumentException.class, () -> CODEC.decodeCatalog(rgb, Instant.EPOCH));
    }

    @Test
    void independentBudgetsRejectBeforeLargeAllocations() {
        var frameLimited = codec(36, 1_048_576, 262_144, 65_536, 16_384, 65_536, 8_388_608);
        assertEquals(envelope(new Heartbeat()), frameLimited.decode(frameLimited.encode(envelope(new Heartbeat()))));
        assertThrows(IllegalArgumentException.class, () -> frameLimited.encode(envelope(new RegisterResult(SOURCE, Result.SUCCESS))));
        assertThrows(IllegalArgumentException.class, () -> frameLimited.decode(new byte[37]));
        assertThrows(IllegalArgumentException.class, () -> CODEC.decode(new byte[1_048_577]));
        var stringLimited = codec(1_048_576, 1_048_576, 262_144, 1, 16_384, 65_536, 8_388_608);
        var metadata = envelope(new CatalogMetadata(SOURCE, "é", REVISION, CatalogExportPolicy.PUBLIC));
        assertThrows(IllegalArgumentException.class, () -> stringLimited.encode(metadata));
        assertThrows(IllegalArgumentException.class, () -> stringLimited.decode(CODEC.encode(metadata)));
        var objectLimited = codec(1_048_576, 1_048_576, 262_144, 65_536, 16_384, 2, 8_388_608);
        assertThrows(IllegalArgumentException.class, () -> objectLimited.decode(CODEC.encode(envelope(new Heartbeat()))));
        assertThrows(IllegalArgumentException.class, () -> objectLimited.encode(envelope(new Heartbeat())));
        var allocationLimited = codec(1_048_576, 1_048_576, 262_144, 65_536, 16_384, 65_536, 1);
        assertThrows(IllegalArgumentException.class, () -> allocationLimited.decode(CODEC.encode(metadata)));
        assertThrows(IllegalArgumentException.class, () -> allocationLimited.encode(metadata));
        var collectionLimited = codec(1_048_576, 1_048_576, 262_144, 65_536, 1, 65_536, 8_388_608);
        var register = envelope(new RegisterServer(SOURCE, 1, Set.of(1, 2)));
        assertThrows(IllegalArgumentException.class, () -> collectionLimited.encode(register));
        assertThrows(IllegalArgumentException.class, () -> collectionLimited.decode(CODEC.encode(register)));
        var catalogLimited = codec(1_048_576, 1, 262_144, 65_536, 16_384, 65_536, 8_388_608);
        assertThrows(IllegalArgumentException.class, () -> catalogLimited.encodeCatalog(catalog()));
        assertThrows(IllegalArgumentException.class, () -> catalogLimited.decodeCatalog(CODEC.encodeCatalog(catalog()), Instant.EPOCH));
    }

    @Test
    void chunkBoundariesAndBinaryOwnershipAreEnforced() {
        byte[] data = new byte[262_144];
        data[0] = 7;
        var chunk = new CatalogSnapshot(SOURCE, REVISION, HANDOFF, 0, data.length, new Bytes(data));
        data[0] = 8;
        assertEquals(7, chunk.data().copy()[0]);
        byte[] escaped = chunk.data().copy();
        escaped[0] = 9;
        assertEquals(7, chunk.data().copy()[0]);
        assertEquals(envelope(chunk), CODEC.decode(CODEC.encode(envelope(chunk))));
        var tooLarge = new CatalogSnapshot(SOURCE, REVISION, HANDOFF, 0, 262_145, new Bytes(new byte[262_145]));
        assertThrows(IllegalArgumentException.class, () -> CODEC.encode(envelope(tooLarge)));
        var chunkLimited = codec(1_048_576, 1_048_576, 1, 65_536, 16_384, 65_536, 8_388_608);
        assertThrows(IllegalArgumentException.class, () -> chunkLimited.decode(CODEC.encode(envelope(chunk))));
        assertThrows(IllegalArgumentException.class, () -> new CatalogSnapshot(SOURCE, REVISION, HANDOFF,
                Integer.MAX_VALUE, Integer.MAX_VALUE, new Bytes(new byte[1])));
        assertThrows(IllegalArgumentException.class, () -> new CatalogSnapshot(SOURCE, REVISION, HANDOFF, 0, 1, new Bytes(new byte[0])));
        var excessTotal = new CatalogSnapshot(SOURCE, REVISION, HANDOFF, 0, 1_048_577, new Bytes(new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> CODEC.encode(envelope(excessTotal)));
    }

    @Test
    void domainValuesRejectConflictsAndImpossibleHandoffs() {
        assertThrows(IllegalArgumentException.class, () -> new CatalogDelta(SOURCE, REVISION, REVISION, Map.of(), Map.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new CatalogDelta(SOURCE, new RemoteRevision(0), REVISION,
                Map.of("d", Map.of()), Map.of(), Set.of("d")));
        assertThrows(IllegalArgumentException.class, () -> new CatalogDelta(SOURCE, new RemoteRevision(0), REVISION,
                Map.of("d", Map.of("l", new RemoteListSnapshot("", REVISION, Map.of()))), Map.of("d", Set.of("l")), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new PrepareHandoff(PLAYER, DESTINATION, KEY, Action.TELEPORT, REVISION, REVISION));
        assertThrows(IllegalArgumentException.class, () -> new HandoffBinding(HANDOFF, PLAYER, SOURCE, KEY, Action.TELEPORT, 0));
        assertThrows(IllegalArgumentException.class, () -> new HandoffRejected(Result.SUCCESS));
        assertThrows(IllegalArgumentException.class, () -> new CatalogInvalidate(SOURCE, REVISION, RemoteCatalogState.AVAILABLE));
        assertThrows(IllegalArgumentException.class, () -> new RegisterServer(SOURCE, 2, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProtocolLimits(1_048_577, 1, 1, 1, 1, 1, 1));
    }

    @Test
    void seededMutationsEitherRejectOrRetainCanonicalBytes() {
        Random random = new Random(959);
        List<byte[]> vectors = messages().map(message -> CODEC.encode(envelope(message))).toList();
        for (int trial = 0; trial < 2_000; trial++) {
            byte[] bytes = vectors.get(random.nextInt(vectors.size())).clone();
            bytes[random.nextInt(bytes.length)] ^= (byte) (1 + random.nextInt(255));
            ApplicationEnvelope decoded;
            try {
                decoded = CODEC.decode(bytes);
            } catch (IllegalArgumentException expected) {
                continue;
            }
            assertArrayEquals(bytes, CODEC.encode(decoded));
        }
    }

    @Test
    void malformedUtf8InUnrestrictedDisplayStringsIsRejected() {
        for (byte[] malformed : List.of(new byte[]{(byte) 0xFF}, new byte[]{(byte) 0xC0, (byte) 0xAF},
                new byte[]{(byte) 0xED, (byte) 0xA0, (byte) 0x80},
                new byte[]{(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80}, new byte[]{(byte) 0xC2})) {
            byte[] bytes = CODEC.encode(envelope(new CatalogMetadata(SOURCE, "x".repeat(malformed.length),
                    REVISION, CatalogExportPolicy.PUBLIC)));
            System.arraycopy(malformed, 0, bytes, 45, malformed.length);
            assertThrows(IllegalArgumentException.class, () -> CODEC.decode(bytes));
        }
    }

    @Test
    void utf8ByteLimitHasExactBoundaryAndRevisionRejectsNegativeWireValue() {
        var maximum = envelope(new CatalogMetadata(SOURCE, "é".repeat(32_768), REVISION, CatalogExportPolicy.PUBLIC));
        assertEquals(maximum, CODEC.decode(CODEC.encode(maximum)));
        assertThrows(IllegalArgumentException.class, () -> CODEC.encode(envelope(new CatalogMetadata(
                SOURCE, "é".repeat(32_769), REVISION, CatalogExportPolicy.PUBLIC))));
        byte[] empty = CODEC.encodeCatalog(new RemoteCatalogSnapshot(SOURCE, REVISION, Map.of(), Instant.EPOCH));
        assertEquals("000000010000000161000000000000000900000000", HexFormat.of().formatHex(empty));
        ByteBuffer.wrap(empty).putLong(9, -1);
        assertThrows(IllegalArgumentException.class, () -> CODEC.decodeCatalog(empty, Instant.EPOCH));
    }

    @Test
    void deltasAndCapabilitiesOwnAllMutableInputCollections() {
        var capabilities = new HashSet<>(Set.of(1));
        var register = new RegisterServer(SOURCE, 1, capabilities);
        capabilities.clear();
        assertEquals(Set.of(1), register.capabilities());
        var names = new HashSet<>(Set.of("gone"));
        var removals = new HashMap<String, Set<String>>();
        removals.put("d", names);
        var lists = new HashMap<String, RemoteListSnapshot>();
        lists.put("l", new RemoteListSnapshot("", REVISION, Map.of()));
        var replacements = new HashMap<String, Map<String, RemoteListSnapshot>>();
        replacements.put("new", lists);
        var dimensions = new HashSet<>(Set.of("deleted"));
        var delta = new CatalogDelta(SOURCE, new RemoteRevision(8), REVISION, replacements, removals, dimensions);
        byte[] before = CODEC.encode(envelope(delta));
        names.clear(); removals.clear(); lists.clear(); replacements.clear(); dimensions.clear();
        assertArrayEquals(before, CODEC.encode(envelope(delta)));
        assertThrows(UnsupportedOperationException.class, () -> delta.replacements().clear());
        assertThrows(UnsupportedOperationException.class, () -> delta.replacements().get("new").clear());
        assertThrows(UnsupportedOperationException.class, () -> delta.removedLists().get("d").clear());
        assertThrows(UnsupportedOperationException.class, () -> delta.removedDimensions().clear());
    }

    @ParameterizedTest
    @MethodSource("messages")
    void encoderAndDecoderAgreeOnObjectBudget(ApplicationMessage message) {
        byte[] canonical = CODEC.encode(envelope(message));
        for (int objects = 1; objects <= 200; objects++) {
            var bounded = codec(1_048_576, 1_048_576, 262_144, 65_536, 16_384, objects, 8_388_608);
            boolean encoded;
            try {
                bounded.encode(envelope(message));
                encoded = true;
            } catch (IllegalArgumentException expected) {
                encoded = false;
            }
            boolean decoded;
            try {
                bounded.decode(canonical);
                decoded = true;
            } catch (IllegalArgumentException expected) {
                decoded = false;
            }
            assertEquals(encoded, decoded, "object budget=" + objects);
        }
    }

    private static ApplicationEnvelope envelope(ApplicationMessage message) {
        return new ApplicationEnvelope(7, REQUEST, message);
    }

    private static ApplicationCodec codec(int frame, int catalog, int chunk, int string, int collection, int objects, int allocation) {
        return new ApplicationCodec(new ProtocolLimits(frame, catalog, chunk, string, collection, objects, allocation));
    }

    private static RemoteCatalogSnapshot catalog() {
        var waypoint = new RemoteWaypointSnapshot("Visible label", "😀", new WaypointPos(Integer.MIN_VALUE, 64, Integer.MAX_VALUE),
                0xABCDEF, -180, true, List.of(" One ", "世界", " One "), "Description");
        var list = new RemoteListSnapshot("List label", new RemoteRevision(3), Map.of("Café", waypoint, "Cafe\u0301", waypoint));
        return new RemoteCatalogSnapshot(DESTINATION, REVISION, Map.of("世界", Map.of(" List ", list), "", Map.of()), Instant.EPOCH);
    }
}
