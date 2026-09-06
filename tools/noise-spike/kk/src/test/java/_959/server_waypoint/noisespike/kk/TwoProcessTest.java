package _959.server_waypoint.noisespike.kk;

import com.southernstorm.noise.protocol.Noise;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class TwoProcessTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"success", "wrong-pin", "wrong-backend-key", "unknown-id", "revoked-id",
            "wrong-prologue", "tamper-request", "tamper-response", "tamper-confirmation",
            "operation-before-confirmation", "missing-confirmation", "tamper-transport", "replay-transport"})
    void unshadedPeers(String scenario) throws Exception {
        runPeers(scenario, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "wrong-pin", "wrong-backend-key", "unknown-id", "revoked-id",
            "wrong-prologue", "tamper-request", "tamper-response", "tamper-confirmation",
            "operation-before-confirmation", "missing-confirmation", "tamper-transport", "replay-transport"})
    void relocatedPeersWithoutOriginalClasses(String scenario) throws Exception {
        runPeers(scenario, true);
    }

    @Test
    void shadedJarIsSelfContainedAndRelocated() throws Exception {
        try (JarFile jar = new JarFile(System.getProperty("spike.relocatedJar"))) {
            assertNotNull(jar.getEntry("_959/server_waypoint/internal/noisekk/protocol/HandshakeState.class"));
            assertNotNull(jar.getEntry("META-INF/LICENSE-noise-java"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("com/eatthepath/noise/")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("javax/annotation/")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("com/southernstorm/")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("org/junit/")));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("com/google/gson/")));
            for (var entry : jar.stream().filter(entry -> entry.getName().endsWith(".class")).toList()) {
                try (var input = new java.io.DataInputStream(jar.getInputStream(entry))) {
                    assertEquals(0xcafebabe, input.readInt());
                    input.readUnsignedShort();
                    assertTrue(input.readUnsignedShort() <= 61, entry.getName() + " exceeds Java 17");
                }
            }
        }
    }

    private void runPeers(String scenario, boolean relocated) throws Exception {
        var serverKeys = Noise.createDH("25519");
        var clientKeys = Noise.createDH("25519");
        var wrongKeys = Noise.createDH("25519");
        byte[] serverPrivate = new byte[32];
        byte[] clientPrivate = new byte[32];
        Path serverCredentials;
        Path clientCredentials;
        try {
            serverKeys.generateKeyPair();
            clientKeys.generateKeyPair();
            wrongKeys.generateKeyPair();
            byte[] serverPublic = new byte[32];
            byte[] clientPublic = new byte[32];
            serverKeys.getPublicKey(serverPublic, 0);
            clientKeys.getPublicKey(clientPublic, 0);
            serverKeys.getPrivateKey(serverPrivate, 0);
            clientKeys.getPrivateKey(clientPrivate, 0);
            serverCredentials = credentials("server", serverPrivate, clientPublic);
            if (scenario.equals("wrong-pin")) {
                wrongKeys.getPublicKey(serverPublic, 0);
            }
            if (scenario.equals("wrong-backend-key")) {
                wrongKeys.getPrivateKey(clientPrivate, 0);
            }
            clientCredentials = credentials("client", clientPrivate, serverPublic);
        } finally {
            serverKeys.destroy();
            clientKeys.destroy();
            wrongKeys.destroy();
            java.util.Arrays.fill(serverPrivate, (byte) 0);
            java.util.Arrays.fill(clientPrivate, (byte) 0);
        }
        Path port = temporary.resolve("port");
        Path serverLog = temporary.resolve("server.log");
        Path clientLog = temporary.resolve("client.log");
        Process server = start("server", serverCredentials, port, scenario, relocated, serverLog);
        Process client = null;
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(port) || Files.size(port) == 0) {
                assertTrue(server.isAlive(), () -> readLog(serverLog));
                assertTrue(System.nanoTime() < deadline, "Server startup timed out");
                Thread.sleep(20);
            }
            client = start("client", clientCredentials, port, scenario, relocated, clientLog);
            assertNotEquals(server.pid(), client.pid());
            assertTrue(client.waitFor(20, TimeUnit.SECONDS), "Client failed to shut down");
            assertTrue(server.waitFor(20, TimeUnit.SECONDS), "Server failed to shut down");
            assertEquals(0, client.exitValue(), () -> readLog(clientLog));
            assertEquals(0, server.exitValue(), () -> readLog(serverLog));
            int sessions = scenario.equals("success") ? 3 : 1;
            assertEquals("server " + scenario + " PASS sessions=" + sessions, readLog(serverLog).strip());
            assertEquals("client " + scenario + " PASS sessions=" + sessions, readLog(clientLog).strip());
        } finally {
            stop(client);
            stop(server);
        }
    }

    private Path credentials(String role, byte[] privateKey, byte[] remotePublic) throws Exception {
        Path path = temporary.resolve(role + ".fixture");
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(path))) {
            KkPeer.writeFrame(output, privateKey);
            KkPeer.writeFrame(output, remotePublic);
        }
        return path;
    }

    private Process start(String role, Path credentials, Path port, String scenario, boolean relocated, Path log)
            throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        if (relocated) {
            command.addAll(List.of("-jar", System.getProperty("spike.relocatedJar")));
        } else {
            command.addAll(List.of("-cp", System.getProperty("spike.classpath"), KkPeer.class.getName()));
        }
        command.addAll(List.of(role, credentials.toString(), port.toString(), scenario));
        return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }

    private static void stop(Process process) throws InterruptedException {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Child process leaked");
        }
    }

    private static String readLog(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            return "Could not read child process log";
        }
    }
}
