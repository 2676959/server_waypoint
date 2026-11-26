package _959.server_waypoint.perf.paper;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bootstrap-visible hooks: JDK types only, shared across Paper's isolated class loader. */
public final class CommandTreeHooks {
    private static final Map<Object, Map<String, Object>> ACTIVE = new IdentityHashMap<>();
    private static final List<Map<String, Object>> FINISHED = new ArrayList<>();
    private static volatile boolean transformed;
    private static boolean enabled;

    public static void transformed(boolean value) {
        transformed = value;
    }

    public static synchronized void enable() {
        if (!transformed || !ACTIVE.isEmpty()) {
            throw new IllegalStateException("Native send boundaries unavailable or still active");
        }
        FINISHED.clear();
        enabled = true;
    }

    public static synchronized void packet(Object packet, Object player) {
        Map<String, Object> sample = ACTIVE.get(player);
        if (sample != null) {
            sample.put("packet", packet);
        }
    }

    public static synchronized void observe(Object player, String phase) {
        long now = System.nanoTime();
        if (!enabled) {
            return;
        }
        if (phase.equals("dispatchStart")) {
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("player", player);
            if (ACTIVE.containsKey(player)) {
                sample.put("error", "overlapping command-tree send");
            }
            ACTIVE.put(player, sample);
        }
        Map<String, Object> sample = ACTIVE.get(player);
        if (sample == null) {
            return;
        }
        sample.put(phase, now);
        sample.put(phase + "Thread", Thread.currentThread().getName());
        if (phase.equals("handoff")) {
            if (FINISHED.size() < 32) {
                FINISHED.add(sample);
            }
            ACTIVE.remove(player);
        }
    }

    public static synchronized List<Map<String, Object>> snapshot() {
        return FINISHED.stream().map(LinkedHashMap::new).map(value -> (Map<String, Object>) value).toList();
    }
}
