package _959.server_waypoint.crossserver.pairing;

import _959.server_waypoint.crossserver.RuntimeConfiguration;
import _959.server_waypoint.crossserver.transport.TransportMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates a backend identity only when its configured credential store has no static key. */
public final class StaticKeyGenerator {
    private StaticKeyGenerator() { }

    public static Path generate(Path configDirectory) throws IOException {
        var config = RuntimeConfiguration.readBackend(configDirectory);
        if (RuntimeConfiguration.mode(config) != TransportMode.NOISE_KK) {
            throw new IOException("Static keys require NOISE_KK transport");
        }
        Path credentialDirectory = configDirectory.resolve(
                RuntimeConfiguration.text(config, "credentialsDirectory", "credentials"));
        try (CredentialFiles files = new CredentialFiles(credentialDirectory)) {
            if (files.read("static.key", 48) != null) {
                throw new IOException("Static key already exists; it was not replaced");
            }
            String publicKey = new LocalCredentials(files).publicKey();
            Path publicFile = configDirectory.resolve("cross-server-public-key.txt");
            Files.writeString(publicFile, publicKey + "\n");
            return publicFile;
        }
    }
}
