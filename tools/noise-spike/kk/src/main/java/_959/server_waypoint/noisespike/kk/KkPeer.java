package _959.server_waypoint.noisespike.kk;

import javax.crypto.BadPaddingException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/** Loopback-only test peer. Registry, preface and confirmation bytes are fixtures, not protocol v1. */
public final class KkPeer {
    private static final int TIMEOUT_MILLIS = 5_000;
    private static final byte[] CONFIRM = ascii("backend-confirmed");
    private static final byte[] ACK = ascii("coordinator-confirmed");
    private static final Set<String> SCENARIOS = Set.of("success", "wrong-pin", "wrong-backend-key",
            "unknown-id", "revoked-id", "wrong-prologue", "tamper-request", "tamper-response",
            "tamper-confirmation", "operation-before-confirmation", "missing-confirmation",
            "tamper-transport", "replay-transport");

    private KkPeer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !Set.of("server", "client").contains(args[0])
                || !SCENARIOS.contains(args[3])) {
            throw new IllegalArgumentException("Expected role, fixture, port file, scenario");
        }
        boolean server = args[0].equals("server");
        String scenario = args[3];
        int sessions = scenario.equals("success") ? 3 : 1;
        Set<String> hashes = new HashSet<>();
        if (server) {
            try (ServerSocket listener = new ServerSocket()) {
                listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                listener.setSoTimeout(TIMEOUT_MILLIS);
                Path portFile = Path.of(args[2]);
                Path ready = portFile.resolveSibling("port.ready");
                Files.writeString(ready, Integer.toString(listener.getLocalPort()));
                Files.move(ready, portFile, StandardCopyOption.ATOMIC_MOVE);
                for (int i = 0; i < sessions; i++) {
                    try (Socket socket = listener.accept()) {
                        exchange(socket, true, Path.of(args[1]), scenario, i, hashes);
                    }
                }
            }
        } else {
            int port = Integer.parseInt(Files.readString(Path.of(args[2])));
            for (int i = 0; i < sessions; i++) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), TIMEOUT_MILLIS);
                    exchange(socket, false, Path.of(args[1]), scenario, i, hashes);
                }
            }
        }
        System.out.println(args[0] + " " + scenario + " PASS sessions=" + sessions);
    }

    private static KkSession handshake(boolean server, Path fixture, byte[] prologue) throws Exception {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(fixture))) {
            byte[] localPrivate = readFrame(input);
            try {
                byte[] remotePublic = readFrame(input);
                if (localPrivate.length != 32 || remotePublic.length != 32 || input.read() != -1) {
                    throw new IOException("Invalid fixture");
                }
                return new KkSession(!server, localPrivate, remotePublic, prologue);
            } finally {
                Arrays.fill(localPrivate, (byte) 0);
            }
        }
    }

    private static void exchange(Socket socket, boolean server, Path fixture, String scenario,
                                 int session, Set<String> hashes) throws Exception {
        socket.setSoTimeout(TIMEOUT_MILLIS);
        var input = new DataInputStream(socket.getInputStream());
        var output = new DataOutputStream(socket.getOutputStream());
        String stage = "registry";
        try {
            byte[] id;
            byte[] nonce = new byte[32];
            if (server) {
                id = readFrame(input);
                // One-key lookup fixture: no trial decryption and no admission of the untrusted ID.
                if (!Arrays.equals(id, ascii("backend")) || scenario.equals("revoked-id")) {
                    throw new KkSession.Rejected(scenario.equals("revoked-id") ? "revoked-id" : "unknown-id");
                }
                new SecureRandom().nextBytes(nonce);
                writeFrame(output, nonce);
            } else {
                id = ascii(scenario.equals("unknown-id") ? "unknown" : "backend");
                writeFrame(output, id);
                nonce = readFrame(input);
                if (nonce.length != 32) {
                    throw new IOException("Invalid session nonce");
                }
            }
            byte[] prologue = ascii("step2|NOISE_KK|v1|" + KkSession.PROTOCOL + "|"
                    + HexFormat.of().formatHex(id) + "|" + HexFormat.of().formatHex(nonce));
            if (!server && scenario.equals("wrong-prologue")) {
                prologue[0] ^= 1;
            }
            stage = "handshake";
            try (KkSession noise = handshake(server, fixture, prologue)) {
                if (server) {
                    noise.readHandshake(readFrame(input));
                    byte[] response = noise.writeHandshake();
                    if (scenario.equals("tamper-response")) {
                        response[response.length - 1] ^= 1;
                    }
                    writeFrame(output, response);
                } else {
                    byte[] request = noise.writeHandshake();
                    if (scenario.equals("tamper-request")) {
                        request[request.length - 1] ^= 1;
                    }
                    writeFrame(output, request);
                    noise.readHandshake(readFrame(input));
                }
                if (!hashes.add(HexFormat.of().formatHex(noise.split()))) {
                    throw new IllegalStateException("Reconnect reused handshake state");
                }
                stage = "confirmation";
                if (server) {
                    // No catalog/operation response is emitted until the first transport message authenticates.
                    if (!Arrays.equals(CONFIRM, noise.decrypt(readFrame(input)))) {
                        throw new KkSession.Rejected("operation-before-confirmation");
                    }
                    writeFrame(output, noise.encrypt(ACK));
                } else {
                    if (scenario.equals("missing-confirmation")) {
                        socket.shutdownOutput();
                        requireEof(input); // Any server output before confirmation fails the test.
                        return;
                    }
                    byte[] confirmation = noise.encrypt(scenario.equals("operation-before-confirmation")
                            ? ascii("catalog-operation") : CONFIRM);
                    if (scenario.equals("tamper-confirmation")) {
                        confirmation[confirmation.length - 1] ^= 1;
                    }
                    writeFrame(output, confirmation);
                    requireEqual(ACK, noise.decrypt(readFrame(input)));
                }
                stage = "transport";
                for (int size : new int[]{0, 31, 65_519}) {
                    byte[] request = payload(size, session + 1);
                    byte[] response = payload(size, session + 17);
                    if (server) {
                        requireEqual(request, noise.decrypt(readFrame(input)));
                        writeFrame(output, noise.encrypt(response));
                        if (scenario.equals("replay-transport")) {
                            noise.decrypt(readFrame(input));
                            throw new IllegalStateException("Replay accepted");
                        }
                    } else {
                        byte[] encrypted = noise.encrypt(request);
                        if (encrypted.length != size + 16 || Arrays.equals(request, encrypted)) {
                            throw new IllegalStateException("Transport did not encrypt");
                        }
                        if (scenario.equals("tamper-transport")) {
                            encrypted[encrypted.length - 1] ^= 1;
                        }
                        writeFrame(output, encrypted);
                        requireEqual(response, noise.decrypt(readFrame(input)));
                        if (scenario.equals("replay-transport")) {
                            writeFrame(output, encrypted);
                            readFrame(input);
                            throw new IllegalStateException("Replay admitted traffic");
                        }
                    }
                }
                if (!scenario.equals("success")) {
                    throw new IllegalStateException("Negative scenario admitted traffic");
                }
                socket.shutdownOutput();
                requireEof(input);
            }
        } catch (BadPaddingException rejected) {
            String expectedStage = switch (scenario) {
                case "wrong-pin", "wrong-backend-key", "wrong-prologue", "tamper-request", "tamper-response" -> "handshake";
                case "tamper-confirmation" -> "confirmation";
                case "tamper-transport", "replay-transport" -> "transport";
                default -> "never";
            };
            boolean expectedServer = !scenario.equals("tamper-response");
            if (server != expectedServer || !stage.equals(expectedStage)) {
                throw rejected;
            }
        } catch (KkSession.Rejected rejected) {
            if (!server || !rejected.getMessage().equals(scenario)) {
                throw rejected;
            }
        } catch (EOFException rejected) {
            boolean expected = server
                    ? stage.equals("confirmation") && Set.of("tamper-response", "missing-confirmation").contains(scenario)
                    : switch (stage) {
                        case "registry" -> Set.of("unknown-id", "revoked-id").contains(scenario);
                        case "handshake" -> Set.of("wrong-pin", "wrong-backend-key", "wrong-prologue", "tamper-request").contains(scenario);
                        case "confirmation" -> Set.of("tamper-confirmation", "operation-before-confirmation").contains(scenario);
                        case "transport" -> Set.of("tamper-transport", "replay-transport").contains(scenario);
                        default -> false;
                    };
            if (!expected) {
                throw rejected;
            }
        }
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] payload(int size, int seed) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 31 + seed);
        }
        return bytes;
    }

    private static void requireEqual(byte[] expected, byte[] actual) {
        if (!Arrays.equals(expected, actual)) {
            throw new IllegalStateException("Payload mismatch");
        }
    }

    private static void requireEof(DataInputStream input) throws IOException {
        if (input.read() != -1) {
            throw new IOException("Unexpected bytes before peer shutdown");
        }
    }

    public static byte[] readFrame(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > 65_535) {
            throw new IOException("Invalid probe frame length");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return bytes;
    }

    public static void writeFrame(DataOutputStream output, byte[] bytes) throws IOException {
        if (bytes.length > 65_535) {
            throw new IOException("Oversized probe frame");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();
    }
}
