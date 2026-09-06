package _959.server_waypoint.crossserver;

/** Semantic states only; enum ordinals are not wire IDs. */
public enum RemoteCatalogState {
    /** A current authorized snapshot, which may explicitly be empty. */
    AVAILABLE,
    /** A retained snapshot whose freshness is no longer assured. */
    STALE,
    /** No current source is available; any retained snapshot remains stale. */
    UNAVAILABLE,
    /** Access is denied; retained entries must not be exposed to the denied reader. */
    UNAUTHORIZED
}
