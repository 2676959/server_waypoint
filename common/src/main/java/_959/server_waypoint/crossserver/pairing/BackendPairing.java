package _959.server_waypoint.crossserver.pairing;

import _959.server_waypoint.crossserver.RemoteServerId;
import java.io.IOException;
import java.util.*;

/** One single-use bootstrap attempt. Caller must bound I/O and deliver messages on its transport worker. */
public final class BackendPairing implements AutoCloseable {
    private final LocalCredentials credentials;
    private final PairingCode code;
    private final String expectedPin;
    private final String localKey;
    private final byte[] request;
    private final long deadline;
    private byte[] transcript;
    private String newPin;
    private boolean closed;
    public BackendPairing(LocalCredentials credentials, UUID ticket, RemoteServerId id, PairingCode code,
                          String expectedPin, int timeoutMillis) throws IOException {
        if (timeoutMillis < 1 || timeoutMillis > 300_000) throw new IllegalArgumentException("Invalid pairing timeout");
        this.credentials = credentials;
        this.code = PairingCode.importCode(code.exportCode());
        this.expectedPin = expectedPin;
        deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        try {
            if (!Objects.equals(expectedPin, credentials.coordinatorPin())) throw new IOException("Coordinator pin changed");
            localKey = credentials.publicKey();
            request = PairingWire.request(ticket, id, localKey, this.code);
        } catch (Exception failure) { this.code.close(); throw new IOException("Pairing initialization rejected"); }
    }
    public synchronized byte[] request() throws IOException { requireOpen(); return request.clone(); }
    public synchronized byte[] confirm(byte[] response) throws IOException {
        try {
            requireOpen();
            if (transcript != null) throw new IOException("Pairing phase rejected");
            byte[] candidate = PairingWire.transcript(request, response);
            PairingWire.verify(code, 2, candidate, Arrays.copyOfRange(response, 76, 108));
            newPin = PairingWire.responseKey(response);
            transcript = candidate;
            return code.authenticate(3, transcript);
        } catch (Exception failure) { close(); throw new IOException("Pairing confirmation rejected"); }
    }
    public synchronized void finish(byte[] acknowledgement) throws IOException {
        try {
            requireOpen();
            if (transcript == null) throw new IOException("Pairing phase rejected");
            PairingWire.verify(code, 4, transcript, acknowledgement);
            if (!localKey.equals(credentials.publicKey())) throw new IOException("Local key changed");
            credentials.installPin(expectedPin, newPin);
        } catch (Exception failure) { throw new IOException("Pairing installation rejected"); }
        finally { close(); }
    }
    private void requireOpen() throws IOException {
        if (closed || System.nanoTime() - deadline >= 0) { close(); throw new IOException("Pairing closed or expired"); }
    }
    @Override public synchronized void close() { closed = true; code.close(); transcript = null; newPin = null; }
    @Override public String toString() { return "BackendPairing[redacted]"; }
}
