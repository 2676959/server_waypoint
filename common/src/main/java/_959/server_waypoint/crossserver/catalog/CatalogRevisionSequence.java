package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.crossserver.pairing.CredentialFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Durable high-water mark, stored in a separate catalog-state directory in either transport mode. */
public final class CatalogRevisionSequence implements AutoCloseable {
    private final CredentialFiles files;
    private long value;
    private boolean failed;
    public CatalogRevisionSequence(Path directory) throws IOException {
        files = new CredentialFiles(directory);
        try {
            byte[] data = files.read("revision", 19);
            if (data != null) {
                String text = new String(data, StandardCharsets.US_ASCII);
                value = Long.parseLong(text);
                if (value < 0 || !Long.toString(value).equals(text)) throw new IllegalArgumentException();
            }
        } catch (Exception failure) { files.close(); throw new IOException("Catalog revision state rejected"); }
    }
    public synchronized long next() throws IOException {
        if (failed || value == Long.MAX_VALUE) throw new IOException("Catalog revision unavailable");
        long next = value + 1;
        try { files.write("revision", Long.toString(next).getBytes(StandardCharsets.US_ASCII)); }
        catch (IOException failure) { failed = true; throw failure; }
        value = next;
        return value;
    }
    @Override public synchronized void close() throws IOException { failed = true; files.close(); }
}
