package _959.server_waypoint.proxy.pairing;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.pairing.*;
import _959.server_waypoint.crossserver.protocol.ProtocolLimits;
import _959.server_waypoint.crossserver.transport.*;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Coordinator-only admission and administrative pairing state. No commands or bootstrap listener. */
public final class PairingCoordinator implements AutoCloseable {
    public record Invitation(UUID ticket, PairingCode code) implements AutoCloseable {
        @Override public void close() { code.close(); }
        @Override public String toString() { return "Invitation[redacted]"; }
    }
    private static final class Pending implements AutoCloseable {
        final RemoteServerId id;
        final String expected;
        final PairingCode code;
        final long deadline;
        byte[] transcript;
        String backendKey;
        int attempts;
        Pending(RemoteServerId id, String expected, PairingCode code, long deadline) {
            this.id = id; this.expected = expected; this.code = code; this.deadline = deadline;
        }
        @Override public void close() { code.close(); transcript = null; backendKey = null; }
    }
    private final CredentialFiles files;
    private final LocalCredentials local;
    private final LongSupplier clock;
    private final Map<UUID, Pending> invitations = new HashMap<>();
    private final Map<RemoteServerId, String> pins = new HashMap<>();
    private final ScheduledExecutorService timer;
    private TcpCoordinator listener;
    private NoiseKeys listenerKeys;
    private boolean closed;

