package _959.server_waypoint;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every Paper build starts on the Minecraft version it is built against. */
class CompatibilityCheckerTest {
    @Test
    void theBuildAcceptsTheVersionInItsPluginDescriptor() throws IOException {
        String apiVersion;
        try (InputStream descriptor = Objects.requireNonNull(
                CompatibilityCheckerTest.class.getResourceAsStream("/paper-plugin.yml"))) {
            apiVersion = new String(descriptor.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> line.startsWith("api-version:"))
                    .map(line -> line.substring("api-version:".length()).trim().replace("'", ""))
                    .findFirst()
                    .orElseThrow();
        }

        assertTrue(CompatibilityChecker.isCompatible(apiVersion),
                apiVersion + " is outside " + CompatibilityChecker.getSupportedVersions());
    }
}
