package _959.server_waypoint.common.client;

import _959.server_waypoint.common.client.local.LocalTeleportCommand;
import _959.server_waypoint.common.client.local.LocalWaypointProfiles;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.edit.*;
import _959.server_waypoint.core.network.message.*;
import _959.server_waypoint.core.waypoint.*;
import _959.server_waypoint.text.FormattedTextHelper;
import _959.server_waypoint.util.NamespacedId;
import _959.server_waypoint.util.StringCommandBuilder;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Client-thread operation boundary. Every operation carries its captured source and generation. */
public final class ClientWaypointAccess {
    public enum Action { READ, ADD, EDIT, REMOVE, TELEPORT }
    public enum Status { SAVED, AWAITING_SERVER, COMMAND_SUBMITTED, REJECTED, OBSOLETE, DENIED, DISCONNECTED, SEND_FAILED, PERSISTENCE_FAILED }
    public enum Problem { INVALID_TEMPLATE, DIMENSION_REQUIRED, COMMAND_TOO_LONG }
    public record Outcome(Status status, @Nullable EditResultStatus editStatus, @Nullable Problem problem) {
        public static Outcome of(Status status) { return new Outcome(status, null, null); }
        public static Outcome rejected(Problem problem) { return new Outcome(Status.REJECTED, null, problem); }
    }
    /** The transport owns live connection, protocol length, and advertised command permissions. */
    public interface Transport {
        boolean connected();
        String currentDimension();
        int commandLimit();
        List<String> dimensions();
        boolean allowed(ClientWaypointSource source, Action action);
        boolean command(String command);
        boolean edit(WaypointEditRequestMessage message);
    }
    /** Read and destination identities stay captured; retrieving the draft never exposes its instance. */
    public record CopyDraft(ClientWaypointSelection source, ClientWaypointSelection destination, SimpleWaypoint waypoint) {
        public CopyDraft { waypoint = new SimpleWaypoint(waypoint); }
        @Override public SimpleWaypoint waypoint() { return new SimpleWaypoint(waypoint); }
    }
    private record Pending(ClientWaypointSelection selection, CompletableFuture<Outcome> completion) { }
    private static final AtomicLong REQUEST_IDS = new AtomicLong();
    private final ClientWaypointCollections collections;
    private final LocalWaypointProfiles profiles;
    private final Transport transport;
    private final Map<Long, Pending> pending = new HashMap<>();

    public ClientWaypointAccess(ClientWaypointCollections collections, LocalWaypointProfiles profiles, Transport transport) {
        this.collections = Objects.requireNonNull(collections);
        this.profiles = Objects.requireNonNull(profiles);
        this.transport = Objects.requireNonNull(transport);
    }
    public ClientWaypointSelection capture(ClientWaypointSource source, String dimension, String list, @Nullable String name) {
        return collections.selection(source, dimension, list, name);
    }
    public boolean valid(ClientWaypointSelection selection) { return collections.valid(selection); }
    public @Nullable WaypointList list(ClientWaypointSelection selection) {
        if (!valid(selection)) return null;
        var manager = collections.files(selection.source()).getWaypointFileManager(selection.dimensionName());
        return manager == null ? null : manager.getWaypointListByName(selection.listIdentifier());
    }
    public @Nullable SimpleWaypoint waypoint(ClientWaypointSelection selection) {
        if (selection.waypointIdentifier() == null) return null;
        WaypointList list = list(selection);
        return list == null ? null : list.getWaypointByName(selection.waypointIdentifier());
    }
    public List<String> dimensions(ClientWaypointSelection selection) {
        if (!valid(selection)) return List.of();
        Set<String> dimensions = new LinkedHashSet<>(transport.dimensions());
        dimensions.addAll(collections.files(selection.source()).getFileManagerMap().keySet());
        return List.copyOf(dimensions);
    }
    public List<String> lists(ClientWaypointSelection selection) {
        if (!valid(selection)) return List.of();
        var manager = collections.files(selection.source()).getWaypointFileManager(selection.dimensionName());
        return manager == null ? List.of() : manager.getWaypointLists().stream().map(WaypointList::name).toList();
    }
    public List<String> names(ClientWaypointSelection selection) {
        WaypointList list = list(selection);
        return list == null ? List.of() : list.simpleWaypoints().stream().map(SimpleWaypoint::name).toList();
    }
    public CopyDraft copyDraft(ClientWaypointSelection source, ClientWaypointSelection destination) {
        SimpleWaypoint waypoint = waypoint(source);
        if (waypoint == null || unavailable(source, Action.READ) != null || !valid(destination)) {
            throw new IllegalStateException("Unavailable copy source or destination");
        }
        return new CopyDraft(source, destination, waypoint);
    }
    /** Submitting an edited draft must still have source read and destination add access. */
    public Outcome addCopy(CopyDraft draft) {
        Outcome failure = unavailable(draft.source(), Action.READ);
        return failure == null ? add(draft.destination(), draft.waypoint()) : failure;
    }