    public PairingCoordinator(CredentialFiles files) throws IOException { this(files, System::nanoTime); }
    PairingCoordinator(CredentialFiles files, LongSupplier monotonicNanos) throws IOException {
        this.files = files;
        local = new LocalCredentials(files);
        clock = monotonicNanos;
        loadPins();
        if (!pins.isEmpty() && files.read("static.key", 48) == null) throw new IOException("Paired coordinator key missing");
        String coordinatorKey = local.publicKey(); // absent key generates; malformed/missing paired keys fail closed
        if (pins.containsValue(coordinatorKey) || new HashSet<>(pins.values()).size() != pins.size()) {
            throw new IOException("Static keys must be unique");
        }
        timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "server-waypoint-pairing-expiry"); thread.setDaemon(true); return thread;
        });
        timer.scheduleWithFixedDelay(this::expire, 1, 1, TimeUnit.SECONDS);
    }
    public synchronized Map<RemoteServerId, String> pins() { return Map.copyOf(pins); }
    public synchronized String publicKey() throws IOException { requireOpen(); return local.publicKey(); }

    /** Starts only the operational listener explicitly. Pairing messages still require an owner-supplied carrier. */
    public synchronized TcpCoordinator listen(TcpEndpoint endpoint, TcpLimits limits, ProtocolLimits protocol) throws IOException {
        requireOpen();
        if (listener != null) throw new IOException("Listener already owned");
        Map<RemoteServerId, byte[]> raw = new HashMap<>();
        pins.forEach((id, pin) -> raw.put(id, CanonicalKey.rawPublic(pin)));
        listenerKeys = local.noiseKeys();
        try {
            listener = new TcpCoordinator(endpoint, TransportMode.NOISE_KK, listenerKeys, raw, limits, protocol);
            return listener;
        } catch (Exception failure) { listenerKeys.close(); listenerKeys = null; throw new IOException("Listener start rejected"); }
    }

    /** Administrative API: expectedPin must exactly match current registration, or null for a new ID. */
    public synchronized Invitation pair(RemoteServerId id, String expectedPin, int lifetimeMillis) throws IOException {
        requireOpen(); expire();
        Objects.requireNonNull(id);
        if (lifetimeMillis < 1 || lifetimeMillis > 300_000 || !Objects.equals(pins.get(id), expectedPin)) {
            throw new IOException("Pairing request rejected");
        }
        if (invitations.size() >= 64 || pins.size() >= 4096 && !pins.containsKey(id)) throw new IOException("Pairing capacity");
        invitations.entrySet().removeIf(entry -> {
            if (!entry.getValue().id.equals(id)) return false;
            entry.getValue().close(); return true;
        });
        PairingCode secret = PairingCode.generate();
        UUID ticket = UUID.randomUUID();
        invitations.put(ticket, new Pending(id, expectedPin, secret, clock.getAsLong() + lifetimeMillis * 1_000_000L));
        return new Invitation(ticket, PairingCode.importCode(secret.exportCode()));
    }

    public synchronized byte[] begin(byte[] request) throws IOException {
        requireOpen(); expire();
        Pending pending = null;
        try {
            PairingWire.Request parsed = PairingWire.parseRequest(request);
            pending = invitations.get(parsed.ticket());
            if (pending == null || pending.transcript != null || !pending.id.equals(parsed.serverId())
                    || !Objects.equals(pins.get(pending.id), pending.expected)) throw new IOException("Pairing unavailable");
            PairingWire.verifyRequest(pending.code, request);
            String coordinatorKey = local.publicKey();
            if (parsed.publicKey().equals(coordinatorKey) || pins.entrySet().stream().anyMatch(entry ->
                    !entry.getKey().equals(parsed.serverId()) && entry.getValue().equals(parsed.publicKey()))) {
                throw new IOException("Static keys must be unique");
            }
            byte[] response = PairingWire.response(request, coordinatorKey, pending.code);
            pending.transcript = PairingWire.transcript(request, response);
            pending.backendKey = parsed.publicKey();
            return response;
        } catch (Exception failure) {
            if (pending != null && ++pending.attempts >= 8) remove(pending);
            throw new IOException("Pairing request rejected");
        }
    }

    public synchronized byte[] complete(UUID ticket, byte[] confirmation) throws IOException {
        requireOpen(); expire();
        Pending pending = invitations.get(ticket);
        if (pending == null || pending.transcript == null) throw new IOException("Pairing unavailable");
        try {
            PairingWire.verify(pending.code, 3, pending.transcript, confirmation);
            if (!Objects.equals(pins.get(pending.id), pending.expected)) throw new IOException("Registration changed");
            if (pins.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(pending.id)
                    && entry.getValue().equals(pending.backendKey))) throw new IOException("Static keys must be unique");
            byte[] acknowledgement = pending.code.authenticate(4, pending.transcript);
            // Consume before durable mutation. Any failure requires a fresh administrative invitation.
            invitations.remove(ticket);
            Map<RemoteServerId, String> next = new HashMap<>(pins);
            next.put(pending.id, pending.backendKey);
            persist(next);
            pins.clear(); pins.putAll(next);
            if (listener != null) listener.replacePin(pending.id, CanonicalKey.rawPublic(pending.backendKey));
            return acknowledgement;
        } catch (Exception failure) { throw new IOException("Pairing completion rejected"); }
        finally { invitations.remove(ticket); pending.close(); }
    }

    /** Durable revocation precedes transport invalidation, so restart cannot re-admit the old pin. */
    public synchronized void revoke(RemoteServerId id) throws IOException {
        requireOpen();
        Map<RemoteServerId, String> next = new HashMap<>(pins); next.remove(id);
        persist(next); pins.clear(); pins.putAll(next);
        invitations.entrySet().removeIf(entry -> {
            if (!entry.getValue().id.equals(id)) return false;
            entry.getValue().close(); return true;
        });
        if (listener != null) listener.replacePin(id, null);
    }

    /** Administrative coordinator rotation invalidates every old registration and requires re-pairing. */
    public synchronized void rotateCoordinator() throws IOException {
        requireOpen();
        persist(Map.of()); pins.clear();
        invitations.values().forEach(Pending::close); invitations.clear();
        stopListener();
        local.rotate();
    }

    private synchronized void expire() {
        long now = clock.getAsLong();
        invitations.entrySet().removeIf(entry -> {
            if (now - entry.getValue().deadline < 0) return false;
            entry.getValue().close(); return true;
        });
    }
    private void remove(Pending pending) {
        invitations.values().removeIf(value -> value == pending); pending.close();
    }
    private void persist(Map<RemoteServerId, String> values) throws IOException {
        Map<String, String> sorted = new TreeMap<>(); values.forEach((id, pin) -> sorted.put(id.value(), pin));
        try {
            files.write("backend-pins.json", new Gson().toJson(sorted).getBytes(StandardCharsets.US_ASCII));
        } catch (IOException failure) {
            // A rename/fsync failure may have an uncertain durable outcome. Stop serving either view.
            try { close(); } catch (IOException ignored) { }
            throw new IOException("Credential persistence failed; coordinator stopped");
        }
    }
    private void loadPins() throws IOException {
        byte[] bytes = files.read("backend-pins.json", 1_048_576);
        if (bytes == null) return;
        try (JsonReader reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.US_ASCII)))) {
            reader.setLenient(false); reader.beginObject();
            while (reader.hasNext()) {
                if (pins.size() >= 4096) throw new IOException("Admission capacity");
                RemoteServerId id = new RemoteServerId(reader.nextName());
                if (pins.containsKey(id) || reader.peek() != JsonToken.STRING) throw new IOException("Invalid admission entry");
                String key = reader.nextString(); CanonicalKey.publicBytes(key); pins.put(id, key);
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Invalid admission document");
        } catch (Exception failure) { throw new IOException("Credential registry rejected"); }
    }
    private void requireOpen() throws IOException { if (closed) throw new IOException("Pairing coordinator closed"); }
    private void stopListener() throws IOException {
        try { if (listener != null) listener.close(); }
        finally { listener = null; if (listenerKeys != null) listenerKeys.close(); listenerKeys = null; }
    }
    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true; timer.shutdownNow();
        invitations.values().forEach(Pending::close); invitations.clear();
        stopListener();
    }
    @Override public String toString() { return "PairingCoordinator[redacted]"; }
}
