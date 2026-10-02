package _959.server_waypoint.crossserver.pairing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Set;

/** Owner-only files under an owner-only, caller-selected credential directory. */
public final class CredentialFiles implements AutoCloseable {
    private final Path directory;
    private final boolean posix;
    private final FileChannel ownership;
    private final java.nio.channels.FileLock lock;
    private boolean closed;
    /** Validate mode/endpoint first. Plaintext never touches the credential directory. */
    public static java.util.Optional<CredentialFiles> forTransport(
            _959.server_waypoint.crossserver.transport.TransportMode mode,
            _959.server_waypoint.crossserver.transport.TcpEndpoint endpoint, Path directory) throws IOException {
        endpoint.validate(mode);
        return mode == _959.server_waypoint.crossserver.transport.TransportMode.PLAINTEXT
                ? java.util.Optional.empty() : java.util.Optional.of(new CredentialFiles(directory));
    }

    public CredentialFiles(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        Path ancestor = this.directory;
        while (ancestor != null) {
            if (Files.isSymbolicLink(ancestor)) throw new IOException("Credential symlink rejected");
            ancestor = ancestor.getParent();
        }
        posix = Files.getFileStore(this.directory.getParent()).supportsFileAttributeView("posix");
        if (!Files.exists(this.directory, LinkOption.NOFOLLOW_LINKS)) {
            if (posix) Files.createDirectory(this.directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            else Files.createDirectory(this.directory);
        }
        if (!Files.isDirectory(this.directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid credential directory");
        if (posix) Files.setPosixFilePermissions(this.directory, PosixFilePermissions.fromString("rwx------"));
        Path ownerFile = this.directory.resolve(".owner.lock");
        ownership = posix ? FileChannel.open(ownerFile,
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                : FileChannel.open(ownerFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            lock = ownership.tryLock();
            if (lock == null) throw new IOException("Credential directory already owned");
        } catch (Exception failure) {
            ownership.close(); throw new IOException("Credential directory already owned");
        }
    }

    public synchronized byte[] read(String name, int maximum) throws IOException {
        Path file = path(name);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid credential file");
        if (posix) Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        try (var channel = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            if (channel.size() > maximum) throw new IOException("Credential file too large");
            ByteBuffer bytes = ByteBuffer.allocate(maximum + 1);
            while (bytes.hasRemaining() && channel.read(bytes) != -1) { }
            if (bytes.position() > maximum) throw new IOException("Credential file too large");
            return java.util.Arrays.copyOf(bytes.array(), bytes.position());
        }
    }

    public synchronized void write(String name, byte[] bytes) throws IOException {
        Path target = path(name);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid credential file");
        }
        Path temporary = posix ? Files.createTempFile(directory, ".credential-", ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                : Files.createTempFile(directory, ".credential-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            if (posix) {
                try (FileChannel parent = FileChannel.open(directory, StandardOpenOption.READ)) { parent.force(true); }
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private Path path(String name) throws IOException {
        if (closed) throw new IOException("Credential store closed");
        if (!name.matches("[a-z][a-z0-9.-]{0,63}")) throw new IllegalArgumentException("Invalid credential name");
        return directory.resolve(name);
    }
    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try { lock.release(); } finally { ownership.close(); }
    }
    @Override public String toString() { return "CredentialFiles[redacted]"; }
}
