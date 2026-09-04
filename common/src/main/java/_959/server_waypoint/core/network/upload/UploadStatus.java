package _959.server_waypoint.core.network.upload;

/** A client-side result of handling an upload request. */
public enum UploadStatus {
    SUCCESS,
    XAERO_NOT_INSTALLED,
    XAERO_NOT_READY,
    FAILED,
    VOXELMAP_NOT_INSTALLED,
    VOXELMAP_NOT_READY
}
