package _959.server_waypoint.perf;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProbeContractTest {
    @Test void rejectsMalformedAndUnknownSchema() {
        assertThrows(IllegalArgumentException.class, () -> ProbeCommand.parse(new String[]{"bad"}));
        String run = UUID.randomUUID().toString();
        String encoded = Base64.getUrlEncoder().encodeToString("{\"schemaVersion\":2}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> ProbeCommand.parse(new String[]{run, run, "hello", encoded}));
    }

    @Test void retainsRunAttemptAndOperation() {
        String run = UUID.randomUUID().toString();
        String encoded = Base64.getUrlEncoder().encodeToString("{\"schemaVersion\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ProbeCommand command = ProbeCommand.parse(new String[]{run, run, "hello", encoded});
        assertEquals(run, command.runId());
        assertEquals("hello", command.operation());
    }
}
