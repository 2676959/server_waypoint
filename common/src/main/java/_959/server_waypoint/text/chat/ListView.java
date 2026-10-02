package _959.server_waypoint.text.chat;

/** How a dimension's lists are shown. DEFAULT lets the screen choose. */
public enum ListView {
    DEFAULT(""),
    LISTS("lists"),
    TREE("tree"),
    FLAT("flat");

    private final String id;

    ListView(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}
