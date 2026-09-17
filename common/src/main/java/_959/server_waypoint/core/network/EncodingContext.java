package _959.server_waypoint.core.network;

/** Tracks variable-size allocations claimed while encoding one enclosing message. */
public final class EncodingContext {
    private final int byteBudget;
    private int claimedBytes;

    public EncodingContext(int byteBudget) {
        if (byteBudget < 0) {
            throw new IllegalArgumentException("Encoding byte budget cannot be negative");
        }
        this.byteBudget = byteBudget;
    }

    public void claimBytes(int count) {
        if (count < 0 || count > this.byteBudget - this.claimedBytes) {
            throw new MessageEncodingException(
                    "Message encoding exceeds its " + this.byteBudget + "-byte resource budget"
            );
        }
        this.claimedBytes += count;
    }

    public int claimedBytes() {
        return this.claimedBytes;
    }
}
