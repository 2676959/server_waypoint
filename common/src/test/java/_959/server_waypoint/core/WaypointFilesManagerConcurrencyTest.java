package _959.server_waypoint.core;

import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.IntStream;

import static _959.server_waypoint.core.WaypointFilesManagerCore.AddWaypointStatus.ADDED;
import static _959.server_waypoint.core.WaypointFilesManagerCore.AddWaypointStatus.DUPLICATE;
import static _959.server_waypoint.core.WaypointFilesManagerCore.RemoveWaypointStatus.LIST_EMPTY;
import static _959.server_waypoint.core.WaypointFilesManagerCore.RemoveWaypointStatus.REMOVED;
import static _959.server_waypoint.core.WaypointFilesManagerCore.UpdateWaypointStatus.NAME_USED;
import static _959.server_waypoint.core.WaypointFilesManagerCore.UpdateWaypointStatus.UPDATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class WaypointFilesManagerConcurrencyTest {
    private static final String DIMENSION = "minecraft:overworld";
    private static final String LIST = "shared";
    private static final int FUTURE_TIMEOUT_SECONDS = 20;

    @TempDir
    private Path tempDir;

    @Test
    void concurrentSameNameAddCommitsExactlyOnce() throws Exception {
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        int taskCount = 24;
        AtomicInteger callbackCount = new AtomicInteger();

        List<WaypointFilesManagerCore.AddWaypointResult> results = runConcurrently(
                taskCount,
                index -> {
                    AtomicReference<WaypointFilesManagerCore.AddWaypointResult> callbackResult =
                            new AtomicReference<>();
                    WaypointFilesManagerCore.AddWaypointResult returned = filesManager.addWaypoint(
                            DIMENSION,
                            LIST,
                            waypoint("same-name", index),
                            result -> {
                                assertNull(callbackResult.getAndSet(result));
                                callbackCount.incrementAndGet();
                            }
                    );
                    assertSame(returned, callbackResult.get());
                    return returned;
                }
        );

        assertEquals(taskCount, callbackCount.get());
        assertEquals(1, count(results, result -> result.status() == ADDED));
        assertEquals(taskCount - 1, count(results, result -> result.status() == DUPLICATE));
        assertEquals(1, count(results, WaypointFilesManagerCore.AddWaypointResult::dimensionCreated));
        assertEquals(1, count(results, WaypointFilesManagerCore.AddWaypointResult::listCreated));

        WaypointFileManager fileManager = filesManager.getWaypointFileManager(DIMENSION);
        assertNotNull(fileManager);
        WaypointList waypointList = fileManager.getWaypointListByName(LIST);
        assertNotNull(waypointList);
        assertEquals(1, waypointList.size());
        assertEquals(WaypointList.SERVER_N + 1, waypointList.getSyncNum());

        SimpleWaypoint canonicalWaypoint = waypointList.getWaypointByName("same-name");
        assertNotNull(canonicalWaypoint);
        for (WaypointFilesManagerCore.AddWaypointResult result : results) {
            assertSame(fileManager, result.fileManager());
            assertSame(waypointList, result.waypointList());
            assertSame(canonicalWaypoint, result.waypoint());
            assertEquals(WaypointList.SERVER_N + 1, result.syncNum());
        }
    }

    @Test
    void concurrentUniqueAddsLoseNothingAndReturnExactSyncRange() throws Exception {
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        int taskCount = 8;
        int additionsPerTask = 40;
        int totalAdditions = taskCount * additionsPerTask;
        AtomicInteger callbackCount = new AtomicInteger();

        List<List<WaypointFilesManagerCore.AddWaypointResult>> resultsByTask = runConcurrently(
                taskCount,
                taskIndex -> {
                    List<WaypointFilesManagerCore.AddWaypointResult> results =
                            new ArrayList<>(additionsPerTask);
                    for (int additionIndex = 0; additionIndex < additionsPerTask; additionIndex++) {
                        int ordinal = taskIndex * additionsPerTask + additionIndex;
                        results.add(filesManager.addWaypoint(
                                DIMENSION,
                                LIST,
                                waypoint("waypoint-" + ordinal, ordinal),
                                ignored -> callbackCount.incrementAndGet()
                        ));
                    }
                    return results;
                }
        );
        List<WaypointFilesManagerCore.AddWaypointResult> results = resultsByTask.stream()
                .flatMap(List::stream)
                .toList();

        assertEquals(totalAdditions, callbackCount.get());
        assertEquals(totalAdditions, count(results, result -> result.status() == ADDED));
        assertEquals(1, count(results, WaypointFilesManagerCore.AddWaypointResult::dimensionCreated));
        assertEquals(1, count(results, WaypointFilesManagerCore.AddWaypointResult::listCreated));
        assertEquals(
                IntStream.rangeClosed(
                        WaypointList.SERVER_N + 1,
                        WaypointList.SERVER_N + totalAdditions
                ).boxed().collect(java.util.stream.Collectors.toSet()),
                results.stream()
                        .map(WaypointFilesManagerCore.AddWaypointResult::syncNum)
                        .collect(java.util.stream.Collectors.toSet())
        );

        WaypointFileManager fileManager = filesManager.getWaypointFileManager(DIMENSION);
        assertNotNull(fileManager);
        WaypointList waypointList = fileManager.getWaypointListByName(LIST);
        assertNotNull(waypointList);
        assertEquals(totalAdditions, waypointList.size());
        assertEquals(WaypointList.SERVER_N + totalAdditions, waypointList.getSyncNum());
        assertEquals(expectedNames(totalAdditions), waypointNames(waypointList));
    }

    @Test
    void insertingOneWaypointIntoTwoDimensionsCreatesIndependentOwnedValues() {
        String otherDimension = "minecraft:the_nether";
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        SimpleWaypoint suppliedWaypoint = waypoint("shared-input", 0);

        WaypointFilesManagerCore.AddWaypointResult overworldResult = filesManager.addWaypoint(
                DIMENSION,
                LIST,
                suppliedWaypoint,
                ignored -> {
                }
        );
        WaypointFilesManagerCore.AddWaypointResult netherResult = filesManager.addWaypoint(
                otherDimension,
                LIST,
                suppliedWaypoint,
                ignored -> {
                }
        );

        assertNotSame(suppliedWaypoint, overworldResult.waypoint());
        assertNotSame(suppliedWaypoint, netherResult.waypoint());
        assertNotSame(overworldResult.waypoint(), netherResult.waypoint());

        filesManager.updateWaypointProperties(
                DIMENSION,
                LIST,
                "shared-input",
                "changed-only-in-overworld",
                "C",
                new WaypointPos(10, 80, 10),
                0x123456,
                45,
                true,
                List.of(),
                "",
                ignored -> {
                }
        );

        WaypointList netherList = filesManager.getWaypointFileManager(otherDimension)
                .getWaypointListByName(LIST);
        assertNotNull(netherList.getWaypointByName("shared-input"));
        assertNull(netherList.getWaypointByName("changed-only-in-overworld"));
        assertEquals(WaypointList.SERVER_N + 1, netherList.getSyncNum());
    }

    @Test
    void concurrentRenamesToOneNameCommitExactlyOnce() throws Exception {
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        int taskCount = 24;
        for (int index = 0; index < taskCount; index++) {
            filesManager.addWaypoint(
                    DIMENSION,
                    LIST,
                    waypoint("original-" + index, index),
                    ignored -> {
                    }
            );
        }
        WaypointList waypointList = filesManager.getWaypointFileManager(DIMENSION)
                .getWaypointListByName(LIST);
        int syncNumBeforeRename = waypointList.getSyncNum();
        AtomicInteger callbackCount = new AtomicInteger();

        List<WaypointFilesManagerCore.UpdateWaypointResult> results = runConcurrently(
                taskCount,
                index -> {
                    AtomicReference<WaypointFilesManagerCore.UpdateWaypointResult> callbackResult =
                            new AtomicReference<>();
                    WaypointFilesManagerCore.UpdateWaypointResult returned =
                            filesManager.updateWaypointProperties(
                                    DIMENSION,
                                    LIST,
                                    "original-" + index,
                                    "renamed",
                                    "R",
                                    new WaypointPos(index, 80, -index),
                                    0x336699,
                                    index,
                                    true,
                                    List.of(),
                                    "",
                                    result -> {
                                        assertNull(callbackResult.getAndSet(result));
                                        callbackCount.incrementAndGet();
                                    }
                            );
                    assertSame(returned, callbackResult.get());
                    return returned;
                }
        );

        assertEquals(taskCount, callbackCount.get());
        assertEquals(1, count(results, result -> result.status() == UPDATED));
        assertEquals(taskCount - 1, count(results, result -> result.status() == NAME_USED));
        assertEquals(syncNumBeforeRename + 1, waypointList.getSyncNum());
        assertEquals(taskCount, waypointList.size());
        assertEquals(
                1,
                waypointList.simpleWaypoints().stream()
                        .filter(waypoint -> waypoint.name().equals("renamed"))
                        .count()
        );
        assertTrue(results.stream().allMatch(result -> result.syncNum() == syncNumBeforeRename + 1));
    }

    @Test
    void concurrentRemoveCommitsExactlyOnce() throws Exception {
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        filesManager.addWaypoint(
                DIMENSION,
                LIST,
                waypoint("remove-me", 0),
                ignored -> {
                }
        );
        WaypointList waypointList = filesManager.getWaypointFileManager(DIMENSION)
                .getWaypointListByName(LIST);
        int syncNumBeforeRemove = waypointList.getSyncNum();
        int taskCount = 24;
        AtomicInteger callbackCount = new AtomicInteger();

        List<WaypointFilesManagerCore.RemoveWaypointResult> results = runConcurrently(
                taskCount,
                ignored -> {
                    AtomicReference<WaypointFilesManagerCore.RemoveWaypointResult> callbackResult =
                            new AtomicReference<>();
                    WaypointFilesManagerCore.RemoveWaypointResult returned =
                            filesManager.removeWaypoint(
                                    DIMENSION,
                                    LIST,
                                    "remove-me",
                                    result -> {
                                        assertNull(callbackResult.getAndSet(result));
                                        callbackCount.incrementAndGet();
                                    }
                            );
                    assertSame(returned, callbackResult.get());
                    return returned;
                }
        );

        assertEquals(taskCount, callbackCount.get());
        assertEquals(1, count(results, result -> result.status() == REMOVED));
        assertEquals(
                taskCount - 1,
                count(results, result -> result.status() == LIST_EMPTY)
        );
        assertEquals(0, waypointList.size());
        assertEquals(syncNumBeforeRemove + 1, waypointList.getSyncNum());
        assertTrue(results.stream().allMatch(result -> result.syncNum() == syncNumBeforeRemove + 1));
    }

    @Test
    void removedListRejectsServerMutationThroughEscapedReference() {
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        WaypointFilesManagerCore.AddWaypointListResult addResult = filesManager.addWaypointList(
                DIMENSION,
                LIST,
                ignored -> {
                }
        );
        WaypointList escapedList = addResult.waypointList();

        WaypointFilesManagerCore.RemoveWaypointListResult removeResult =
                filesManager.removeWaypointList(DIMENSION, LIST, ignored -> {
                });

        assertEquals(WaypointFilesManagerCore.RemoveWaypointListStatus.REMOVED, removeResult.status());
        assertSame(escapedList, removeResult.waypointList());
        assertThrows(
                IllegalStateException.class,
                () -> escapedList.addByServerIfAbsent(null, waypoint("detached", 0))
        );
        assertNull(filesManager.getWaypointFileManager(DIMENSION).getWaypointListByName(LIST));
    }

    @Test
    void callbacksFollowCommittedSyncOrderUnderContention() throws Exception {
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        int taskCount = 32;
        List<Integer> callbackSyncNums = Collections.synchronizedList(new ArrayList<>());

        List<WaypointFilesManagerCore.AddWaypointResult> results = runConcurrently(
                taskCount,
                index -> filesManager.addWaypoint(
                        DIMENSION,
                        LIST,
                        waypoint("ordered-" + index, index),
                        result -> callbackSyncNums.add(result.syncNum())
                )
        );

        assertEquals(taskCount, results.size());
        assertEquals(
                IntStream.rangeClosed(
                        WaypointList.SERVER_N + 1,
                        WaypointList.SERVER_N + taskCount
                ).boxed().toList(),
                callbackSyncNums
        );
    }

    @Test
    void topLevelCallbacksRunOnTheirExactInvokingThreadsUnderContention() throws Exception {
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        int taskCount = 8;
        CountDownLatch firstCallbackEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstCallback = new CountDownLatch(1);
        AtomicReferenceArray<Thread> invokingThreads = new AtomicReferenceArray<>(taskCount);
        AtomicReferenceArray<Thread> callbackThreads = new AtomicReferenceArray<>(taskCount);
        ExecutorService executor = newDaemonExecutor(taskCount);
        List<Future<WaypointFilesManagerCore.AddWaypointResult>> futures =
                new ArrayList<>(taskCount);

        try {
            futures.add(executor.submit(() -> {
                invokingThreads.set(0, Thread.currentThread());
                return filesManager.addWaypoint(
                        DIMENSION,
                        LIST,
                        waypoint("thread-affinity-0", 0),
                        ignored -> {
                            callbackThreads.set(0, Thread.currentThread());
                            firstCallbackEntered.countDown();
                            awaitStart(releaseFirstCallback);
                        }
                );
            }));
            assertTrue(
                    firstCallbackEntered.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "First callback did not start"
            );

            for (int index = 1; index < taskCount; index++) {
                int taskIndex = index;
                futures.add(executor.submit(() -> {
                    invokingThreads.set(taskIndex, Thread.currentThread());
                    return filesManager.addWaypoint(
                            DIMENSION,
                            LIST,
                            waypoint("thread-affinity-" + taskIndex, taskIndex),
                            ignored -> callbackThreads.set(taskIndex, Thread.currentThread())
                    );
                }));
            }
            awaitCondition(
                    () -> {
                        WaypointFileManager fileManager =
                                filesManager.getWaypointFileManager(DIMENSION);
                        if (fileManager == null) {
                            return false;
                        }
                        WaypointList waypointList = fileManager.getWaypointListByName(LIST);
                        return waypointList != null && waypointList.size() == taskCount;
                    },
                    "Contending mutations did not all commit"
            );
            releaseFirstCallback.countDown();

            for (int index = 0; index < taskCount; index++) {
                assertEquals(ADDED, awaitFuture(futures.get(index)).status());
                assertSame(
                        invokingThreads.get(index),
                        callbackThreads.get(index),
                        "Callback " + index + " ran on a different caller's thread"
                );
            }
        } finally {
            releaseFirstCallback.countDown();
            executor.shutdownNow();
            executor.awaitTermination(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void reentrantCallbackWaitsForEarlierCommittedCallbackInSameDimension() throws Exception {
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        CountDownLatch rootCallbackEntered = new CountDownLatch(1);
        CountDownLatch allowNestedMutation = new CountDownLatch(1);
        List<Integer> callbackSyncNums = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = newDaemonExecutor(2);

        try {
            Future<WaypointFilesManagerCore.AddWaypointResult> rootMutation =
                    executor.submit(() -> filesManager.addWaypoint(
                            DIMENSION,
                            LIST,
                            waypoint("root", 0),
                            rootResult -> {
                                callbackSyncNums.add(rootResult.syncNum());
                                rootCallbackEntered.countDown();
                                awaitStart(allowNestedMutation);
                                filesManager.addWaypoint(
                                        DIMENSION,
                                        LIST,
                                        waypoint("nested", 2),
                                        nestedResult -> callbackSyncNums.add(
                                                nestedResult.syncNum()
                                        )
                                );
                            }
                    ));
            assertTrue(
                    rootCallbackEntered.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Root callback did not start"
            );

            Future<WaypointFilesManagerCore.AddWaypointResult> interveningMutation =
                    executor.submit(() -> filesManager.addWaypoint(
                            DIMENSION,
                            LIST,
                            waypoint("intervening", 1),
                            result -> callbackSyncNums.add(result.syncNum())
                    ));
            awaitCondition(
                    () -> {
                        WaypointFileManager fileManager =
                                filesManager.getWaypointFileManager(DIMENSION);
                        WaypointList waypointList = fileManager == null
                                ? null
                                : fileManager.getWaypointListByName(LIST);
                        return waypointList != null && waypointList.size() == 2;
                    },
                    "Intervening mutation did not commit"
            );
            allowNestedMutation.countDown();

            assertEquals(ADDED, awaitFuture(rootMutation).status());
            assertEquals(ADDED, awaitFuture(interveningMutation).status());
            assertEquals(
                    List.of(
                            WaypointList.SERVER_N + 1,
                            WaypointList.SERVER_N + 2,
                            WaypointList.SERVER_N + 3
                    ),
                    callbackSyncNums
            );
        } finally {
            allowNestedMutation.countDown();
            executor.shutdownNow();
            executor.awaitTermination(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void deferredNestedCallbackFailurePropagatesToOutermostMutationCaller() throws Exception {
        String targetDimension = "minecraft:the_nether";
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        IllegalStateException expectedFailure =
                new IllegalStateException("nested callback failure");
        CountDownLatch targetCallbackEntered = new CountDownLatch(1);
        CountDownLatch releaseTargetCallback = new CountDownLatch(1);
        CountDownLatch nestedCallbackRan = new CountDownLatch(1);
        CountDownLatch nestedCallerCompleted = new CountDownLatch(1);
        AtomicReference<Throwable> immediateNestedFailure = new AtomicReference<>();
        AtomicBoolean nestedCallReturnedNormally = new AtomicBoolean();
        ExecutorService executor = newDaemonExecutor(2);

        try {
            Future<WaypointFilesManagerCore.AddWaypointResult> targetMutation =
                    executor.submit(() -> filesManager.addWaypoint(
                            targetDimension,
                            LIST,
                            waypoint("target-root", 0),
                            ignored -> {
                                targetCallbackEntered.countDown();
                                awaitStart(releaseTargetCallback);
                            }
                    ));
            assertTrue(
                    targetCallbackEntered.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Target callback did not start"
            );

            Future<WaypointFilesManagerCore.AddWaypointResult> sourceMutation =
                    executor.submit(() -> filesManager.addWaypoint(
                            DIMENSION,
                            LIST,
                            waypoint("source-root", 1),
                            ignored -> {
                                try {
                                    filesManager.addWaypoint(
                                            targetDimension,
                                            LIST,
                                            waypoint("nested-failure", 2),
                                            nestedResult -> {
                                                nestedCallbackRan.countDown();
                                                throw expectedFailure;
                                            }
                                    );
                                    nestedCallReturnedNormally.set(true);
                                } catch (Throwable failure) {
                                    immediateNestedFailure.set(failure);
                                } finally {
                                    nestedCallerCompleted.countDown();
                                }
                            }
                    ));
            awaitCondition(
                    () -> {
                        WaypointFileManager fileManager =
                                filesManager.getWaypointFileManager(targetDimension);
                        if (fileManager == null) {
                            return false;
                        }
                        WaypointList waypointList = fileManager.getWaypointListByName(LIST);
                        return waypointList != null
                                && waypointList.getWaypointByName("nested-failure") != null;
                    },
                    "Nested mutation did not commit"
            );
            releaseTargetCallback.countDown();

            assertTrue(
                    nestedCallerCompleted.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Nested mutation caller did not complete"
            );
            assertEquals(ADDED, awaitFuture(targetMutation).status());
            IllegalStateException propagatedFailure = assertThrows(
                    IllegalStateException.class,
                    () -> awaitFuture(sourceMutation)
            );
            assertTrue(
                    nestedCallbackRan.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Failing nested callback did not run"
            );
            assertTrue(
                    nestedCallReturnedNormally.get(),
                    "Deferred nested mutation did not return to let the outer callback release its turn"
            );
            assertNull(immediateNestedFailure.get());
            assertSame(expectedFailure, propagatedFailure);
        } finally {
            releaseTargetCallback.countDown();
            executor.shutdownNow();
            executor.awaitTermination(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void crossDimensionNestedCallbacksDoNotFormCallbackLockCycle() throws Exception {
        String otherDimension = "minecraft:the_nether";
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        CountDownLatch rootCallbacksEntered = new CountDownLatch(2);
        CountDownLatch allCallbacksCompleted = new CountDownLatch(4);
        AtomicBoolean dimensionACrossMutationStarted = new AtomicBoolean();
        AtomicBoolean dimensionBCrossMutationStarted = new AtomicBoolean();
        AtomicInteger rootCallbackCount = new AtomicInteger();
        AtomicInteger nestedCallbackCount = new AtomicInteger();

        List<WaypointFilesManagerCore.AddWaypointResult> rootResults = runConcurrently(
                2,
                taskIndex -> {
                    boolean mutateFromDimensionA = taskIndex == 0;
                    String rootDimension = mutateFromDimensionA ? DIMENSION : otherDimension;
                    String nestedDimension = mutateFromDimensionA ? otherDimension : DIMENSION;
                    AtomicBoolean crossMutationStarted = mutateFromDimensionA
                            ? dimensionACrossMutationStarted
                            : dimensionBCrossMutationStarted;
                    return filesManager.addWaypoint(
                            rootDimension,
                            LIST,
                            waypoint("root-" + taskIndex, taskIndex),
                            ignored -> {
                                rootCallbackCount.incrementAndGet();
                                allCallbacksCompleted.countDown();
                                rootCallbacksEntered.countDown();
                                awaitStart(rootCallbacksEntered);
                                if (crossMutationStarted.compareAndSet(false, true)) {
                                    filesManager.addWaypoint(
                                            nestedDimension,
                                            LIST,
                                            waypoint("nested-from-" + taskIndex, taskIndex + 2),
                                            nestedResult -> {
                                                nestedCallbackCount.incrementAndGet();
                                                allCallbacksCompleted.countDown();
                                            }
                                    );
                                }
                            }
                    );
                }
        );

        assertTrue(
                allCallbacksCompleted.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Nested callbacks did not all finish"
        );
        assertEquals(2, count(rootResults, result -> result.status() == ADDED));
        assertEquals(2, rootCallbackCount.get());
        assertEquals(2, nestedCallbackCount.get());
        assertTrue(dimensionACrossMutationStarted.get());
        assertTrue(dimensionBCrossMutationStarted.get());

        WaypointList dimensionAList = filesManager.getWaypointFileManager(DIMENSION)
                .getWaypointListByName(LIST);
        WaypointList dimensionBList = filesManager.getWaypointFileManager(otherDimension)
                .getWaypointListByName(LIST);
        assertEquals(Set.of("root-0", "nested-from-1"), waypointNames(dimensionAList));
        assertEquals(Set.of("root-1", "nested-from-0"), waypointNames(dimensionBList));
    }

    @Test
    void dimensionWaypointBufferRemainsStableAfterSourceMutation() {
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        filesManager.addWaypoint(
                DIMENSION,
                LIST,
                waypoint("first", 0),
                ignored -> {
                }
        );
        WaypointFileManager fileManager = filesManager.getWaypointFileManager(DIMENSION);
        DimensionWaypointBuffer captured = fileManager.toDimensionWaypoint();

        filesManager.updateWaypointProperties(
                DIMENSION,
                LIST,
                "first",
                "first-updated",
                "FU",
                new WaypointPos(100, 90, 100),
                0xAA5500,
                90,
                true,
                List.of(),
                "",
                ignored -> {
                }
        );
        filesManager.addWaypoint(
                DIMENSION,
                LIST,
                waypoint("second", 1),
                ignored -> {
                }
        );

        WaypointList currentList = fileManager.getWaypointListByName(LIST);
        assertEquals(2, currentList.size());
        assertEquals(WaypointList.SERVER_N + 3, currentList.getSyncNum());

        assertCapturedInitialState(captured);
        DimensionWaypointBuffer decoded =
                (DimensionWaypointBuffer) captured.decode(captured.encode());
        assertCapturedInitialState(decoded);
    }

    @Test
    void concurrentAddsAndSavesRemainCompleteAndReloadable() throws Exception {
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        int taskCount = 6;
        int additionsPerTask = 15;
        int totalAdditions = taskCount * additionsPerTask;
        AtomicInteger callbackCount = new AtomicInteger();

        runConcurrently(
                taskCount,
                taskIndex -> {
                    for (int additionIndex = 0; additionIndex < additionsPerTask; additionIndex++) {
                        int ordinal = taskIndex * additionsPerTask + additionIndex;
                        WaypointFilesManagerCore.AddWaypointResult result = filesManager.addWaypoint(
                                DIMENSION,
                                LIST,
                                waypoint("waypoint-" + ordinal, ordinal),
                                ignored -> callbackCount.incrementAndGet()
                        );
                        filesManager.saveWaypointFile(result.fileManager());
                    }
                    return null;
                }
        );

        assertEquals(totalAdditions, callbackCount.get());
        WaypointFileManager currentManager = filesManager.getWaypointFileManager(DIMENSION);
        assertNotNull(currentManager);
        assertTrue(Files.isRegularFile(currentManager.getDimensionFile()));

        WaypointFileManager reloaded = WaypointFileManager.buildFromDimensionName(
                this.tempDir,
                DIMENSION
        );
        reloaded.readDimension();
        WaypointList reloadedList = reloaded.getWaypointListByName(LIST);
        assertNotNull(reloadedList);
        assertEquals(totalAdditions, reloadedList.size());
        assertEquals(WaypointList.SERVER_N + totalAdditions, reloadedList.getSyncNum());
        assertEquals(expectedNames(totalAdditions), waypointNames(reloadedList));
    }

    @Test
    void mutationCallbackRejectsLifecycleTransitionWithoutDeadlocking() throws Exception {
        Path initialDirectory = this.tempDir.resolve("initial");
        Path reloadedDirectory = this.tempDir.resolve("reloaded");
        Files.createDirectories(reloadedDirectory);
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(initialDirectory);
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch callbackCompleted = new CountDownLatch(1);
        AtomicReference<IllegalStateException> rejection = new AtomicReference<>();
        ExecutorService executor = newDaemonExecutor(1);

        try {
            Future<WaypointFilesManagerCore.AddWaypointResult> mutation = executor.submit(
                    () -> filesManager.addWaypoint(
                            DIMENSION,
                            LIST,
                            waypoint("before-reload", 0),
                            ignored -> {
                                callbackEntered.countDown();
                                rejection.set(assertThrows(
                                        IllegalStateException.class,
                                        () -> filesManager.changeWaypointFilesDir(
                                                reloadedDirectory
                                        )
                                ));
                                callbackCompleted.countDown();
                            }
                    )
            );

            assertTrue(
                    callbackEntered.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Mutation callback did not start"
            );
            WaypointFilesManagerCore.AddWaypointResult result = awaitFuture(mutation);
            assertEquals(ADDED, result.status());
            assertTrue(
                    callbackCompleted.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Mutation callback did not finish"
            );
            assertEquals(
                    "Lifecycle transitions cannot run from waypoint mutation callbacks",
                    rejection.get().getMessage()
            );
            assertEquals(initialDirectory, filesManager.getWaypointFilesDir());
            assertNotNull(filesManager.getWaypointFileManager(DIMENSION));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void lifecyclePublicationWaitsForAllCommittedCallbacks() throws Exception {
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        CountDownLatch firstCallbackEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstCallback = new CountDownLatch(1);
        AtomicBoolean secondCallbackSawOriginalGeneration = new AtomicBoolean();
        AtomicReference<Thread> lifecycleThread = new AtomicReference<>();
        ExecutorService executor = newDaemonExecutor(3);

        try {
            Future<WaypointFilesManagerCore.AddWaypointResult> firstMutation =
                    executor.submit(() -> filesManager.addWaypoint(
                            DIMENSION,
                            LIST,
                            waypoint("before-lifecycle-0", 0),
                            ignored -> {
                                firstCallbackEntered.countDown();
                                awaitStart(releaseFirstCallback);
                            }
                    ));
            assertTrue(
                    firstCallbackEntered.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "First callback did not start"
            );

            Future<WaypointFilesManagerCore.AddWaypointResult> secondMutation =
                    executor.submit(() -> filesManager.addWaypoint(
                            DIMENSION,
                            LIST,
                            waypoint("before-lifecycle-1", 1),
                            result -> secondCallbackSawOriginalGeneration.set(
                                    filesManager.getWaypointFileManager(DIMENSION)
                                            == result.fileManager()
                            )
                    ));
            awaitCondition(
                    () -> {
                        WaypointFileManager fileManager =
                                filesManager.getWaypointFileManager(DIMENSION);
                        WaypointList waypointList = fileManager == null
                                ? null
                                : fileManager.getWaypointListByName(LIST);
                        return waypointList != null && waypointList.size() == 2;
                    },
                    "Second mutation did not commit"
            );

            Future<?> lifecyclePublication = executor.submit(() -> {
                lifecycleThread.set(Thread.currentThread());
                filesManager.clearWaypointFileManagers();
                return null;
            });
            awaitCondition(
                    () -> lifecyclePublication.isDone()
                            || isLockWaiting(lifecycleThread.get()),
                    "Lifecycle publication neither completed nor waited for callbacks"
            );
            assertFalse(
                    lifecyclePublication.isDone(),
                    "Lifecycle publication overtook committed callbacks"
            );
            assertNotNull(filesManager.getWaypointFileManager(DIMENSION));

            releaseFirstCallback.countDown();
            assertEquals(ADDED, awaitFuture(firstMutation).status());
            assertEquals(ADDED, awaitFuture(secondMutation).status());
            awaitFuture(lifecyclePublication);

            assertTrue(secondCallbackSawOriginalGeneration.get());
            assertTrue(filesManager.getFileManagerMap().isEmpty());
        } finally {
            releaseFirstCallback.countDown();
            executor.shutdownNow();
            executor.awaitTermination(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void callbackSpawnedCrossDimensionMutationCanFinishWhileLifecycleWaits() throws Exception {
        String otherDimension = "minecraft:the_nether";
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch allowWorkerMutation = new CountDownLatch(1);
        AtomicBoolean workerCallbackSawOriginalGeneration = new AtomicBoolean();
        AtomicReference<Thread> lifecycleThread = new AtomicReference<>();
        ExecutorService executor = newDaemonExecutor(3);

        try {
            Future<WaypointFilesManagerCore.AddWaypointResult> outerMutation =
                    executor.submit(() -> filesManager.addWaypoint(
                            DIMENSION,
                            LIST,
                            waypoint("outer", 0),
                            ignored -> {
                                callbackEntered.countDown();
                                awaitStart(allowWorkerMutation);
                                Future<WaypointFilesManagerCore.AddWaypointResult> worker =
                                        executor.submit(() -> filesManager.addWaypoint(
                                                otherDimension,
                                                LIST,
                                                waypoint("worker", 1),
                                                result -> workerCallbackSawOriginalGeneration.set(
                                                        filesManager.getWaypointFileManager(
                                                                otherDimension
                                                        ) == result.fileManager()
                                                )
                                        ));
                                try {
                                    assertEquals(ADDED, awaitFuture(worker).status());
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }
                    ));
            assertTrue(
                    callbackEntered.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Outer callback did not start"
            );

            Future<?> lifecyclePublication = executor.submit(() -> {
                lifecycleThread.set(Thread.currentThread());
                filesManager.clearWaypointFileManagers();
                return null;
            });
            awaitCondition(
                    () -> lifecyclePublication.isDone()
                            || isLockWaiting(lifecycleThread.get()),
                    "Lifecycle publication did not wait for the outer callback"
            );
            assertFalse(lifecyclePublication.isDone());

            allowWorkerMutation.countDown();
            assertEquals(ADDED, awaitFuture(outerMutation).status());
            awaitFuture(lifecyclePublication);

            assertTrue(workerCallbackSawOriginalGeneration.get());
            assertTrue(filesManager.getFileManagerMap().isEmpty());
        } finally {
            allowWorkerMutation.countDown();
            executor.shutdownNow();
            executor.awaitTermination(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void retainedLiveMapViewNeverObservesPartialDirectoryGeneration() throws Exception {
        int dimensionsPerGeneration = 6;
        Path oldDirectory = this.tempDir.resolve("old-generation");
        Path newDirectory = this.tempDir.resolve("new-generation");
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(oldDirectory);
        Set<String> oldDimensions = populateGeneration(
                filesManager,
                "old",
                dimensionsPerGeneration
        );
        WaypointFilesManagerCore newGeneration = new WaypointFilesManagerCore(newDirectory);
        Set<String> newDimensions = populateGeneration(
                newGeneration,
                "new",
                dimensionsPerGeneration
        );
        Map<String, WaypointFileManager> retainedView = filesManager.getFileManagerMap();
        assertEquals(oldDimensions, Set.copyOf(retainedView.keySet()));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean writerFinished = new AtomicBoolean();
        AtomicBoolean observedOld = new AtomicBoolean(true);
        AtomicBoolean observedNew = new AtomicBoolean();
        AtomicReference<Set<String>> unexpectedGeneration = new AtomicReference<>();
        ExecutorService executor = newDaemonExecutor(2);

        try {
            Future<?> reader = executor.submit(() -> {
                ready.countDown();
                awaitStart(start);
                do {
                    Set<String> observed = Set.copyOf(retainedView.keySet());
                    if (observed.equals(oldDimensions)) {
                        observedOld.set(true);
                    } else if (observed.equals(newDimensions)) {
                        observedNew.set(true);
                    } else {
                        unexpectedGeneration.compareAndSet(null, observed);
                        return;
                    }
                    Thread.onSpinWait();
                } while (!writerFinished.get());
            });
            Future<?> writer = executor.submit(() -> {
                ready.countDown();
                awaitStart(start);
                for (int iteration = 0; iteration < 11; iteration++) {
                    filesManager.changeWaypointFilesDir(
                            (iteration & 1) == 0 ? newDirectory : oldDirectory
                    );
                }
                return null;
            });

            boolean allReady = ready.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(allReady, "Generation reader and writer did not become ready");
            awaitFuture(writer);
            writerFinished.set(true);
            awaitFuture(reader);

            Set<String> finalGeneration = Set.copyOf(retainedView.keySet());
            assertEquals(newDimensions, finalGeneration);
            observedNew.set(true);
            assertNull(
                    unexpectedGeneration.get(),
                    "Retained live view exposed an empty or partially published generation"
            );
            assertTrue(observedOld.get(), "Reader never observed the complete old generation");
            assertTrue(observedNew.get(), "Reader never observed the complete new generation");
        } finally {
            start.countDown();
            writerFinished.set(true);
            executor.shutdownNow();
            executor.awaitTermination(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void staleManagerSavePersistsCurrentDirectoryGeneration() throws Exception {
        Path oldDirectory = this.tempDir.resolve("old-save-generation");
        Path newDirectory = this.tempDir.resolve("new-save-generation");
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(oldDirectory);
        WaypointFilesManagerCore.AddWaypointResult oldResult = filesManager.addWaypoint(
                DIMENSION,
                LIST,
                waypoint("old-state", 0),
                ignored -> {
                }
        );
        filesManager.saveWaypointFile(oldResult.fileManager());
        WaypointFileManager staleManager = oldResult.fileManager();

        WaypointFilesManagerCore newGeneration = new WaypointFilesManagerCore(newDirectory);
        WaypointFilesManagerCore.AddWaypointResult newResult = newGeneration.addWaypoint(
                DIMENSION,
                LIST,
                waypoint("new-state", 1),
                ignored -> {
                }
        );
        newGeneration.saveWaypointFile(newResult.fileManager());

        filesManager.changeWaypointFilesDir(newDirectory);
        WaypointFileManager currentManager = filesManager.getWaypointFileManager(DIMENSION);
        assertNotNull(currentManager);
        assertNotSame(staleManager, currentManager);
        filesManager.addWaypoint(
                DIMENSION,
                LIST,
                waypoint("current-unsaved-state", 2),
                ignored -> {
                }
        );

        filesManager.saveWaypointFile(staleManager);

        WaypointFileManager reloadedCurrent = WaypointFileManager.buildFromDimensionName(
                newDirectory,
                DIMENSION
        );
        reloadedCurrent.readDimension();
        WaypointList currentList = reloadedCurrent.getWaypointListByName(LIST);
        assertNotNull(currentList);
        assertEquals(Set.of("new-state", "current-unsaved-state"), waypointNames(currentList));
        assertEquals(WaypointList.SERVER_N + 2, currentList.getSyncNum());

        WaypointFileManager reloadedOld = WaypointFileManager.buildFromDimensionName(
                oldDirectory,
                DIMENSION
        );
        reloadedOld.readDimension();
        WaypointList oldList = reloadedOld.getWaypointListByName(LIST);
        assertNotNull(oldList);
        assertEquals(Set.of("old-state"), waypointNames(oldList));
        assertEquals(WaypointList.SERVER_N + 1, oldList.getSyncNum());
    }

    @Test
    void concurrentSaveCannotRecreateFileDeletedByDimensionRemoval() throws Exception {
        WaypointFilesManagerCore filesManager = new WaypointFilesManagerCore(this.tempDir);
        WaypointFileManager fileManager = filesManager.addWaypointFileManager(DIMENSION);
        BlockingWaypointList blockingList = new BlockingWaypointList(
                LIST,
                WaypointList.SERVER_N,
                List.of(waypoint("persisted", 0))
        );
        fileManager.addWaypointList(blockingList);
        filesManager.saveWaypointFile(fileManager);
        Path dimensionFile = fileManager.getDimensionFile();
        assertTrue(Files.isRegularFile(dimensionFile));

        blockingList.blockNextSnapshot();
        CountDownLatch removalStarted = new CountDownLatch(1);
        AtomicReference<Thread> removalThread = new AtomicReference<>();
        ExecutorService executor = newDaemonExecutor(2);

        try {
            Future<?> save = executor.submit(() -> {
                filesManager.saveWaypointFile(fileManager);
                return null;
            });
            assertTrue(
                    blockingList.snapshotStarted.await(
                            FUTURE_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    ),
                    "Save did not reach its blocked snapshot"
            );

            Future<WaypointFileManager> removal = executor.submit(() -> {
                removalThread.set(Thread.currentThread());
                removalStarted.countDown();
                return filesManager.removeWaypointFileManager(DIMENSION, true);
            });
            assertTrue(
                    removalStarted.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Removal did not start"
            );
            awaitCondition(
                    () -> removal.isDone() || isLockWaiting(removalThread.get()),
                    "Removal neither completed nor waited for the in-flight save"
            );
            assertFalse(
                    removal.isDone(),
                    "Removal completed while the same-dimension save was still blocked"
            );
            assertSame(fileManager, filesManager.getWaypointFileManager(DIMENSION));

            blockingList.releaseSnapshot.countDown();
            awaitFuture(save);
            assertSame(fileManager, awaitFuture(removal));

            assertNull(filesManager.getWaypointFileManager(DIMENSION));
            assertTrue(
                    Files.notExists(dimensionFile),
                    "Remove-with-delete left the dimension file behind"
            );
            filesManager.saveWaypointFile(fileManager);
            assertTrue(
                    Files.notExists(dimensionFile),
                    "A stale save recreated the removed dimension file"
            );
        } finally {
            blockingList.releaseSnapshot.countDown();
            executor.shutdownNow();
            executor.awaitTermination(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static void assertCapturedInitialState(DimensionWaypointBuffer buffer) {
        assertEquals(DIMENSION, buffer.dimensionName());
        assertEquals(1, buffer.waypointLists().size());
        WaypointList capturedList = buffer.waypointLists().get(0);
        assertEquals(LIST, capturedList.name());
        assertEquals(WaypointList.SERVER_N + 1, capturedList.getSyncNum());
        assertEquals(1, capturedList.size());
        SimpleWaypoint capturedWaypoint = capturedList.getWaypointByName("first");
        assertNotNull(capturedWaypoint);
        assertEquals(new WaypointPos(0, 64, 0), capturedWaypoint.pos());
    }

    private static SimpleWaypoint waypoint(String name, int ordinal) {
        return new SimpleWaypoint(
                name,
                "W",
                new WaypointPos(ordinal, 64, -ordinal),
                ordinal & 0xFFFFFF,
                ordinal,
                (ordinal & 1) == 0
        );
    }

    private static Set<String> expectedNames(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> "waypoint-" + index)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Set<String> waypointNames(WaypointList waypointList) {
        Set<String> names = new HashSet<>();
        for (SimpleWaypoint waypoint : waypointList.simpleWaypoints()) {
            names.add(waypoint.name());
        }
        return names;
    }

    private static <T> long count(List<T> values, Function<T, Boolean> predicate) {
        return values.stream().filter(predicate::apply).count();
    }

    private static Set<String> populateGeneration(
            WaypointFilesManagerCore filesManager,
            String generationName,
            int dimensionCount
    ) throws Exception {
        Set<String> dimensions = new HashSet<>();
        for (int index = 0; index < dimensionCount; index++) {
            String dimensionName = generationName + ":dimension_" + index;
            dimensions.add(dimensionName);
            WaypointFilesManagerCore.AddWaypointResult result = filesManager.addWaypoint(
                    dimensionName,
                    LIST,
                    waypoint(generationName + "-waypoint-" + index, index),
                    ignored -> {
                    }
            );
            filesManager.saveWaypointFile(result.fileManager());
        }
        return Set.copyOf(dimensions);
    }

    private static void awaitCondition(BooleanSupplier condition, String timeoutMessage) {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(FUTURE_TIMEOUT_SECONDS);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError(timeoutMessage);
            }
            Thread.onSpinWait();
        }
    }

    private static boolean isLockWaiting(Thread thread) {
        if (thread == null) {
            return false;
        }
        Thread.State state = thread.getState();
        return state == Thread.State.BLOCKED
                || state == Thread.State.WAITING
                || state == Thread.State.TIMED_WAITING;
    }

    private static <T> List<T> runConcurrently(
            int taskCount,
            ConcurrentTask<T> task
    ) throws Exception {
        ExecutorService executor = newDaemonExecutor(taskCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>(taskCount);
        try {
            for (int index = 0; index < taskCount; index++) {
                int taskIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting for the concurrent start");
                    }
                    return task.run(taskIndex);
                }));
            }
            boolean allReady = ready.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(allReady, "Timed out waiting for workers to become ready");

            List<T> results = new ArrayList<>(taskCount);
            for (Future<T> future : futures) {
                results.add(awaitFuture(future));
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static ExecutorService newDaemonExecutor(int threadCount) {
        AtomicInteger threadNumber = new AtomicInteger();
        return Executors.newFixedThreadPool(threadCount, task -> {
            Thread thread = new Thread(
                    task,
                    "waypoint-concurrency-test-" + threadNumber.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        });
    }

    private static <T> T awaitFuture(Future<T> future) throws Exception {
        try {
            return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            rethrowWorkerFailure(exception.getCause());
            throw new AssertionError("Unreachable");
        }
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            if (!start.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the concurrent start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the concurrent start", exception);
        }
    }

    private static void rethrowWorkerFailure(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError(failure);
    }

    private static final class BlockingWaypointList extends WaypointList {
        private final CountDownLatch snapshotStarted = new CountDownLatch(1);
        private final CountDownLatch releaseSnapshot = new CountDownLatch(1);
        private final AtomicBoolean blockNextSnapshot = new AtomicBoolean();

        private BlockingWaypointList(
                String name,
                int syncNum,
                List<SimpleWaypoint> simpleWaypoints
        ) {
            super(name, syncNum, simpleWaypoints);
        }

        private void blockNextSnapshot() {
            if (!this.blockNextSnapshot.compareAndSet(false, true)) {
                throw new IllegalStateException("A snapshot block is already armed");
            }
        }

        @Override
        public synchronized WaypointList deepCopy() {
            if (this.blockNextSnapshot.compareAndSet(true, false)) {
                this.snapshotStarted.countDown();
                awaitStart(this.releaseSnapshot);
            }
            return super.deepCopy();
        }
    }

    @FunctionalInterface
    private interface ConcurrentTask<T> {
        T run(int taskIndex) throws Exception;
    }
}
