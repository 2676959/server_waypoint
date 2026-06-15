package _959.server_waypoint.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThreadDispatchingTest {
    @Test
    void runOnTargetThreadRunsTaskImmediately() {
        Queue<Runnable> queuedTasks = new ArrayDeque<>();
        int[] runs = {0};

        ThreadDispatching.runOnTargetThread(() -> true, queuedTasks::add, () -> runs[0]++);

        assertEquals(1, runs[0]);
        assertEquals(0, queuedTasks.size());
    }

    @Test
    void runOnTargetThreadEnqueuesTaskWhenCalledFromOtherThread() {
        Queue<Runnable> queuedTasks = new ArrayDeque<>();
        int[] runs = {0};

        ThreadDispatching.runOnTargetThread(() -> false, queuedTasks::add, () -> runs[0]++);

        assertEquals(0, runs[0]);
        assertEquals(1, queuedTasks.size());
        queuedTasks.remove().run();
        assertEquals(1, runs[0]);
    }
}