    private @Nullable Outcome unavailable(ClientWaypointSelection selection, Action action) {
        if (!valid(selection)) return Outcome.of(Status.OBSOLETE);
        if (selection.source() == ClientWaypointSource.LOCAL) return null;
        if (!transport.connected()) return Outcome.of(Status.DISCONNECTED);
        return transport.allowed(selection.source(), action) ? null : Outcome.of(Status.DENIED);
    }
    public Outcome add(ClientWaypointSelection destination, SimpleWaypoint draft) {
        Outcome failure = unavailable(destination, Action.ADD);
        if (failure != null) return failure;
        SimpleWaypoint waypoint = new SimpleWaypoint(draft);
        if (!validAddition(destination, waypoint)) return Outcome.of(Status.REJECTED);
        if (destination.source() != ClientWaypointSource.LOCAL) {
            String command = StringCommandBuilder.addCmd(destination.dimensionName(), destination.listIdentifier(), waypoint);
            return send(destination, command, Status.AWAITING_SERVER);
        }
        try {
            return profiles.mutate(destination, files -> {
                var result = files.addWaypoint(destination.dimensionName(), destination.listIdentifier(), waypoint, ignored -> {});
                return Outcome.of(result.status() == WaypointFilesManagerCore.AddWaypointStatus.ADDED ? Status.SAVED : Status.REJECTED);
            });
        } catch (IOException exception) { return Outcome.of(Status.PERSISTENCE_FAILED); }
        catch (IllegalStateException exception) { return Outcome.of(Status.OBSOLETE); }
    }
    private boolean validAddition(ClientWaypointSelection destination, SimpleWaypoint waypoint) {
        try { NamespacedId.parse(destination.dimensionName()); }
        catch (IllegalArgumentException exception) { return false; }
        if (!validName(destination.listIdentifier()) || !validName(waypoint.name())
                || waypoint.initials().length() > FormattedTextHelper.MAX_NAME_LENGTH
                || (waypoint.displayNameOverride() != null && !validText(waypoint.displayNameOverride(), FormattedTextHelper.MAX_NAME_LENGTH))
                || !validText(waypoint.description(), FormattedTextHelper.MAX_DESCRIPTION_LENGTH)
                || waypoint.rgb() < 0 || waypoint.rgb() > 0xFFFFFF
                || waypoint.keywords().size() > FormattedTextHelper.MAX_KEYWORDS
                || FormattedTextHelper.hasDuplicateKeywords(waypoint.keywords())) return false;
        for (String keyword : waypoint.keywords()) if (keyword.length() > FormattedTextHelper.MAX_KEYWORD_LENGTH || hasControl(keyword)) return false;
        WaypointList list = list(destination);
        return list == null || list.getWaypointByName(waypoint.name()) == null;
    }
    private static boolean validName(String value) {
        return !value.isEmpty() && !hasControl(value);
    }
    private static boolean validText(String value, int limit) {
        return value.length() <= limit && !hasControl(value) && FormattedTextHelper.isValidInput(value);
    }
    private static boolean hasControl(String value) { return value.codePoints().anyMatch(Character::isISOControl); }

