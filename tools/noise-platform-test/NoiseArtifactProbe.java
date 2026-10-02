import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Development-only observer. Never transforms game classes or enters a release artifact. */
public final class NoiseArtifactProbe {
    private static final String PREFIX = "_959.server_waypoint.internal.noisekk.protocol.";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one final artifact path");
        }
        if (!Files.isRegularFile(Path.of(args[0]))) {
            throw new IllegalArgumentException("Artifact does not exist: " + args[0]);
        }
        try (var loader = new URLClassLoader(new java.net.URL[]{Path.of(args[0]).toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            run(loader);
            System.out.println("SW_NOISE_ARTIFACT PASS " + Path.of(args[0]).getFileName());
        }
    }

    /** agent options: fully-qualified platform entrypoint|new evidence file */
    public static void premain(String options, Instrumentation instrumentation) {
        String[] args = options.split("\\|", 2);
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected entrypoint|evidence path");
        }
        Thread observer = new Thread(() -> {
            try {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(3);
                while (System.nanoTime() < deadline) {
                    for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
                        if (loaded.getName().equals(args[0])) {
                            ClassLoader loader = loaded.getClassLoader();
                            run(loader);
                            Class<?> cipher = Class.forName(PREFIX + "HandshakeState", true, loader);
                            String evidence = "PASS entrypoint=" + args[0] + "\nloader=" + loader.getClass().getName()
                                    + "\nsource=" + cipher.getProtectionDomain().getCodeSource().getLocation() + "\n";
                            Files.writeString(Path.of(args[1]), evidence);
                            System.out.println("SW_NOISE_PLATFORM " + evidence.replace('\n', ' '));
                            return;
                        }
                    }
                    Thread.sleep(100);
                }
                throw new IllegalStateException("Platform entrypoint was not loaded");
            } catch (Throwable failure) {
                try {
                    Files.writeString(Path.of(args[1]), "FAIL " + failure + "\n");
                } catch (Exception ignored) {
                    // The console remains the second evidence sink.
                }
                failure.printStackTrace();
            }
        }, "server-waypoint-noise-classloader-probe");
        observer.setDaemon(true);
        observer.start();
    }

    public static void run(ClassLoader loader) throws Exception {
        Class<?> noise = Class.forName(PREFIX + "Noise", true, loader);
        Class<?> dh = Class.forName(PREFIX + "DHState", true, loader);
        Class<?> handshake = Class.forName(PREFIX + "HandshakeState", true, loader);
        Class<?> pair = Class.forName(PREFIX + "CipherStatePair", true, loader);
        Class<?> cipher = Class.forName(PREFIX + "CipherState", true, loader);
        Object aKeys = noise.getMethod("createDH", String.class).invoke(null, "25519");
        Object bKeys = noise.getMethod("createDH", String.class).invoke(null, "25519");
        Object a = null;
        Object b = null;
        Object at = null;
        Object bt = null;
        byte[] ap = new byte[32];
        byte[] bp = new byte[32];
        try {
            dh.getMethod("generateKeyPair").invoke(aKeys);
            dh.getMethod("generateKeyPair").invoke(bKeys);
            byte[] au = new byte[32];
            byte[] bu = new byte[32];
            dh.getMethod("getPrivateKey", byte[].class, int.class).invoke(aKeys, ap, 0);
            dh.getMethod("getPrivateKey", byte[].class, int.class).invoke(bKeys, bp, 0);
            dh.getMethod("getPublicKey", byte[].class, int.class).invoke(aKeys, au, 0);
            dh.getMethod("getPublicKey", byte[].class, int.class).invoke(bKeys, bu, 0);
            var constructor = handshake.getConstructor(String.class, int.class);
            a = constructor.newInstance("Noise_KK_25519_AESGCM_SHA256", handshake.getField("INITIATOR").getInt(null));
            b = constructor.newInstance("Noise_KK_25519_AESGCM_SHA256", handshake.getField("RESPONDER").getInt(null));
            configure(handshake, dh, a, ap, bu);
            configure(handshake, dh, b, bp, au);
            read(handshake, b, write(handshake, a));
            read(handshake, a, write(handshake, b));
            equal((byte[]) handshake.getMethod("getHandshakeHash").invoke(a),
                    (byte[]) handshake.getMethod("getHandshakeHash").invoke(b));
            at = handshake.getMethod("split").invoke(a);
            bt = handshake.getMethod("split").invoke(b);
            Object sendA = pair.getMethod("getSender").invoke(at);
            Object recvB = pair.getMethod("getReceiver").invoke(bt);
            Object sendB = pair.getMethod("getSender").invoke(bt);
            Object recvA = pair.getMethod("getReceiver").invoke(at);
            for (int size : new int[]{0, 31, 65_519}) {
                byte[] payload = new byte[size];
                Arrays.fill(payload, (byte) 41);
                equal(payload, decrypt(cipher, recvB, encrypt(cipher, sendA, payload)));
                Arrays.fill(payload, (byte) 73);
                equal(payload, decrypt(cipher, recvA, encrypt(cipher, sendB, payload)));
            }
            byte[] replay = encrypt(cipher, sendA, new byte[]{7});
            equal(new byte[]{7}, decrypt(cipher, recvB, replay));
            try {
                decrypt(cipher, recvB, replay);
                throw new AssertionError("Replayed transport accepted");
            } catch (InvocationTargetException rejected) {
                if (!(rejected.getCause() instanceof javax.crypto.BadPaddingException)) {
                    throw rejected;
                }
            }
            // Fresh sender state for the last legal nonce; repeated reserved-nonce calls must reject.
            cipher.getMethod("setNonce", long.class).invoke(sendB, -2L);
            encrypt(cipher, sendB, new byte[0]);
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    encrypt(cipher, sendB, new byte[0]);
                    throw new AssertionError("Exhausted nonce accepted");
                } catch (InvocationTargetException rejected) {
                    if (!(rejected.getCause() instanceof IllegalStateException)) {
                        throw rejected;
                    }
                }
            }
        } finally {
            Arrays.fill(ap, (byte) 0);
            Arrays.fill(bp, (byte) 0);
            dh.getMethod("destroy").invoke(aKeys);
            dh.getMethod("destroy").invoke(bKeys);
            if (a != null) handshake.getMethod("destroy").invoke(a);
            if (b != null) handshake.getMethod("destroy").invoke(b);
            if (at != null) pair.getMethod("destroy").invoke(at);
            if (bt != null) pair.getMethod("destroy").invoke(bt);
        }
    }

    private static void configure(Class<?> handshake, Class<?> dh, Object state, byte[] privateKey, byte[] pin)
            throws Exception {
        dh.getMethod("setPrivateKey", byte[].class, int.class)
                .invoke(handshake.getMethod("getLocalKeyPair").invoke(state), privateKey, 0);
        dh.getMethod("setPublicKey", byte[].class, int.class)
                .invoke(handshake.getMethod("getRemotePublicKey").invoke(state), pin, 0);
        byte[] prologue = new byte[]{1, 2, 3};
        handshake.getMethod("setPrologue", byte[].class, int.class, int.class).invoke(state, prologue, 0, prologue.length);
        handshake.getMethod("start").invoke(state);
    }

    private static byte[] write(Class<?> handshake, Object state) throws Exception {
        byte[] message = new byte[65_535];
        int length = (int) handshake.getMethod("writeMessage", byte[].class, int.class, byte[].class, int.class, int.class)
                .invoke(state, message, 0, new byte[0], 0, 0);
        return Arrays.copyOf(message, length);
    }

    private static void read(Class<?> handshake, Object state, byte[] message) throws Exception {
        int length = (int) handshake.getMethod("readMessage", byte[].class, int.class, int.class, byte[].class, int.class)
                .invoke(state, message, 0, message.length, new byte[65_535], 0);
        if (length != 0) throw new AssertionError("Unexpected handshake payload");
    }

    private static byte[] encrypt(Class<?> cipher, Object state, byte[] payload) throws Exception {
        byte[] output = new byte[payload.length + 16];
        int length = (int) cipher.getMethod("encryptWithAd", byte[].class, byte[].class, int.class,
                byte[].class, int.class, int.class).invoke(state, null, payload, 0, output, 0, payload.length);
        if (length != output.length) throw new AssertionError("Cipher did not encrypt");
        return output;
    }

    private static byte[] decrypt(Class<?> cipher, Object state, byte[] payload) throws Exception {
        byte[] output = new byte[payload.length];
        int length = (int) cipher.getMethod("decryptWithAd", byte[].class, byte[].class, int.class,
                byte[].class, int.class, int.class).invoke(state, null, payload, 0, output, 0, payload.length);
        return Arrays.copyOf(output, length);
    }

    private static void equal(byte[] expected, byte[] actual) {
        if (!Arrays.equals(expected, actual)) throw new AssertionError("Payload mismatch");
    }
}
