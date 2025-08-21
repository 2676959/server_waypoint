package _959.server_waypoint.core.waypoint;

public class SimpleWaypoint {
    private String name;
    private String initials;
    private WaypointPos pos;
    private int colorIdx;
    private int yaw;
    private boolean global;
    private static final String SEPARATOR = ":";

    public SimpleWaypoint(String name, String initials, WaypointPos pos, int colorIdx, int yaw, boolean global) {
        this.name = name;
        this.initials = initials;
        this.pos = pos;
        this.colorIdx = colorIdx;
        this.yaw = convertYaw(yaw);
        this.global = global;
    }

    public SimpleWaypoint(String name, String initials, int x, int y, int z, int colorIdx, int yaw, boolean global) {
        this.name = name;
        this.initials = initials;
        this.pos = new WaypointPos(x, y, z);
        this.colorIdx = colorIdx;
        this.yaw = convertYaw(yaw);
        this.global = global;
    }

    private int convertYaw(int yaw) {
        boolean isNegative = yaw < 0;
        int r = Math.abs(yaw) % 360;
        r = (r <= 180) ? r : r - 360;
        r = isNegative ? -r : r;
        return r;
    }

    public String name() {
        return this.name;
    }

    public String initials() {
        return this.initials;
    }

    public WaypointPos pos() {
        return this.pos;
    }

    public int colorIdx() {
        return this.colorIdx;
    }

    public int yaw() {
        return this.yaw;
    }

    public boolean global() {
        return this.global;
    }

    public SimpleWaypoint setName(String name) {
        this.name = name;
        return this;
    }

    public void setInitials(String initials) {
        this.initials = initials;
    }

    public void setPos(WaypointPos pos) {
        this.pos = pos;
    }

    public void setColorIdx(int colorIdx) {
        this.colorIdx = colorIdx;
    }

    public void setYaw(int yaw) {
        this.yaw = convertYaw(yaw);
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    public String toString() {
        return "SimpleWaypoint{name='" + this.name + "', initials='" + this.initials + "', pos=" + this.pos + ", colorIdx=" + this.colorIdx + ", yaw=" + this.yaw + ", global=" + this.global + "}";
    }

    public String toSaveString() {
        return this.name() + SEPARATOR + this.initials() + SEPARATOR + this.pos().x() + SEPARATOR + this.pos().y() + SEPARATOR + this.pos().z() + SEPARATOR + this.colorIdx() + SEPARATOR + this.yaw() + SEPARATOR + this.global();
    }

    public static SimpleWaypoint fromString(String waypointString) throws NumberFormatException {
        String[] args = waypointString.split(SEPARATOR);
        int colorIdx = Integer.parseInt(args[5]);
        return new SimpleWaypoint(args[0], args[1], new WaypointPos(Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4])), colorIdx >= 0 && colorIdx <= 15 ? colorIdx : 15, Integer.parseInt(args[6]), Boolean.parseBoolean(args[7]));
    }

    public boolean compareProperties(String initials, WaypointPos pos, int colorIdx, int yaw, boolean global) {
        return this.initials.equals(initials) && this.pos.equals(pos) && this.colorIdx == colorIdx && this.yaw == convertYaw(yaw) && this.global == global;
    }
}
