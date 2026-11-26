package _959.server_waypoint.common.client.local;

import _959.server_waypoint.common.client.ClientWaypointCollections;
import _959.server_waypoint.common.client.ClientWaypointSelection;
import _959.server_waypoint.common.client.ClientWaypointSource;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.net.IDN;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;

/**
 * Account/world-owned Local data. All operations, including disk I/O and binding publication, are
 * serialized by this owner. Callers capture a collection selection before queuing a mutation.
 * No handshake, server cache, map integration, or network operation participates in persistence.
 */
public final class LocalWaypointProfiles implements AutoCloseable {
    public static final String DEFAULT_TELEPORT_COMMAND = "/execute in {dimension} run tp @s {x} {y} {z} {yaw} ~";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path gameRoot;
    private final ClientWaypointCollections collections;
    private Path directory;
    private Index index;
    private Profile active;
    private ProfileFiles files;
    private Integer backend;
    private boolean manual;
    private String place;

    /** How the active profile came to be in use. */
    public enum ActiveReason {
        /** The address's or world's saved profile, chosen on join. */
        FALLBACK,
        /** The profile bound to the backend identity the server reported. */
        BACKEND,
        /** The player picked it: Use, New, or deleting the active profile. */
        MANUAL
    }

    public record Profile(UUID id, String name, String teleportCommand) {
        public Profile {
            Objects.requireNonNull(id, "id");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Empty profile name");
            Objects.requireNonNull(teleportCommand, "teleportCommand");
        }
    }

    private record Index(Integer version, UUID fallback, List<Profile> profiles, Map<Integer, UUID> bindings) { }

    public LocalWaypointProfiles(Path gameRoot, ClientWaypointCollections collections) {
        this.gameRoot = Objects.requireNonNull(gameRoot);
        this.collections = Objects.requireNonNull(collections);
    }

    public synchronized Profile active() { return active; }
    public synchronized Path directory() { return directory; }
    public synchronized List<Profile> profiles() { return index == null ? List.of() : index.profiles(); }

    /** The server address as typed, or the singleplayer world's name; {@code null} outside a session. */
    public synchronized @Nullable String place() { return place; }

    public synchronized @Nullable ActiveReason activeReason() {
        if (active == null) return null;
        if (manual) return ActiveReason.MANUAL;
        return backend != null && active.id().equals(index.bindings().get(backend)) ? ActiveReason.BACKEND : ActiveReason.FALLBACK;
    }

    public synchronized void joinMultiplayer(UUID account, String address) throws IOException {
        join(gameRoot.resolve("server_waypoint/local").resolve(Objects.requireNonNull(account).toString())
                .resolve(addressDigest(address)), address.strip());
    }

    public synchronized void joinSingleplayer(UUID account, Path worldSave, String worldName) throws IOException {
        join(worldSave.resolve("server_waypoint/client-local").resolve(Objects.requireNonNull(account).toString()),
                worldName.strip());
    }

    private void join(Path target, String label) throws IOException {
        save();
        Index loaded = readIndex(target);
        Profile selected = find(loaded, loaded.fallback());
        ProfileFiles loadedFiles = load(target, selected.id());
        writeIndex(target, loaded);
        directory = target;
        index = loaded;
        backend = null;
        manual = false;
        place = label;
        publish(selected, loadedFiles);
    }

    public synchronized void onBackendIdentity(int identity) throws IOException {
        requireActive();
        if (Objects.equals(backend, identity)) return;
        Index next = index;
        UUID selected;
        if (manual) {
            selected = active.id();
        } else if (index.bindings().containsKey(identity)) {
            selected = index.bindings().get(identity);
        } else {
            selected = index.fallback();
            if (index.bindings().containsValue(selected)) {
                Profile created = newProfile("World " + identity);
                var profiles = new ArrayList<>(index.profiles());
                profiles.add(created);
                next = new Index(1, index.fallback(), List.copyOf(profiles), index.bindings());
                selected = created.id();
            }
        }
        var bindings = new LinkedHashMap<>(next.bindings());
        bindings.put(identity, selected);
        next = new Index(1, next.fallback(), next.profiles(), Map.copyOf(bindings));
        switchTo(next, selected);
        backend = identity;
    }

