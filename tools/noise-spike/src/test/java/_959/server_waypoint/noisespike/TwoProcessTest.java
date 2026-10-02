package _959.server_waypoint.noisespike;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class TwoProcessTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"success", "wrong-psk", "wrong-pin", "wrong-prologue", "tamper-request",
            "tamper-response"})
    void unshadedPeers(String scenario) throws Exception {
        runPeers(scenario, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "wrong-psk", "wrong-pin", "wrong-prologue", "tamper-request",
            "tamper-response"})
    void relocatedPeersWithoutOriginalClasses(String scenario) throws Exception {
        runPeers(scenario, true);
    }

    @Test
    void shadedJarIsSelfContainedAndRelocated() throws Exception {
        try (JarFile jar = new JarFile(System.getProperty("spike.relocatedJar"))) {
            assertNotNull(jar.getEntry("_959/server_waypoint/internal/noise/NoiseHandshake.class"));
            assertNotNull(jar.getEntry("META-INF/LICENSE-java-noise"));
            assertNotNull(jar.getEntry("META-INF/LICENSE-jsr305"));
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
        var generator = KeyPairGenerator.getInstance("X25519");
        var serverKeys = generator.generateKeyPair();
        byte[] psk = new byte[32];
        new SecureRandom().nextBytes(psk);
        Path serverCredentials = credentials("server", serverKeys.getPublic().getEncoded(),
                serverKeys.getPrivate().getEncoded(), psk);
        byte[] clientPsk = psk.clone();
        if (scenario.equals("wrong-psk")) {
            clientPsk[0] ^= 1;
        }
        byte[] pin = scenario.equals("wrong-pin")
                ? generator.generateKeyPair().getPublic().getEncoded() : serverKeys.getPublic().getEncoded();
        Path clientCredentials = credentials("client", pin, new byte[0], clientPsk);
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

    private Path credentials(String role, byte[] publicKey, byte[] privateKey, byte[] psk) throws Exception {
        Path path = temporary.resolve(role + ".fixture");
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(path))) {
            NoisePeer.writeFrame(output, publicKey);
            NoisePeer.writeFrame(output, privateKey);
            NoisePeer.writeFrame(output, psk);
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
            command.addAll(List.of("-cp", System.getProperty("spike.classpath"), NoisePeer.class.getName()));
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
