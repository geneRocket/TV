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
        scheduler.runAll();
        releaseBlocker.countDown();
        assertFalse(queuedStarted.await(100, TimeUnit.MILLISECONDS));
        scheduler.runLast();
        assertEquals(List.of(false), values);
        scope.close();
    }

    @Test
    public void oneScopeCoordinatesDifferentResultTypes() throws Exception {
        Scheduler scheduler = new Scheduler();
        TaskScope scope = new TaskScope(Executors.newFixedThreadPool(2), scheduler, error -> { });
        CountDownLatch completed = new CountDownLatch(2);
        List<Object> values = Collections.synchronizedList(new ArrayList<>());

        scope.submit("name", () -> "video", 0, value -> { values.add(value); completed.countDown(); }, error -> "", null);
        scope.submit("count", () -> 3, 0, value -> { values.add(value); completed.countDown(); }, error -> 0, null);

        assertTrue(scheduler.awaitSize(2, 1, TimeUnit.SECONDS));
        scheduler.runAll();
        assertTrue(completed.await(1, TimeUnit.SECONDS));
        assertTrue(values.contains("video"));
        assertTrue(values.contains(3));
        scope.close();
    }

    @Test
    public void latestModeSuppressesReplacedResult() throws Exception {
        Scheduler scheduler = new Scheduler();
        TaskScope scope = new TaskScope(Executors.newFixedThreadPool(2), scheduler, error -> { });
        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        CountDownLatch newerDone = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        List<String> values = Collections.synchronizedList(new ArrayList<>());

        scope.submit("home", () -> { oldStarted.countDown(); releaseOld.await(); return "old"; }, 0, values::add, error -> "error", cancelled::countDown);
        assertTrue(oldStarted.await(1, TimeUnit.SECONDS));
        scope.submit("home", () -> "new", 0, value -> { values.add(value); newerDone.countDown(); }, error -> "error", null);
        releaseOld.countDown();

        assertTrue(scheduler.awaitSize(1, 1, TimeUnit.SECONDS));
        scheduler.runAll();
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
        scheduler.runLast();
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

    @Test
    public void closeSuppressesCompletedButUndeliveredCallback() throws Exception {
        Scheduler scheduler = new Scheduler();
        TaskScope scope = new TaskScope(Executors.newSingleThreadExecutor(), scheduler, error -> { });
        CountDownLatch delivered = new CountDownLatch(1);

        scope.submit("history", () -> "done", 0, value -> delivered.countDown(), error -> "error", null);
        assertTrue(scheduler.awaitSize(1, 1, TimeUnit.SECONDS));
        scope.close();
        scheduler.runAll();

        assertEquals(1, delivered.getCount());
    }

    @Test
    public void replacementSuppressesCompletedButUndeliveredCallback() throws Exception {
        Scheduler scheduler = new Scheduler();
        TaskScope scope = new TaskScope(Executors.newSingleThreadExecutor(), scheduler, error -> { });
        List<String> values = Collections.synchronizedList(new ArrayList<>());

        scope.submit("history", () -> "old", 0, values::add, error -> "error", null);
        assertTrue(scheduler.awaitSize(1, 1, TimeUnit.SECONDS));
        scope.submit("history", () -> "new", 0, values::add, error -> "error", null);
        assertTrue(scheduler.awaitSize(1, 1, TimeUnit.SECONDS));
        scheduler.runAll();

        assertEquals(List.of("new"), values);
        scope.close();
    }

    @Test
    public void cancelBeforeSubmitReturnsStillInterruptsWorker() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch allowReturn = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0,
                TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>()) {
            @Override public java.util.concurrent.Future<?> submit(Runnable task) {
                java.util.concurrent.Future<?> future = super.submit(task);
                try { allowReturn.await(); }
                catch (InterruptedException error) { throw new AssertionError(error); }
                return future;
            }
        };
        Scheduler scheduler = new Scheduler();
        TaskScope scope = new TaskScope(executor, scheduler, error -> {}, false);
        Thread submitter = new Thread(() -> scope.submit("race", () -> {
            started.countDown();
            try { release.await(); }
            catch (InterruptedException error) { interrupted.countDown(); throw error; }
            return true;
        }, 1000, value -> {}, error -> false, null));
        try {
            submitter.start();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertTrue(scope.cancel("race"));
            allowReturn.countDown();
            submitter.join(2000);
            assertFalse(submitter.isAlive());
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertEquals(0, scheduler.size());
        } finally {
            allowReturn.countDown();
            release.countDown();
            scope.close();
            executor.shutdownNow();
            submitter.join(2000);
        }
    }

    private static final class Scheduler implements TaskScheduler {
        private final List<Runnable> tasks = new ArrayList<>();
        @Override public synchronized void post(Runnable task, long delayMillis) {
            tasks.add(task);
            notifyAll();
        }
        @Override public synchronized void remove(Runnable task) { tasks.remove(task); }
        synchronized int size() { return tasks.size(); }
        synchronized boolean awaitSize(int expected, long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (tasks.size() < expected) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
            return true;
        }
        void runLast() {
            Runnable task;
            synchronized (this) { task = tasks.remove(tasks.size() - 1); }
            task.run();
        }
        void runAll() {
            while (size() > 0) runLast();
        }
    }
}
