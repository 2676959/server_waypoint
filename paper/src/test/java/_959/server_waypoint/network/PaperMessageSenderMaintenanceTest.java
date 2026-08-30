package _959.server_waypoint.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperMessageSenderMaintenanceTest {
    @Test
    void maintenanceInvokesExactlyOneManagerWideTick() {
        AtomicInteger managerTicks = new AtomicInteger();
        List<String> warnings = new ArrayList<>();

        PaperMessageSender.runChunkedMessageMaintenance(
                managerTicks::incrementAndGet,
                warnings::add
        );

        assertEquals(1, managerTicks.get());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void maintenanceContainsManagerTickFailure() {
        List<String> warnings = new ArrayList<>();

        PaperMessageSender.runChunkedMessageMaintenance(
                () -> {
                    throw new IllegalStateException("failed");
                },
                warnings::add
        );

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("failed"));
    }
}
