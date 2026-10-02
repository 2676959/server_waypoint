package _959.server_waypoint.crossserver.transport;

import _959.server_waypoint.crossserver.RemoteServerId;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

final class TcpWire {
    static final int RECORD = 65_535;
    static final int CLEAR = RECORD - 16;
    static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "server-waypoint-tcp-deadlines");
        thread.setDaemon(true);
        return thread;
    });
    static { TIMER.setRemoveOnCancelPolicy(true); }
    private static final SecureRandom RANDOM = new SecureRandom();

    static ScheduledFuture<?> deadline(Socket socket, int millis) {
        return TIMER.schedule(() -> close(socket), millis, TimeUnit.MILLISECONDS);
    }

    static void close(Socket socket) {
        try { socket.close(); } catch (IOException ignored) { }
    }

    static byte[] read(Socket socket, int maximum) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        int length = in.readInt();
        if (length <= 0 || length > maximum) throw new IOException("Invalid frame length");
        byte[] result = new byte[length];
        in.readFully(result);
        return result;
    }

    static void write(Socket socket, byte[] bytes, int maximum) throws IOException {
        if (bytes.length == 0 || bytes.length > maximum) throw new IOException("Invalid frame length");
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.writeInt(bytes.length);
        out.write(bytes);
        out.flush();
    }

    record Hello(RemoteServerId serverId, Set<Integer> capabilities) { }

    static byte[] hello(TransportMode mode, RemoteServerId id, Set<Integer> capabilities, boolean response)
            throws IOException {
        if (capabilities.size() > 64 || capabilities.stream().anyMatch(c -> c == null || c <= 0)) {
            throw new IllegalArgumentException("Invalid capabilities");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x53575054); // SWPT
        out.writeInt(1); // transport version
        out.writeInt(mode.wireId);
        out.writeInt(1); // application version
        byte[] name = id.value().getBytes(StandardCharsets.US_ASCII);
        out.writeInt(name.length);
        out.write(name);
        out.writeInt(capabilities.size());
        for (int capability : new TreeSet<>(capabilities)) out.writeInt(capability);
        byte[] nonce = new byte[32];
        RANDOM.nextBytes(nonce);
        out.write(nonce);
        out.writeInt(mode == TransportMode.NOISE_KK ? 1 : 0);
        if (mode == TransportMode.NOISE_KK) out.writeInt(1); // only implemented suite
        out.writeInt(response && mode == TransportMode.NOISE_KK ? 1 : 0);
        return bytes.toByteArray();
    }

    static Hello parse(byte[] bytes, TransportMode mode, boolean response) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 0x53575054 || in.readInt() != 1 || in.readInt() != mode.wireId
                || in.readInt() != 1) throw new IOException("Transport mode or version mismatch");
        int length = in.readInt();
        if (length < 1 || length > 64) throw new IOException("Invalid identity");
        byte[] name = new byte[length];
        in.readFully(name);
        for (byte b : name) if (b < 0) throw new IOException("Invalid identity");
        RemoteServerId id = new RemoteServerId(new String(name, StandardCharsets.US_ASCII));
        int count = in.readInt();
        if (count < 0 || count > 64) throw new IOException("Invalid capabilities");
        Set<Integer> capabilities = new TreeSet<>();
        int previous = 0;
        for (int n = 0; n < count; n++) {
            int value = in.readInt();
            if (value <= previous) throw new IOException("Invalid capabilities");
            capabilities.add(value);
            previous = value;
        }
        byte[] nonce = new byte[32];
        in.readFully(nonce);
        int suites = in.readInt();
        if (suites < 0 || suites > 16) throw new IOException("Invalid suite list");
        boolean kk = false;
        previous = 0;
        for (int n = 0; n < suites; n++) {
            int suite = in.readInt();
            if (suite <= previous) throw new IOException("Invalid suite list");
            kk |= suite == 1;
            previous = suite; // unknown future IDs are parsed but never selected
        }
        int selected = in.readInt();
        if (in.available() != 0 || (mode == TransportMode.NOISE_KK
                ? !kk || selected != (response ? 1 : 0) : suites != 0 || selected != 0)) {
            throw new IOException("Unsupported suite");
        }
        return new Hello(id, Set.copyOf(capabilities));
    }

    static byte[] prologue(byte[] first, byte[] second) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF("ServerWaypoint TCP v1");
        out.writeInt(first.length); out.write(first);
        out.writeInt(second.length); out.write(second);
        return bytes.toByteArray();
    }

    private TcpWire() { }
}
