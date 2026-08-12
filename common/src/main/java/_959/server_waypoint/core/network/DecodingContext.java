package _959.server_waypoint.core.network;

/** Tracks variable-size memory and object claims while decoding one message. */
public final class DecodingContext {
    private final int byteBudget;
    private final int objectBudget;
    private int claimedBytes;
    private int claimedObjects;

    public DecodingContext(int byteBudget, int objectBudget) {
        if (byteBudget < 0 || objectBudget < 0) {
            throw new IllegalArgumentException("Decoding budgets cannot be negative");
        }
        this.byteBudget = byteBudget;
        this.objectBudget = objectBudget;
    }

    public void claimBytes(int count) {
        if (count < 0 || count > this.byteBudget - this.claimedBytes) {
            throw new IllegalArgumentException(
                    "Message decoding exceeds its " + this.byteBudget + "-byte resource budget"
            );
        }
        this.claimedBytes += count;
    }

    public void claimObject() {
        if (this.claimedObjects >= this.objectBudget) {
            throw new IllegalArgumentException(
                    "Message decoding exceeds its " + this.objectBudget + "-object resource budget"
            );
        }
        this.claimedObjects++;
    }

    public int claimedBytes() {
        return this.claimedBytes;
    }

    public int claimedObjects() {
        return this.claimedObjects;
    }
}
