package _959.server_waypoint.util;

public class Pair<L, R> {
    protected final L left;
    protected final R right;

    public Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public L left() {
        return left;
    }

    public R right() {
        return right;
    }

    @Override
    public String toString() {
        return "(%s, %s)".formatted(left, right);
    }
}