    /** Creates and manually selects an empty profile; display names are never paths. */
    public synchronized Profile create(String name) throws IOException {
        requireActive();
        Profile created = newProfile(name);
        var profiles = new ArrayList<>(index.profiles());
        profiles.add(created);
        manualSelect(new Index(1, index.fallback(), List.copyOf(profiles), index.bindings()), created.id());
        return active;
    }

    public synchronized void select(UUID id) throws IOException {
        requireActive();
        find(index, id);
        manualSelect(index, id);
    }

    private void manualSelect(Index next, UUID id) throws IOException {
        var bindings = new LinkedHashMap<>(next.bindings());
        if (backend != null) bindings.put(backend, id);
        switchTo(new Index(1, id, next.profiles(), Map.copyOf(bindings)), id);
        manual = true;
    }

    public synchronized void rename(UUID id, String name) throws IOException {
        requireActive();
        Profile before = find(index, id);
        replaceProfile(new Profile(id, name, before.teleportCommand()));
    }

    /** Template validation/submission belongs to LocalTeleportCommand, not storage. */
    public synchronized void setTeleportCommand(String template) throws IOException {
        requireActive();
        replaceProfile(new Profile(active.id(), active.name(), template));
    }

    private void replaceProfile(Profile replacement) throws IOException {
        var profiles = index.profiles().stream().map(profile -> profile.id().equals(replacement.id()) ? replacement : profile).toList();
        Index next = new Index(1, index.fallback(), profiles, index.bindings());
        writeIndex(directory, next);
        index = next;
        if (active.id().equals(replacement.id())) active = replacement;
    }

