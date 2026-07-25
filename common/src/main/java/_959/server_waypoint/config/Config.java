package _959.server_waypoint.config;

import _959.server_waypoint.navigation.NavigationMethod;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class Config {
    public static final int MIN_PAGE_LIMIT = 1;
    public static final int DEFAULT_PAGE_LIMIT = 10;
    public static final int MAX_PAGE_LIMIT = 100;

    int serverId = ThreadLocalRandom.current().nextInt();
    int defaultPageLimit = DEFAULT_PAGE_LIMIT;
    @SerializedName(value = "defaultNavigationMethods")
    @JsonAdapter(value = NavigationMethodSetJsonAdapter.class, nullSafe = false)
    Set<NavigationMethod> defaultNavigationMethods = NavigationMethod.builtInDefaultMethods();
    CommandPermission CommandPermission = new CommandPermission();
    Features Features = new Features();

    public Config() {
    }

    public CommandPermission CommandPermission() {
        return this.CommandPermission;
    }

    public Features Features() {
        return this.Features;
    }

    public int defaultPageLimit() {
        return Math.max(MIN_PAGE_LIMIT, Math.min(MAX_PAGE_LIMIT, this.defaultPageLimit));
    }

    public Set<NavigationMethod> defaultNavigationMethods() {
        return this.defaultNavigationMethods;
    }

    public int getServerId() {
        return serverId;
    }

    @Override
    public String toString() {
        return "Config{serverId=" + serverId + ", defaultPageLimit=" + defaultPageLimit()
                + ", defaultNavigationMethods=" + defaultNavigationMethods() +
                ", CommandPermission=" + CommandPermission + ", Features=" + Features + "}";
    }
}
