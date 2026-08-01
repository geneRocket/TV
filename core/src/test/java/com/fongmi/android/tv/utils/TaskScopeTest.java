package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaskScopeTest {

    @Test
    public void configUrlsAreTrimmedDeDuplicatedAndOrdered() {
        assertEquals(List.of("https://one", "https://two", "https://three"), ConfigUrlParser.parse(" https://one,https://two；https://one\n https://three "));
    }

    @Test
    public void emptyConfigUrlsProduceAnEmptyList() {
        assertTrue(ConfigUrlParser.parse(" \n , ; ").isEmpty());
        assertTrue(ConfigUrlParser.parse(null).isEmpty());
    }

    @Test
    public void queuedWorkStillRespectsEndToEndDeadline() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Scheduler scheduler = new Scheduler();
        TaskScope scope = new TaskScope(executor, scheduler, error -> { });
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        CountDownLatch queuedStarted = new CountDownLatch(1);
        List<Boolean> values = Collections.synchronizedList(new ArrayList<>());

        scope.submit("blocker", () -> {
            blockerStarted.countDown();
            releaseBlocker.await();
            return true;
        }, 0, value -> { }, error -> false, null);
        assertTrue(blockerStarted.await(1, TimeUnit.SECONDS));

        scope.submit("queued", () -> {
            queuedStarted.countDown();
            return true;
        }, 1_000, values::add, error -> false, null);

        assertEquals(1, scheduler.size());
        scheduler.runLast();
        releaseBlocker.countDown();
        assertFalse(queuedStarted.await(100, TimeUnit.MILLISECONDS));
        assertEquals(List.of(false), values);
        scope.close();
    }

    @Test
    public void oneScopeCoordinatesDifferentResultTypes() throws Exception {
        TaskScope scope = new TaskScope(Executors.newFixedThreadPool(2), new Scheduler(), error -> { });
        CountDownLatch completed = new CountDownLatch(2);
        List<Object> values = Collections.synchronizedList(new ArrayList<>());

        scope.submit("name", () -> "video", 0, value -> { values.add(value); completed.countDown(); }, error -> "", null);
        scope.submit("count", () -> 3, 0, value -> { values.add(value); completed.countDown(); }, error -> 0, null);

        assertTrue(completed.await(1, TimeUnit.SECONDS));
        assertTrue(values.contains("video"));
        assertTrue(values.contains(3));
        scope.close();
    }

    @Test
    public void latestModeSuppressesReplacedResult() throws Exception {
        TaskScope scope = new TaskScope(Executors.newFixedThreadPool(2), new Scheduler(), error -> { });
        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        CountDownLatch newerDone = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        List<String> values = Collections.synchronizedList(new ArrayList<>());

        scope.submit("home", () -> { oldStarted.countDown(); releaseOld.await(); return "old"; }, 0, values::add, error -> "error", cancelled::countDown);
        assertTrue(oldStarted.await(1, TimeUnit.SECONDS));
        scope.submit("home", () -> "new", 0, value -> { values.add(value); newerDone.countDown(); }, error -> "error", null);
        releaseOld.countDown();

        assertTrue(newerDone.await(1, TimeUnit.SECONDS));
        assertEquals(0, cancelled.getCount());
        assertEquals(List.of("new"), values);
        scope.close();
    }

    @Test
    public void timeoutInterruptsRunningWorkAndPublishesFallbackOnce() throws Exception {
        Scheduler scheduler = new Scheduler();
        TaskScope scope = new TaskScope(Executors.newSingleThreadExecutor(), scheduler, error -> { });
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        List<String> values = Collections.synchronizedList(new ArrayList<>());

        scope.submit("player", () -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
                return "late";
            } catch (InterruptedException error) {
                interrupted.countDown();
                throw error;
            }
        }, 1_000, values::add, error -> error instanceof TimeoutException ? "timeout" : "error", cancelled::countDown);
        assertTrue(started.await(1, TimeUnit.SECONDS));
        scheduler.runLast();

        assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        assertEquals(0, cancelled.getCount());
        assertEquals(List.of("timeout"), values);
        scope.close();
    }

    @Test
    public void closeCancelsUnderlyingWork() throws Exception {
        TaskScope scope = new TaskScope(Executors.newSingleThreadExecutor(), new Scheduler(), error -> { });
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);

        scope.submit("player", () -> {
            started.countDown();
            Thread.sleep(10_000);
            return true;
        }, 0, value -> { }, error -> false, cancelled::countDown);
        assertTrue(started.await(1, TimeUnit.SECONDS));
        scope.close();

        assertEquals(0, cancelled.getCount());
    }

    private static final class Scheduler implements TaskScheduler {
        private final List<Runnable> tasks = new ArrayList<>();
        @Override public synchronized void post(Runnable task, long delayMillis) { tasks.add(task); }
        @Override public synchronized void remove(Runnable task) { tasks.remove(task); }
        synchronized int size() { return tasks.size(); }
        synchronized void runLast() { tasks.get(tasks.size() - 1).run(); }
    }
}
