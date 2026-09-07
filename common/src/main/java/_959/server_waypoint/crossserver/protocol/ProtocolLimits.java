package _959.server_waypoint.crossserver.protocol;

/** Per-operation limits. Negotiation/configuration may lower these v1 hard maxima, never raise them. */
public record ProtocolLimits(int frameBytes, int catalogBytes, int chunkBytes, int stringBytes,
                             int collectionEntries, int objects, int allocationBytes) {
    public static final ProtocolLimits DEFAULT = new ProtocolLimits(
            1_048_576, 1_048_576, 262_144, 65_536, 16_384, 65_536, 8_388_608);

    public ProtocolLimits {
        bounded(frameBytes, 1_048_576);
        bounded(catalogBytes, 1_048_576);
        bounded(chunkBytes, 262_144);
        bounded(stringBytes, 65_536);
        bounded(collectionEntries, 16_384);
        bounded(objects, 65_536);
        bounded(allocationBytes, 8_388_608);
    }

    private static void bounded(int value, int maximum) {
        if (value <= 0 || value > maximum) {
            throw new IllegalArgumentException("Limit outside v1 range");
        }
    }
}
