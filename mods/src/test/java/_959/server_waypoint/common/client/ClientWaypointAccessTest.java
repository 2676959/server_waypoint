package _959.server_waypoint.common.client;

import _959.server_waypoint.common.client.local.*;
import _959.server_waypoint.core.*;
import _959.server_waypoint.core.edit.*;
import _959.server_waypoint.core.network.message.*;
import _959.server_waypoint.core.waypoint.*;
import _959.server_waypoint.util.NamespacedId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientWaypointAccessTest {
    @TempDir Path directory;
    private ClientWaypointCollections collections;
    private LocalWaypointProfiles profiles;
    private ClientWaypointAccess access;
    private final Wire wire = new Wire();
    private final UUID account = UUID.randomUUID();
    private static final String DIM = "minecraft:overworld";

    @BeforeEach void setup() throws Exception {
        collections = new ClientWaypointCollections(new WaypointFilesManagerCore(directory.resolve("shared")));
        collections.beginConnection(UUID.randomUUID(), "example.org");
        collections.bindServer(directory, 1);
        profiles = new LocalWaypointProfiles(directory, collections);
        profiles.joinMultiplayer(account, "example.org");
        collections.markSnapshotApplied(ClientWaypointSource.SHARED);
        collections.markSnapshotApplied(ClientWaypointSource.PRIVATE);
        access = new ClientWaypointAccess(collections, profiles, wire);
    }
    private ClientWaypointSelection selection(ClientWaypointSource source, String name) {
        return collections.selection(source, DIM, "Homes", name);
    }
    private SimpleWaypoint waypoint() {
        return new SimpleWaypoint("Base", "<gold>Home", "H", new WaypointPos(-12,64,30), 0x123456, 90, false,
                List.of("mine", "home"), "<blue>description", NamespacedId.parse("minecraft:diamond"));
    }
    private SimpleWaypoint get(ClientWaypointSource source, String name) {
        return access.waypoint(selection(source, name));
    }
    private WaypointPatch rename(String name) {
        return new WaypointPatch(PatchField.set(name), PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged());
    }
    @Test void localMutationsPersistOnlyTheirCollectionWithoutNetwork() throws Exception {
        for (var source : List.of(ClientWaypointSource.SHARED, ClientWaypointSource.PRIVATE))
            collections.files(source).addWaypoint(DIM, "Homes", waypoint(), ignored -> {});
        assertEquals(ClientWaypointAccess.Status.SAVED, access.add(selection(ClientWaypointSource.LOCAL,null), waypoint()).status());
        int revision = access.list(selection(ClientWaypointSource.LOCAL,"Base")).getSyncNum();
        assertEquals(ClientWaypointAccess.Status.SAVED, access.edit(selection(ClientWaypointSource.LOCAL,"Base"), revision, rename("Changed")).join().status());
        assertNull(get(ClientWaypointSource.LOCAL,"Base"));
        assertNotNull(get(ClientWaypointSource.LOCAL,"Changed"));
        assertEquals(ClientWaypointAccess.Status.SAVED, access.remove(selection(ClientWaypointSource.LOCAL,"Changed")).status());
        assertNotNull(get(ClientWaypointSource.SHARED,"Base"));
        assertNotNull(get(ClientWaypointSource.PRIVATE,"Base"));
        assertTrue(wire.commands.isEmpty()); assertTrue(wire.edits.isEmpty());
        profiles.leave(); profiles.joinMultiplayer(account, "example.org");
        assertTrue(access.list(selection(ClientWaypointSource.LOCAL,null)).simpleWaypoints().isEmpty());
    }
    @Test void serverEditsWaitForMatchingAuthoritativeResultsAndNeverChangeCacheOptimistically() {
        for (var source : List.of(ClientWaypointSource.SHARED, ClientWaypointSource.PRIVATE)) {
            collections.files(source).addWaypoint(DIM,"Homes",waypoint(), ignored -> {});
            var pending = access.edit(selection(source,"Base"), 1, rename("Changed"));
            assertFalse(pending.isDone()); assertNotNull(get(source,"Base")); assertNull(get(source,"Changed"));
            var sent = wire.edits.get(wire.edits.size()-1);
            assertEquals(source.serverScope(),sent.scope());
            var wrong = new WaypointEditResultMessage(sent.scope(), sent.requestId(), EditResultStatus.SUCCESS, DIM, "Other", "Base", null,1);
            assertFalse(access.acceptResult(wrong,collections.generation())); assertFalse(pending.isDone());
            var result = new WaypointEditResultMessage(sent.scope(), sent.requestId(), EditResultStatus.PERMISSION_DENIED,DIM,"Homes","Base",null,1);
            assertTrue(access.acceptResult(result,collections.generation()));
            assertEquals(EditResultStatus.PERMISSION_DENIED,pending.join().editStatus()); assertNotNull(get(source,"Base"));
        }
    }
    @Test void commandsRetainSourceAndCompleteCopiedMetadata() {
        assertEquals(ClientWaypointAccess.Status.AWAITING_SERVER,access.add(selection(ClientWaypointSource.PRIVATE,null),waypoint()).status());
        assertTrue(wire.commands.get(0).startsWith("wp private add minecraft:overworld Homes -12 64 30 Base H"));
        assertTrue(wire.commands.get(0).contains("display_name \"<gold>Home\""));
        collections.files(ClientWaypointSource.PRIVATE).addWaypoint(DIM,"Homes",waypoint(),ignored -> {});
        assertEquals(ClientWaypointAccess.Status.AWAITING_SERVER,access.remove(selection(ClientWaypointSource.PRIVATE,"Base")).status());
        assertEquals("wp private remove minecraft:overworld Homes Base",wire.commands.get(1));
        assertNotNull(get(ClientWaypointSource.PRIVATE,"Base"));
    }
    @Test void copyDraftIsDetachedAndInvalidationKeepsDraftButRejectsSubmission() throws Exception {
        collections.files(ClientWaypointSource.SHARED).addWaypoint(DIM,"Homes",waypoint(),ignored -> {});
        var draft = access.copyDraft(selection(ClientWaypointSource.SHARED,"Base"),selection(ClientWaypointSource.LOCAL,null));
        assertNull(get(ClientWaypointSource.LOCAL,"Base"));
        var copied = draft.waypoint(); assertNotSame(get(ClientWaypointSource.SHARED,"Base"),copied);
        assertEquals("<gold>Home",copied.displayName()); assertEquals("H",copied.initials());
        assertEquals(new WaypointPos(-12,64,30),copied.pos()); assertEquals(90,copied.yaw());
        assertEquals(0x123456,copied.rgb()); assertFalse(copied.global());
        assertEquals(List.of("mine","home"),copied.keywords()); assertEquals("<blue>description",copied.description());
        assertEquals(NamespacedId.parse("minecraft:diamond"),copied.icon());
        var next=profiles.create("Other"); profiles.select(next.id());
        assertEquals(ClientWaypointAccess.Status.OBSOLETE,access.add(draft.destination(),draft.waypoint()).status());
        assertEquals("Base",draft.waypoint().name()); assertNull(get(ClientWaypointSource.LOCAL,"Base"));
    }
    @Test void conflictsMetadataAndPermissionDenialsLeaveDestinationUnchanged() {
        var local=selection(ClientWaypointSource.LOCAL,null);
        assertEquals(ClientWaypointAccess.Status.SAVED,access.add(local,waypoint()).status());
        assertEquals(ClientWaypointAccess.Status.REJECTED,access.add(local,waypoint()).status());
        var invalid=new SimpleWaypoint("Bad","Bad","B",new WaypointPos(0,0,0),0,0,true,List.of("duplicate","DUPLICATE"),"");
        assertEquals(ClientWaypointAccess.Status.REJECTED,access.add(local,invalid).status());
        wire.allowed=false;
        assertEquals(ClientWaypointAccess.Status.DENIED,access.add(selection(ClientWaypointSource.PRIVATE,null),waypoint()).status());
        assertTrue(wire.commands.isEmpty()); assertEquals(1,access.list(local).simpleWaypoints().size());
    }
    @Test void obsoleteConnectionsRejectPendingEditsAndLateResults() {
        collections.files(ClientWaypointSource.SHARED).addWaypoint(DIM,"Homes",waypoint(),ignored -> {});
        var pending=access.edit(selection(ClientWaypointSource.SHARED,"Base"),1,rename("Changed"));
        var sent=wire.edits.get(0); long generation=collections.generation();
        collections.serverUnavailable(); access.tick();
        assertEquals(ClientWaypointAccess.Status.OBSOLETE,pending.join().status());
        assertFalse(access.acceptResult(new WaypointEditResultMessage(sent.scope(),sent.requestId(),EditResultStatus.SUCCESS,DIM,"Homes","Base",null,2),generation));
    }
    @Test void teleportRevalidatesTemplateDimensionConnectionAndLengthAndReportsOnlySubmission() throws Exception {
        access.add(selection(ClientWaypointSource.LOCAL,null),waypoint());
        var target=selection(ClientWaypointSource.LOCAL,"Base");
        profiles.setTeleportCommand("/tp @s {x} {y} {z}"); wire.dimension="minecraft:the_nether";
        assertEquals(ClientWaypointAccess.Status.REJECTED,access.teleport(target).status());
        assertEquals(ClientWaypointAccess.Problem.DIMENSION_REQUIRED,access.teleport(target).problem());
        profiles.setTeleportCommand(LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND);
        wire.limit=10; assertEquals(ClientWaypointAccess.Status.REJECTED,access.teleport(target).status());
        assertEquals(ClientWaypointAccess.Problem.COMMAND_TOO_LONG,access.teleport(target).problem());
        wire.limit=32767; wire.connected=false;
        assertEquals(ClientWaypointAccess.Status.DISCONNECTED,access.teleport(target).status());
        wire.connected=true;
        assertEquals(ClientWaypointAccess.Status.COMMAND_SUBMITTED,access.teleport(target).status());
        assertEquals("execute in minecraft:overworld run tp @s -12 64 30 90 ~",wire.commands.get(0));
    }
    @Test void failedPersistenceIsNotSuccessAndCanBeRetriedBySavingTheProfile() throws Exception {
        var target = selection(ClientWaypointSource.LOCAL, null);
        Path files = collections.files(ClientWaypointSource.LOCAL).getWaypointFilesDir();
        Files.delete(files);
        Files.writeString(files, "blocked");
        assertEquals(ClientWaypointAccess.Status.PERSISTENCE_FAILED, access.add(target, waypoint()).status());
        assertTrue(wire.commands.isEmpty()); assertTrue(wire.edits.isEmpty());
        Files.delete(files); Files.createDirectories(files); profiles.save();
        profiles.leave(); profiles.joinMultiplayer(account, "example.org");
        assertNotNull(get(ClientWaypointSource.LOCAL,"Base"));
    }
    @Test void copyDraftDoesNotFollowSourceEditsAndRechecksDestinationPermissions() {
        collections.files(ClientWaypointSource.SHARED).addWaypoint(DIM,"Homes",waypoint(),ignored -> {});
        var source = selection(ClientWaypointSource.SHARED,"Base");
        var draft = access.copyDraft(source, selection(ClientWaypointSource.PRIVATE,null));
        collections.files(ClientWaypointSource.SHARED).updateWaypoint(EditTarget.waypoint(DIM,"Homes","Base"),null,rename("Renamed"),ignored -> {});
        assertEquals("Base",draft.waypoint().name());
        wire.allowed=false;
        assertEquals(ClientWaypointAccess.Status.DENIED, access.add(draft.destination(),draft.waypoint()).status());
        assertNull(get(ClientWaypointSource.PRIVATE,"Base")); assertNotNull(get(ClientWaypointSource.SHARED,"Renamed"));
    }
    @Test void successfulEditStillUsesOnlyItsMatchingResultAndSendFailureDoesNotRemainPending() {
        var target=selection(ClientWaypointSource.PRIVATE,"Base");
        collections.files(ClientWaypointSource.PRIVATE).addWaypoint(DIM,"Homes",waypoint(),ignored -> {});
        var pending=access.edit(target,1,rename("Renamed"));
        var sent=wire.edits.get(0);
        assertFalse(access.acceptResult(new WaypointEditResultMessage(ClientWaypointSource.SHARED.serverScope(),sent.requestId(),EditResultStatus.SUCCESS,DIM,"Homes","Base",null,2),collections.generation()));
        assertFalse(pending.isDone());
        assertTrue(access.acceptResult(new WaypointEditResultMessage(sent.scope(),sent.requestId(),EditResultStatus.SUCCESS,DIM,"Homes","Base",null,2),collections.generation()));
        assertEquals(ClientWaypointAccess.Status.SAVED,pending.join().status());
        wire.sent=false;
        assertEquals(ClientWaypointAccess.Status.SEND_FAILED,access.edit(target,1,rename("Again")).join().status());
        var failed=wire.edits.get(1);
        assertFalse(access.acceptResult(new WaypointEditResultMessage(failed.scope(),failed.requestId(),EditResultStatus.SUCCESS,DIM,"Homes","Base",null,2),collections.generation()));
    }
    @Test void listEditsAndRemovalStayWithinTheirCapturedSource() {
        for(var source:ClientWaypointSource.values()) collections.files(source).addWaypointList(DIM,"Homes",ignored -> {});
        var local=selection(ClientWaypointSource.LOCAL,null);
        assertEquals(ClientWaypointAccess.Status.SAVED,access.renameList(local,"Renamed").status());
        assertNull(access.list(local));
        var renamed=local.at(DIM,"Renamed",null);
        assertEquals(ClientWaypointAccess.Status.SAVED,access.setListDisplayName(renamed,"<gold>New").status());
        assertEquals("<gold>New",access.list(renamed).displayName());
        assertEquals(ClientWaypointAccess.Status.SAVED,access.removeList(renamed).status());
        assertNotNull(access.list(selection(ClientWaypointSource.SHARED,null)));
        assertNotNull(access.list(selection(ClientWaypointSource.PRIVATE,null)));
        assertEquals(ClientWaypointAccess.Status.AWAITING_SERVER,access.renameList(selection(ClientWaypointSource.PRIVATE,null),"ServerName").status());
        assertEquals("wp private edit list minecraft:overworld Homes set identifier ServerName",wire.commands.get(0));
        assertNotNull(access.list(selection(ClientWaypointSource.PRIVATE,null)));
    }
    @Test void copyUsesExistingIdentifierRulesInsteadOfDisplayTextLimits() {
        String identifier="x".repeat(300);
        var waypoint=new SimpleWaypoint(identifier,"X",new WaypointPos(1,2,3),0,0,true);
        assertEquals(ClientWaypointAccess.Status.SAVED,access.add(selection(ClientWaypointSource.LOCAL,null),waypoint).status());
        assertNotNull(get(ClientWaypointSource.LOCAL,identifier));
    }
    @Test void dimensionChoicesUseWorldAndCapturedStoreWithoutReadingOtherCollections() {
        collections.files(ClientWaypointSource.LOCAL).addWaypoint("example:moon","Homes",waypoint(),ignored -> {});
        collections.files(ClientWaypointSource.SHARED).addWaypoint("example:shared","Homes",waypoint(),ignored -> {});
        assertEquals(Set.of(DIM,"minecraft:the_nether","example:moon"),new HashSet<>(access.dimensions(selection(ClientWaypointSource.LOCAL,null))));
        assertTrue(wire.commands.isEmpty()); assertTrue(wire.edits.isEmpty());
    }
    @Test void submittingACopyRechecksSourceReadPermissionAndConnectionGeneration() throws Exception {
        collections.files(ClientWaypointSource.PRIVATE).addWaypoint(DIM,"Homes",waypoint(),ignored -> {});
        var draft=access.copyDraft(selection(ClientWaypointSource.PRIVATE,"Base"),selection(ClientWaypointSource.LOCAL,null));
        wire.allowed=false;
        assertEquals(ClientWaypointAccess.Status.DENIED,access.addCopy(draft).status());
        assertNull(get(ClientWaypointSource.LOCAL,"Base"));
        wire.allowed=true;
        collections.beginConnection(account,"another.org"); profiles.joinMultiplayer(account,"another.org");
        assertEquals(ClientWaypointAccess.Status.OBSOLETE,access.addCopy(draft).status());
        assertEquals("<gold>Home",draft.waypoint().displayName());
        assertNull(get(ClientWaypointSource.LOCAL,"Base"));
    }
    @Test void runtimeDispatchDeliversSharedAndPrivateResultsToTheAccessBoundary() throws Exception {
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");
        var singleton=unsafeType.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        var client=(WaypointClientMod)unsafeType.getMethod("allocateInstance",Class.class).invoke(singleton.get(null),WaypointClientMod.class);
        for(var entry:Map.of("collections",(Object)collections,"access",access).entrySet()) {
            var field=WaypointClientMod.class.getDeclaredField(entry.getKey()); field.setAccessible(true); field.set(client,entry.getValue());
        }
        var dispatch=WaypointClientMod.class.getDeclaredMethod("applyChunkedMessage",_959.server_waypoint.core.network.ChunkedMessage.class);
        dispatch.setAccessible(true);
        for(var source:List.of(ClientWaypointSource.SHARED,ClientWaypointSource.PRIVATE)) {
            var pending=access.edit(selection(source,"Base"),1,rename("Changed"));
            var sent=wire.edits.get(wire.edits.size()-1);
            dispatch.invoke(client,new WaypointEditResultMessage(sent.scope(),sent.requestId(),EditResultStatus.SUCCESS,DIM,"Homes","Base",null,2));
            assertTrue(pending.isDone());
            assertEquals(ClientWaypointAccess.Status.SAVED,pending.join().status());
        }
    }
    @Test void commandLengthBoundaryIsCheckedAtSubmissionAndInvalidTemplatesHaveAReason() throws Exception {
        access.add(selection(ClientWaypointSource.LOCAL,null),waypoint());
        var target=selection(ClientWaypointSource.LOCAL,"Base");
        profiles.setTeleportCommand("tp {unknown}");
        assertEquals(ClientWaypointAccess.Problem.INVALID_TEMPLATE,access.teleport(target).problem());
        wire.limit=256;
        profiles.setTeleportCommand("x".repeat(256));
        assertEquals(ClientWaypointAccess.Status.COMMAND_SUBMITTED,access.teleport(target).status());
        profiles.setTeleportCommand("x".repeat(257));
        assertEquals(ClientWaypointAccess.Problem.COMMAND_TOO_LONG,access.teleport(target).problem());
        assertEquals(1,wire.commands.size());
    }
    private static class Wire implements ClientWaypointAccess.Transport {
        final List<String> commands=new ArrayList<>(); final List<WaypointEditRequestMessage> edits=new ArrayList<>();
        boolean connected=true, allowed=true, sent=true; int limit=32767; String dimension=DIM;
        public boolean connected(){return connected;}
        public String currentDimension(){return dimension;}
        public int commandLimit(){return limit;}
        public List<String> dimensions(){return List.of(DIM,"minecraft:the_nether");}
        public boolean allowed(ClientWaypointSource source,ClientWaypointAccess.Action action){return allowed;}
        public boolean command(String command){commands.add(command);return true;}
        public boolean edit(WaypointEditRequestMessage message){edits.add(message);return sent;}
    }
}
