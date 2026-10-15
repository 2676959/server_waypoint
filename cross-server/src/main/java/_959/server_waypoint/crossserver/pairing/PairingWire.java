package _959.server_waypoint.crossserver.pairing;

import _959.server_waypoint.crossserver.RemoteServerId;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Bounded bootstrap messages, independent of operational TCP modes and application messages. */
public final class PairingWire {
    public record Request(UUID ticket, RemoteServerId serverId, String publicKey) { }
    public static byte[] request(UUID ticket, RemoteServerId id, String publicKey, PairingCode code) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x53575050); out.writeInt(1); out.writeInt(1); // SWPP, bootstrap v1, future KK suite 1
        out.writeLong(ticket.getMostSignificantBits()); out.writeLong(ticket.getLeastSignificantBits());
        byte[] name = id.value().getBytes(StandardCharsets.US_ASCII);
        out.writeInt(name.length); out.write(name);
        out.write(CanonicalKey.publicBytes(publicKey));
        out.write(nonce());
        return append(bytes.toByteArray(), code.authenticate(1, bytes.toByteArray()));
    }
    public static Request parseRequest(byte[] request) throws IOException {
        if (request.length < 141 || request.length > 204) throw new IOException("Invalid pairing request");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(request));
        if (in.readInt() != 0x53575050 || in.readInt() != 1 || in.readInt() != 1) throw new IOException("Invalid pairing version");
        UUID ticket = new UUID(in.readLong(), in.readLong());
        int length = in.readInt();
        if (length < 1 || length > 64 || request.length != 140 + length) throw new IOException("Invalid pairing identity");
        byte[] name = new byte[length]; in.readFully(name);
        for (byte b : name) if (b < 0) throw new IOException("Invalid pairing identity");
        byte[] key = new byte[44]; in.readFully(key);
        String encoded = Base64.getEncoder().encodeToString(key);
        CanonicalKey.publicBytes(encoded);
        return new Request(ticket, new RemoteServerId(new String(name, StandardCharsets.US_ASCII)), encoded);
    }
    public static byte[] response(byte[] request, String publicKey, PairingCode code) {
        byte[] body = append(CanonicalKey.publicBytes(publicKey), nonce());
        return append(body, code.authenticate(2, append(request, body)));
    }
    public static byte[] transcript(byte[] request, byte[] response) throws IOException {
        if (request.length > 204 || response.length != 108) throw new IOException("Invalid pairing response");
        return append(request, Arrays.copyOf(response, 76));
    }
    public static String responseKey(byte[] response) throws IOException {
        if (response.length != 108) throw new IOException("Invalid pairing response");
        String key = Base64.getEncoder().encodeToString(Arrays.copyOf(response, 44));
        CanonicalKey.publicBytes(key);
        return key;
    }
    public static void verify(PairingCode code, int phase, byte[] transcript, byte[] proof) throws IOException {
        if (proof.length != 32 || !MessageDigest.isEqual(code.authenticate(phase, transcript), proof)) {
            throw new IOException("Pairing authentication rejected");
        }
    }
    public static void verifyRequest(PairingCode code, byte[] request) throws IOException {
        verify(code, 1, Arrays.copyOf(request, request.length - 32), Arrays.copyOfRange(request, request.length - 32, request.length));
    }
    private static byte[] nonce() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return bytes; }
    private static byte[] append(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length); System.arraycopy(b, 0, result, a.length, b.length); return result;
    }
    private PairingWire() { }
}