    /** The caller presents the existing confirmation dialog before invoking deletion. */
    public synchronized void delete(UUID id) throws IOException {
        requireActive();
        find(index, id);
        var profiles = new ArrayList<>(index.profiles());
        profiles.removeIf(profile -> profile.id().equals(id));
        if (profiles.isEmpty()) profiles.add(newProfile("Default"));
        UUID fallback = index.fallback().equals(id) ? profiles.get(0).id() : index.fallback();
        var bindings = new LinkedHashMap<>(index.bindings());
        bindings.values().removeIf(id::equals);
        Index next = new Index(1, fallback, List.copyOf(profiles), Map.copyOf(bindings));
        boolean changesSelection = active.id().equals(id);
        UUID selected = changesSelection ? fallback : active.id();
        switchTo(next, selected);
        if (changesSelection) manual = true;
        Path removed = profilePath(directory, id);
        if (Files.exists(removed, LinkOption.NOFOLLOW_LINKS)) {
            try (var paths = Files.walk(removed)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private void switchTo(Index next, UUID selected) throws IOException {
        save(); // Strict: saveAllWaypointFiles deliberately logs failures instead of throwing.
        Profile profile = find(next, selected);
        ProfileFiles replacement = active.id().equals(selected) ? files : load(directory, selected);
        writeIndex(directory, next);
        index = next;
        if (replacement != files) publish(profile, replacement);
        else active = profile;
    }

    private void publish(Profile profile, ProfileFiles replacement) {
        active = profile;
        files = replacement;
        collections.bindLocal(profile.id(), replacement);
    }

    /** A persistence exception is never a durable-success outcome; keep the live data for retry. */
    public synchronized <T> T mutate(ClientWaypointSelection selection, Function<WaypointFilesManagerCore, T> operation) throws IOException {
        requireActive();
        if (selection.source() != ClientWaypointSource.LOCAL || !collections.valid(selection)
                || !active.id().equals(selection.profileId())) throw new IllegalStateException("Obsolete Local selection");
        try {
            T result = operation.apply(files);
            save();
            return result;
        } finally {
            collections.changed(ClientWaypointSource.LOCAL);
        }
    }

    public synchronized void save() throws IOException {
        if (files == null) return;
        for (var manager : files.getFileManagerMap().values()) files.saveWaypointFile(manager);
    }

    public synchronized void leave() throws IOException {
        save();
        collections.unbindLocal();
        directory = null;
        index = null;
        active = null;
        files = null;
        backend = null;
        manual = false;
        place = null;
    }

    @Override
    public synchronized void close() throws IOException { leave(); }

    private void requireActive() {
        if (active == null || !collections.ready(ClientWaypointSource.LOCAL)
                || collections.files(ClientWaypointSource.LOCAL) != files) {
            throw new IllegalStateException("No bound Local profile");
        }
    }

    private static Profile newProfile(String name) {
        return new Profile(UUID.randomUUID(), name, DEFAULT_TELEPORT_COMMAND);
    }

    private static Profile find(Index index, UUID id) {
        return index.profiles().stream().filter(profile -> profile.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Local profile: " + id));
    }

    private static Index readIndex(Path directory) throws IOException {
        Path path = directory.resolve("index.json");
        if (!Files.exists(path)) {
            if (Files.exists(directory)) {
                try (var children = Files.list(directory)) {
                    if (children.findAny().isPresent()) throw new IOException("Missing Local profile index: " + path);
                }
            }
            Profile initial = newProfile("Default");
            return new Index(1, initial.id(), List.of(initial), Map.of());
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Index index = GSON.fromJson(reader, Index.class);
            if (index == null || !Integer.valueOf(1).equals(index.version()) || index.fallback() == null
                    || index.profiles() == null || index.profiles().isEmpty() || index.bindings() == null) {
                throw new IllegalArgumentException("Invalid Local profile index");
            }
            Set<UUID> ids = new HashSet<>();
            for (Profile profile : index.profiles()) {
                if (profile == null || !ids.add(profile.id())) throw new IllegalArgumentException("Duplicate/missing profile");
                if (!Files.isDirectory(profilePath(directory, profile.id()), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Missing Local profile directory: " + profile.id());
                }
            }
            if (!ids.contains(index.fallback()) || index.bindings().containsKey(null)
                    || !ids.containsAll(index.bindings().values())) throw new IllegalArgumentException("Invalid Local profile binding");
            return new Index(1, index.fallback(), List.copyOf(index.profiles()), Map.copyOf(index.bindings()));
        } catch (JsonParseException | IllegalArgumentException | NullPointerException exception) {
            throw new IOException("Cannot read Local profile index: " + path, exception);
        }
    }

    private static void writeIndex(Path directory, Index index) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "index-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(index), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, directory.resolve("index.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, directory.resolve("index.json"), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path profilePath(Path directory, UUID id) {
        return directory.resolve("profiles").resolve(id.toString());
    }

    private static ProfileFiles load(Path directory, UUID id) throws IOException {
        var files = new ProfileFiles(profilePath(directory, id));
        try { files.load(); }
        catch (RuntimeException exception) { throw new IOException("Cannot read Local profile: " + id, exception); }
        return files;
    }

    private static final class ProfileFiles extends WaypointFilesManagerCore {
        private ProfileFiles(Path directory) { super(directory); }
        private void load() throws IOException { initOrReadCurrentWaypointFiles(); }
    }

    private static String addressDigest(String address) {
        String value = Objects.requireNonNull(address).trim();
        String host;
        String port = "25565";
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end < 0) throw new IllegalArgumentException("Invalid server address");
            host = value.substring(1, end);
            String suffix = value.substring(end + 1);
            if (!suffix.isEmpty()) {
                if (!suffix.startsWith(":")) throw new IllegalArgumentException("Invalid server port");
                port = suffix.substring(1);
            }
        } else {
            int colon = value.lastIndexOf(':');
            if (colon >= 0 && value.indexOf(':') == colon) {
                host = value.substring(0, colon);
                port = value.substring(colon + 1);
            } else host = value;
        }
        if (host.isBlank()) throw new IllegalArgumentException("Empty server host");
        if (host.indexOf(':') >= 0) {
            try { host = InetAddress.getByName(host).getHostAddress(); }
            catch (IOException exception) { throw new IllegalArgumentException("Invalid IPv6 host", exception); }
        } else {
            host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        }
        int effectivePort = Integer.parseInt(port);
        if (effectivePort < 1 || effectivePort > 65535) throw new IllegalArgumentException("Invalid server port");
        String normalized = "[" + host + "]:" + effectivePort;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