    public CompletableFuture<Outcome> edit(ClientWaypointSelection selection, int expectedRevision, WaypointPatch patch) {
        Outcome failure = unavailable(selection, Action.EDIT);
        if (failure != null) return CompletableFuture.completedFuture(failure);
        if (selection.waypointIdentifier() == null) return CompletableFuture.completedFuture(Outcome.of(Status.REJECTED));
        if (selection.source() == ClientWaypointSource.LOCAL) {
            try {
                Outcome result = profiles.mutate(selection, files -> {
                    var edited = files.updateWaypoint(EditTarget.waypoint(selection.dimensionName(), selection.listIdentifier(), selection.waypointIdentifier()),
                            expectedRevision, patch, ignored -> {});
                    return new Outcome(edited.status() == EditResultStatus.SUCCESS ? Status.SAVED : Status.REJECTED, edited.status(), null);
                });
                return CompletableFuture.completedFuture(result);
            } catch (IOException exception) { return CompletableFuture.completedFuture(Outcome.of(Status.PERSISTENCE_FAILED)); }
            catch (IllegalStateException exception) { return CompletableFuture.completedFuture(Outcome.of(Status.OBSOLETE)); }
        }
        long id = REQUEST_IDS.incrementAndGet();
        var completion = new CompletableFuture<Outcome>();
        pending.put(id, new Pending(selection, completion));
        collections.trackEdit(id, selection);
        if (!transport.edit(new WaypointEditRequestMessage(selection.source().serverScope(), id, selection.dimensionName(),
                selection.listIdentifier(), selection.waypointIdentifier(), expectedRevision, patch))) {
            pending.remove(id);
            collections.forgetEdit(id, selection);
            completion.complete(Outcome.of(Status.SEND_FAILED));
        }
        return completion;
    }
    public boolean acceptResult(WaypointEditResultMessage result, long receivedGeneration) {
        Pending request = pending.get(result.requestId());
        if (request == null || !collections.acceptResult(result, receivedGeneration)) return false;
        pending.remove(result.requestId());
        request.completion().complete(new Outcome(result.status() == EditResultStatus.SUCCESS ? Status.SAVED : Status.REJECTED, result.status(), null));
        return true;
    }
    /** Called on the client tick, including after disconnect, so stale futures release their forms. */
    public void tick() {
        for (var entry : List.copyOf(pending.entrySet())) {
            if (!valid(entry.getValue().selection())) cancel(entry.getValue().completion(), Status.OBSOLETE);
        }
    }
    public void cancel(CompletableFuture<Outcome> completion, Status reason) {
        for (var entry : List.copyOf(pending.entrySet())) if (entry.getValue().completion() == completion) {
            pending.remove(entry.getKey());
            collections.forgetEdit(entry.getKey(), entry.getValue().selection());
        }
        completion.complete(Outcome.of(reason));
    }
    public Outcome remove(ClientWaypointSelection selection) {
        Outcome failure = unavailable(selection, Action.REMOVE);
        if (failure != null) return failure;
        SimpleWaypoint waypoint = waypoint(selection);
        if (waypoint == null) return Outcome.of(Status.REJECTED);
        if (selection.source() != ClientWaypointSource.LOCAL) return send(selection,
                StringCommandBuilder.removeCmd(selection.dimensionName(), selection.listIdentifier(), waypoint), Status.AWAITING_SERVER);
        try {
            return profiles.mutate(selection, files -> Outcome.of(files.removeWaypoint(selection.dimensionName(), selection.listIdentifier(),
                    selection.waypointIdentifier(), ignored -> {}).status() == WaypointFilesManagerCore.RemoveWaypointStatus.REMOVED ? Status.SAVED : Status.REJECTED));
        } catch (IOException exception) { return Outcome.of(Status.PERSISTENCE_FAILED); }
        catch (IllegalStateException exception) { return Outcome.of(Status.OBSOLETE); }
    }
    public Outcome renameList(ClientWaypointSelection selection, String identifier) {
        if (!validName(identifier)) return Outcome.of(Status.REJECTED);
        return editList(selection, "identifier", identifier, new WaypointListPatch(PatchField.set(identifier), PatchField.unchanged()));
    }

    /** Null clears the display override; an empty string deliberately hides the list name. */
    public Outcome setListDisplayName(ClientWaypointSelection selection, @Nullable String displayName) {
        if (displayName != null && !validText(displayName, FormattedTextHelper.MAX_NAME_LENGTH)) return Outcome.of(Status.REJECTED);
        return editList(selection, "display-name", displayName,
                new WaypointListPatch(PatchField.unchanged(), displayName == null ? PatchField.clear() : PatchField.set(displayName)));
    }

