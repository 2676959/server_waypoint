package _959.server_waypoint.listener;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class PendingPlayerReconciliationsTest {
    private final PendingPlayerReconciliations pending = new PendingPlayerReconciliations();
    private final UUID player = UUID.randomUUID();
    private final QueuedScheduler scheduler = new QueuedScheduler();

    @Test
    void fiveRequestsScheduleOneScanOfTheLatestStateAndMergeRestorationWithOr() {
        AtomicInteger inventoryState = new AtomicInteger();
        List<Integer> scannedStates = new ArrayList<>();
        List<Boolean> restoration = new ArrayList<>();
        for (int state = 1; state <= 5; state++) {
            inventoryState.set(state);
            this.request(state == 3, restore -> {
                scannedStates.add(inventoryState.get());
                restoration.add(restore);
            });
        }

        assertEquals(1, this.scheduler.tasks.size());
        assertTrue(scannedStates.isEmpty());
        this.scheduler.runNext();
        assertEquals(List.of(5), scannedStates);
        assertEquals(List.of(true), restoration);
        assertTrue(this.scheduler.tasks.isEmpty());
    }

    @Test
    void validationOnlyRequestsDoNotEnableRestoration() {
        List<Boolean> restoration = new ArrayList<>();
        this.request(false, restoration::add);
        this.request(false, restoration::add);
        this.scheduler.runNext();
        assertEquals(List.of(false), restoration);
    }

    @Test
    void requestsDuringExecutionScheduleOneSubsequentPassWithTheirOwnFlag() {
        List<Boolean> restoration = new ArrayList<>();
        Consumer<Boolean> scan = restore -> {
            restoration.add(restore);
            if (restoration.size() == 1) {
                for (int i = 0; i < 5; i++) {
                    this.request(false, ignored -> fail("must use the existing player pass"));
                }
            }
        };
        this.request(true, scan);
        this.scheduler.runNext();
        assertEquals(List.of(true), restoration);
        assertEquals(1, this.scheduler.tasks.size());
        this.scheduler.runNext();
        assertEquals(List.of(true, false), restoration);
        assertTrue(this.scheduler.tasks.isEmpty());
    }

    @Test
    void restorationRequestedDuringValidationIsPreservedForTheFollowUp() {
        List<Boolean> restoration = new ArrayList<>();
        this.request(false, restore -> {
            restoration.add(restore);
            if (restoration.size() == 1) {
                this.request(false, restoration::add);
                this.request(true, restoration::add);
                this.request(false, restoration::add);
            }
        });
        this.scheduler.runNext();
        this.scheduler.runNext();
        assertEquals(List.of(false, true), restoration);
        assertTrue(this.scheduler.tasks.isEmpty());
    }

    @Test
    void playersHaveIndependentPendingPassesAndCompletedEntriesCanBeRequestedAgain() {
        List<Boolean> first = new ArrayList<>();
        List<Boolean> second = new ArrayList<>();
        this.request(false, first::add);
        this.pending.request(UUID.randomUUID(), true, this.scheduler::schedule, second::add);
        this.scheduler.runNext();
        this.request(true, first::add);
        this.scheduler.runNext();
        this.scheduler.runNext();
        assertEquals(List.of(false, true), first);
        assertEquals(List.of(true), second);
        assertTrue(this.scheduler.tasks.isEmpty());
    }

    @Test
    void disconnectInvalidatesOldTaskAndItsRetirementCannotRemoveAReconnectedPlayer() {
        this.request(true, ignored -> fail("disconnected player scanned"));
        Task stale = this.scheduler.tasks.remove();
        this.pending.remove(this.player);
        List<Boolean> restoration = new ArrayList<>();
        this.request(false, restoration::add);
        stale.retired().run();
        stale.action().run();
        this.request(true, restoration::add);
        assertEquals(1, this.scheduler.tasks.size());
        this.scheduler.runNext();
        assertEquals(List.of(true), restoration);
    }

    @Test
    void retirementInvalidatesThePassAndAllowsAFreshRequest() {
        this.request(true, ignored -> fail("retired task scanned"));
        Task retired = this.scheduler.tasks.remove();
        retired.retired().run();
        retired.action().run();
        List<Boolean> restoration = new ArrayList<>();
        this.request(false, restoration::add);
        this.scheduler.runNext();
        assertEquals(List.of(false), restoration);
    }

    @Test
    void schedulerRejectionDoesNotLeaveAnEntryBlockingLaterRequests() {
        this.pending.request(this.player, true, (action, retired) -> false,
                ignored -> fail("rejected task scanned"));
        List<Boolean> restoration = new ArrayList<>();
        this.request(false, restoration::add);
        this.scheduler.runNext();
        assertEquals(List.of(false), restoration);
    }

    @Test
    void schedulerExceptionDoesNotLeaveAnEntryBlockingLaterRequests() {
        assertThrows(IllegalStateException.class, () -> this.pending.request(
                this.player, true, (action, retired) -> {
                    throw new IllegalStateException("plugin disabled");
                }, ignored -> fail("unscheduled task scanned")));
        List<Boolean> restoration = new ArrayList<>();
        this.request(false, restoration::add);
        this.scheduler.runNext();
        assertEquals(List.of(false), restoration);
    }

    @Test
    void failedScanDoesNotLeaveAnEntryBlockingLaterRequests() {
        this.request(true, ignored -> {
            throw new IllegalStateException("validation failed");
        });
        assertThrows(IllegalStateException.class, this.scheduler::runNext);
        List<Boolean> restoration = new ArrayList<>();
        this.request(false, restoration::add);
        this.scheduler.runNext();
        assertEquals(List.of(false), restoration);
    }

    @Test
    void requestsDuringAFailedScanStillReceiveOneFollowUp() {
        List<Boolean> restoration = new ArrayList<>();
        this.request(false, restore -> {
            restoration.add(restore);
            if (restoration.size() == 1) {
                this.request(true, restoration::add);
                throw new IllegalStateException("validation failed after another request");
            }
        });
        assertThrows(IllegalStateException.class, this.scheduler::runNext);
        assertEquals(1, this.scheduler.tasks.size());
        this.scheduler.runNext();
        assertEquals(List.of(false, true), restoration);
        assertTrue(this.scheduler.tasks.isEmpty());
    }

    @Test
    void concurrentRequestsBeforeExecutionScheduleOnePass() throws Exception {
        List<Boolean> restoration = new ArrayList<>();
        var executor = Executors.newFixedThreadPool(4);
        try {
            var requests = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 20; i++) {
                boolean restore = i == 10;
                requests.add(executor.submit(() -> this.request(restore, restoration::add)));
            }
            for (var request : requests) {
                request.get(5, TimeUnit.SECONDS);
            }
            assertEquals(1, this.scheduler.tasks.size());
            this.scheduler.runNext();
            assertEquals(List.of(true), restoration);
            assertTrue(this.scheduler.tasks.isEmpty());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void closeInvalidatesAllPlayersAndRejectsFutureRequests() {
        this.request(true, ignored -> fail("closed queue scanned"));
        this.pending.request(UUID.randomUUID(), true, this.scheduler::schedule,
                ignored -> fail("closed queue scanned another player"));
        this.pending.close();
        this.request(true, ignored -> fail("closed queue accepted a request"));
        assertEquals(2, this.scheduler.tasks.size());
        this.scheduler.runNext();
        this.scheduler.runNext();
        assertTrue(this.scheduler.tasks.isEmpty());
    }

    @Test
    void removalDuringExecutionPreventsItsRequestedFollowUp() {
        AtomicInteger scans = new AtomicInteger();
        this.request(false, restore -> {
            scans.incrementAndGet();
            this.request(true, ignored -> fail("removed follow-up scanned"));
            this.pending.remove(this.player);
        });
        this.scheduler.runNext();
        assertEquals(1, scans.get());
        assertTrue(this.scheduler.tasks.isEmpty());
    }

    @Test
    void closeDuringExecutionPreventsItsRequestedFollowUp() {
        AtomicInteger scans = new AtomicInteger();
        this.request(false, restore -> {
            scans.incrementAndGet();
            this.request(true, ignored -> fail("closed follow-up scanned"));
            this.pending.close();
        });
        this.scheduler.runNext();
        assertEquals(1, scans.get());
        assertTrue(this.scheduler.tasks.isEmpty());
    }

    @Test
    void concurrentRequestsDuringExecutionMergeIntoOneFollowUp() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Boolean> restoration = new ArrayList<>();
        this.request(false, restore -> {
            restoration.add(restore);
            if (restoration.size() == 1) {
                entered.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }
        });
        Task first = this.scheduler.tasks.remove();
        var executor = Executors.newFixedThreadPool(4);
        try {
            var running = executor.submit(first.action());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var requests = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 20; i++) {
                boolean restore = i == 10;
                requests.add(executor.submit(() -> this.request(restore,
                        ignored -> fail("must retain the original player pass"))));
            }
            for (var request : requests) {
                request.get(5, TimeUnit.SECONDS);
            }
            release.countDown();
            running.get(5, TimeUnit.SECONDS);
            assertEquals(1, this.scheduler.tasks.size());
            this.scheduler.runNext();
            assertEquals(List.of(false, true), restoration);
            assertTrue(this.scheduler.tasks.isEmpty());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void request(boolean restoreMissing, Consumer<Boolean> scan) {
        this.pending.request(this.player, restoreMissing, this.scheduler::schedule, scan);
    }

    private record Task(Runnable action, Runnable retired) {
    }

    private static final class QueuedScheduler {
        private final Queue<Task> tasks = new ConcurrentLinkedQueue<>();

        private boolean schedule(Runnable action, Runnable retired) {
            this.tasks.add(new Task(action, retired));
            return true;
        }

        private void runNext() {
            this.tasks.remove().action().run();
        }
    }
}
