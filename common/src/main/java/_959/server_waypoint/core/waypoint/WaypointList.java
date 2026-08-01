package _959.server_waypoint.core.waypoint;

import _959.server_waypoint.core.WaypointFileManager.MutationAuthority;
import _959.server_waypoint.core.network.WaypointListSyncIdentifier;
import _959.server_waypoint.util.GsonUtils;
import com.google.gson.ExclusionStrategy;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public class WaypointList {
    public static volatile boolean excludeClientOnlyFields = true;
    public static final int REMOVE_LIST = -2;
    public static final int SERVER_N = 1;
    @Expose @SerializedName("list_name") private String name;
    @Expose @SerializedName("display_name") private String displayName;
    @Expose @SerializedName("n") private int syncNum;
    @Expose @SerializedName("waypoints") private List<SimpleWaypoint> simpleWaypoints;
    private transient MutationAuthority mutationAuthority;
    private transient boolean attachedToOwner;
    // client only fields and methods
    //? if !paper {
    @Expose private boolean show = true;
    @Expose private boolean expand = true;

    public synchronized boolean isShow() {
        return this.show;
    }

    public synchronized void setShow(boolean show) {
        this.show = show;
    }

    public synchronized boolean isExpand() {
        return this.expand;
    }

    public synchronized void setExpand(boolean expand) {
        this.expand = expand;
    }

    /**
     * should only use as client when syncing from server
     * */
    public synchronized SimpleWaypoint addFromRemoteServer(
            MutationAuthority authority,
            SimpleWaypoint waypoint,
            int syncId
    ) {
        this.requireMutationAuthority(authority);
        SimpleWaypoint waypointFound = this.getWaypointByName(waypoint.name());
        if (waypointFound != null) {
            waypointFound.copyFrom(waypoint);
        } else {
            waypointFound = new SimpleWaypoint(waypoint);
            this.simpleWaypoints.add(waypointFound);
        }
        this.syncNum = syncId;
        return waypointFound;
    }

    /**
     * Replaces one client-side waypoint and its revision as a single operation.
     */
    public synchronized @Nullable SimpleWaypoint updateFromRemoteServer(
            MutationAuthority authority,
            String oldName,
            SimpleWaypoint waypoint,
            int syncId
    ) {
        this.requireMutationAuthority(authority);
        SimpleWaypoint waypointFound = this.getWaypointByName(oldName);
        if (waypointFound == null) {
            return null;
        }
        waypointFound.copyFrom(waypoint);
        this.syncNum = syncId;
        return waypointFound;
    }

    /**
     * should only use as client when syncing from server
     * */
    public synchronized @Nullable SimpleWaypoint removeFromRemoteServer(
            MutationAuthority authority,
            String waypointName,
            int syncId
    ) {
        this.requireMutationAuthority(authority);
        SimpleWaypoint waypoint = this.getWaypointByName(waypointName);
        if (waypoint == null) {
            return null;
        }
        this.simpleWaypoints.remove(waypoint);
        this.syncNum = syncId;
        return waypoint;
    }

    //?}

    public WaypointList() {
        this.simpleWaypoints = new ArrayList<>();
    }

    public WaypointList(String name, int syncNum, List<SimpleWaypoint> simpleWaypoints) {
        this(name, name, syncNum, simpleWaypoints);
    }

    public WaypointList(
            String name,
            String displayName,
            int syncNum,
            List<SimpleWaypoint> simpleWaypoints
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.displayName = normalizeDisplayName(name, displayName);
        this.syncNum = syncNum;
        this.simpleWaypoints = new ArrayList<>(simpleWaypoints.size());
        for (SimpleWaypoint waypoint : simpleWaypoints) {
            this.simpleWaypoints.add(new SimpleWaypoint(waypoint));
        }
    }

    public synchronized @Nullable SimpleWaypoint getWaypointByName(String name) {
        for (SimpleWaypoint waypoint : this.simpleWaypoints) {
            if (waypoint.name().equals(name)) {
                return waypoint;
            }
        }
        return null;
    }

    public synchronized String name() {
        return this.name;
    }

    public synchronized String displayName() {
        return this.displayName == null ? this.name : this.displayName;
    }

    public synchronized int getSyncNum() {
        return this.syncNum;
    }

    public synchronized int size() {
        return this.simpleWaypoints.size();
    }

    public synchronized boolean isEmpty() {
        return this.simpleWaypoints.isEmpty();
    }

    public synchronized @Unmodifiable List<@NotNull SimpleWaypoint> simpleWaypoints() {
        return Collections.unmodifiableList(new ArrayList<>(this.simpleWaypoints));
    }

    synchronized List<WaypointSnapshot> snapshotWaypoints() {
        List<WaypointSnapshot> snapshots = new ArrayList<>(this.simpleWaypoints.size());
        for (SimpleWaypoint waypoint : this.simpleWaypoints) {
            snapshots.add(new WaypointSnapshot(waypoint, new SimpleWaypoint(waypoint)));
        }
        return List.copyOf(snapshots);
    }

    public synchronized WaypointListSyncIdentifier getIdentifier() {
        return new WaypointListSyncIdentifier(this.name, this.syncNum);
    }

    /**
     * Attaches this list to its model owner. The authority is deliberately retained
     * after detachment so a stale escaped list cannot appoint a new owner and mutate itself.
     */
    public synchronized void attachOwner(MutationAuthority authority) {
        Objects.requireNonNull(authority, "authority");
        if (this.mutationAuthority != null && this.mutationAuthority != authority) {
            throw new IllegalStateException("Waypoint list already belongs to another owner");
        }
        this.mutationAuthority = authority;
        this.attachedToOwner = true;
    }

    public synchronized void detachOwner(MutationAuthority authority) {
        this.requireMutationAuthority(authority);
        this.attachedToOwner = false;
    }

    public synchronized ServerAddResult addByServerIfAbsent(
            MutationAuthority authority,
            SimpleWaypoint waypoint
    ) {
        this.requireMutationAuthority(authority);
        SimpleWaypoint existingWaypoint = this.getWaypointByName(waypoint.name());
        if (existingWaypoint != null) {
            return new ServerAddResult(
                    ServerAddStatus.DUPLICATE,
                    existingWaypoint,
                    new SimpleWaypoint(existingWaypoint),
                    this.syncNum
            );
        }
        SimpleWaypoint ownedWaypoint = new SimpleWaypoint(waypoint);
        this.simpleWaypoints.add(ownedWaypoint);
        this.syncNum++;
        return new ServerAddResult(
                ServerAddStatus.ADDED,
                ownedWaypoint,
                new SimpleWaypoint(ownedWaypoint),
                this.syncNum
        );
    }

    public synchronized ServerUpdateResult updateByServer(
            MutationAuthority authority,
            String oldName,
            String newName,
            String displayName,
            String initials,
            WaypointPos waypointPos,
            int rgb,
            int yaw,
            boolean global,
            List<String> keywords,
            String description
    ) {
        this.requireMutationAuthority(authority);
        if (this.simpleWaypoints.isEmpty()) {
            return new ServerUpdateResult(ServerUpdateStatus.EMPTY, null, null, null, this.syncNum);
        }
        SimpleWaypoint waypoint = this.getWaypointByName(oldName);
        if (waypoint == null) {
            return new ServerUpdateResult(ServerUpdateStatus.NOT_FOUND, null, null, null, this.syncNum);
        }
        SimpleWaypoint waypointUsingNewName = this.getWaypointByName(newName);
        if (!oldName.equals(newName) && waypointUsingNewName != null) {
            return new ServerUpdateResult(
                    ServerUpdateStatus.NAME_USED,
                    waypoint,
                    new SimpleWaypoint(waypoint),
                    new SimpleWaypoint(waypoint),
                    this.syncNum
            );
        }
        if (waypoint.compareProperties(newName, displayName, initials, waypointPos, rgb, yaw, global, keywords, description)) {
            SimpleWaypoint snapshot = new SimpleWaypoint(waypoint);
            return new ServerUpdateResult(
                    ServerUpdateStatus.IDENTICAL,
                    waypoint,
                    snapshot,
                    snapshot,
                    this.syncNum
            );
        }
        SimpleWaypoint before = new SimpleWaypoint(waypoint);
        waypoint.updateProperties(newName, displayName, initials, waypointPos, rgb, yaw, global, keywords, description);
        this.syncNum++;
        return new ServerUpdateResult(
                ServerUpdateStatus.UPDATED,
                waypoint,
                before,
                new SimpleWaypoint(waypoint),
                this.syncNum
        );
    }

    public synchronized ServerRemoveResult removeByServer(
            MutationAuthority authority,
            String waypointName
    ) {
        this.requireMutationAuthority(authority);
        if (this.simpleWaypoints.isEmpty()) {
            return new ServerRemoveResult(ServerRemoveStatus.EMPTY, null, null, this.syncNum);
        }
        SimpleWaypoint waypoint = this.getWaypointByName(waypointName);
        if (waypoint == null) {
            return new ServerRemoveResult(ServerRemoveStatus.NOT_FOUND, null, null, this.syncNum);
        }
        SimpleWaypoint snapshot = new SimpleWaypoint(waypoint);
        this.simpleWaypoints.remove(waypoint);
        this.syncNum++;
        return new ServerRemoveResult(ServerRemoveStatus.REMOVED, waypoint, snapshot, this.syncNum);
    }

    private void requireMutationAuthority(MutationAuthority authority) {
        if (!this.attachedToOwner || this.mutationAuthority != authority) {
            throw new IllegalStateException(
                    "Waypoint mutations must be performed through the owning manager"
            );
        }
    }

    @SuppressWarnings("unused")
    public synchronized WaypointList deepCopy() {
        WaypointList newList = new WaypointList(
                this.name,
                this.displayName(),
                this.syncNum,
                this.simpleWaypoints
        );
        //? if !paper {
        newList.show = this.show;
        newList.expand = this.expand;
        //?}
        return newList;
    }

    public synchronized String toString() {
        return "WaypointList{name='" + this.name + "', displayName='" + this.displayName() + "', simpleWaypoints=" + this.simpleWaypoints + "}";
    }

    public static WaypointList build(String name, int syncId) {
        return new WaypointList(name, syncId, new ArrayList<>());
    }

    public static WaypointList build(String name, String displayName, int syncId) {
        return new WaypointList(name, displayName, syncId, new ArrayList<>());
    }

    public static WaypointList buildByServer(String name) {
        return new WaypointList(name, SERVER_N, new ArrayList<>());
    }

    public static WaypointList buildByServer(String name, String displayName) {
        return new WaypointList(name, displayName, SERVER_N, new ArrayList<>());
    }

    private static String normalizeDisplayName(String name, String displayName) {
        Objects.requireNonNull(displayName, "displayName");
        return name.equals(displayName) ? null : displayName;
    }

    public static ExclusionStrategy exclusionStrategy(boolean excludeClientFields) {
        return new GsonUtils.DynamicExclusionStrategy(
                () -> excludeClientFields,
                "show",
                "expand"
        );
    }

    public enum ServerAddStatus {
        ADDED,
        DUPLICATE
    }

    public record ServerAddResult(
            ServerAddStatus status,
            SimpleWaypoint waypoint,
            SimpleWaypoint waypointSnapshot,
            int syncNum
    ) {
    }

    public enum ServerUpdateStatus {
        UPDATED,
        NAME_USED,
        IDENTICAL,
        EMPTY,
        NOT_FOUND
    }

    public record ServerUpdateResult(
            ServerUpdateStatus status,
            @Nullable SimpleWaypoint waypoint,
            @Nullable SimpleWaypoint beforeSnapshot,
            @Nullable SimpleWaypoint afterSnapshot,
            int syncNum
    ) {
    }

    public enum ServerRemoveStatus {
        REMOVED,
        EMPTY,
        NOT_FOUND
    }

    public record ServerRemoveResult(
            ServerRemoveStatus status,
            @Nullable SimpleWaypoint waypoint,
            @Nullable SimpleWaypoint waypointSnapshot,
            int syncNum
    ) {
    }

    record WaypointSnapshot(SimpleWaypoint liveWaypoint, SimpleWaypoint snapshot) {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WaypointList other = (WaypointList) o;
        return this.name().equals(other.name());
    }

    @Override
    public synchronized int hashCode() {
        return this.name.hashCode();
    }
}