    private Outcome editList(ClientWaypointSelection selection, String property, @Nullable String value, WaypointListPatch patch) {
        Outcome failure = unavailable(selection, Action.EDIT);
        if (failure != null) return failure;
        WaypointList list = list(selection);
        if (list == null) return Outcome.of(Status.REJECTED);
        if (selection.source() != ClientWaypointSource.LOCAL) {
            String command = value == null ? StringCommandBuilder.editListClearCmd(selection.dimensionName(), selection.listIdentifier(), property)
                    : "/wp edit list " + selection.dimensionName() + " " + StringCommandBuilder.escapeArgument(selection.listIdentifier())
                    + " set " + property + " " + StringCommandBuilder.escapeArgument(value);
            return send(selection, command, Status.AWAITING_SERVER);
        }
        try {
            return profiles.mutate(selection, files -> {
                var result = files.updateWaypointList(EditTarget.list(selection.dimensionName(), selection.listIdentifier()), list.getSyncNum(), patch, ignored -> {});
                return new Outcome(result.status() == EditResultStatus.SUCCESS ? Status.SAVED : Status.REJECTED, result.status(), null);
            });
        } catch (IOException exception) { return Outcome.of(Status.PERSISTENCE_FAILED); }
        catch (IllegalStateException exception) { return Outcome.of(Status.OBSOLETE); }
    }

    public Outcome removeList(ClientWaypointSelection selection) {
        Outcome failure = unavailable(selection, Action.REMOVE);
        if (failure != null) return failure;
        WaypointList list = list(selection);
        if (list == null || !list.simpleWaypoints().isEmpty()) return Outcome.of(Status.REJECTED);
        if (selection.source() != ClientWaypointSource.LOCAL) return send(selection,
                "/wp remove " + selection.dimensionName() + " " + StringCommandBuilder.escapeArgument(selection.listIdentifier()), Status.AWAITING_SERVER);
        try {
            return profiles.mutate(selection, files -> Outcome.of(files.removeWaypointList(selection.dimensionName(), selection.listIdentifier(), ignored -> {}).status()
                    == WaypointFilesManagerCore.RemoveWaypointListStatus.REMOVED ? Status.SAVED : Status.REJECTED));
        } catch (IOException exception) { return Outcome.of(Status.PERSISTENCE_FAILED); }
        catch (IllegalStateException exception) { return Outcome.of(Status.OBSOLETE); }
    }

    public Outcome teleport(ClientWaypointSelection selection) {
        Outcome failure = unavailable(selection, Action.TELEPORT);
        if (failure != null) return failure;
        if (!transport.connected()) return Outcome.of(Status.DISCONNECTED);
        SimpleWaypoint target = waypoint(selection);
        if (target == null) return Outcome.of(Status.REJECTED);
        if (selection.source() != ClientWaypointSource.LOCAL) return send(selection,
                StringCommandBuilder.tpCmd(selection.dimensionName(), selection.listIdentifier(), selection.waypointIdentifier()), Status.COMMAND_SUBMITTED);
        String template = profiles.active().teleportCommand();
        if (!selection.dimensionName().equals(transport.currentDimension()) && !template.contains("{dimension}")) return Outcome.rejected(Problem.DIMENSION_REQUIRED);
        try {
            String command = LocalTeleportCommand.expand(template, selection.dimensionName(), target);
            return sendCommand(command, Status.COMMAND_SUBMITTED);
        } catch (IllegalArgumentException exception) { return Outcome.rejected(Problem.INVALID_TEMPLATE); }
    }
    private Outcome send(ClientWaypointSelection selection, String command, Status success) {
        return sendCommand(StringCommandBuilder.inScope(selection.source().serverScope(), command).substring(1), success);
    }
    private Outcome sendCommand(String command, Status success) {
        if (command.length() > transport.commandLimit()) return Outcome.rejected(Problem.COMMAND_TOO_LONG);
        if (command.isBlank() || hasControl(command)) return Outcome.of(Status.REJECTED);
        return Outcome.of(transport.command(command) ? success : Status.SEND_FAILED);
    }
}
