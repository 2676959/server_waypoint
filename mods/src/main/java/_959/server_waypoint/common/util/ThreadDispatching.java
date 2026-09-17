package _959.server_waypoint.common.util;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ThreadDispatching {
    private ThreadDispatching() {
    }

    public static void runOnTargetThread(BooleanSupplier isOnTargetThread, Consumer<Runnable> executor, Runnable task) {
        if (isOnTargetThread.getAsBoolean()) {
            task.run();
        } else {
            executor.accept(task);
        }
    }
}
