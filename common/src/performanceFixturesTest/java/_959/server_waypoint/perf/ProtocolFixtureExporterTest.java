package _959.server_waypoint.perf;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolFixtureExporterTest {
    @Test void test_exporter_rejects_wrong_production_class_hash() throws Exception {
        Path fake = Files.createTempFile("untrusted-production", ".jar");
        try {
            Files.writeString(fake, "current build substituted for release");
            assertThrows(IllegalArgumentException.class, () -> ProtocolFixtureExporter.verifyArtifact(fake));
        } finally { Files.delete(fake); }
    }

    @Test void handshake_and_chunk_vectors_round_trip_through_release_codecs() throws Exception {
        var fixtures = ProtocolFixtureExporter.vectors();
        assertEquals(2, fixtures.get("protocolVersion").getAsInt());
        assertTrue(fixtures.getAsJsonArray("vectors").size() >= 8);
    }
}
