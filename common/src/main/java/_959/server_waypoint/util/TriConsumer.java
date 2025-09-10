package _959.server_waypoint.util;

@FunctionalInterface
public interface TriConsumer<T, U, V> {
    void accept(T k, U v, V s);
}
