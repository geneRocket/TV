package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeyedLatestTaskTest {

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
    public void replacementIsScopedToItsKey() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Scheduler scheduler = new Scheduler();
        KeyedLatestTask<String> tasks = new KeyedLatestTask<>(executor, scheduler, error -> { });
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        List<String> values = Collections.synchronizedList(new ArrayList<>());

        tasks.submit("content", () -> { firstStarted.countDown(); releaseFirst.await(); return "old"; }, 1_000, values::add, error -> "fallback", null, null);
        assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
        tasks.submit("content", () -> "new", 1_000, value -> { values.add(value); completed.countDown(); }, error -> "fallback", null, null);
        tasks.submit("player", () -> "player", 1_000, value -> { values.add(value); completed.countDown(); }, error -> "fallback", null, null);
        releaseFirst.countDown();

        assertTrue(completed.await(1, TimeUnit.SECONDS));
        assertEquals(List.of("new", "player"), values.stream().sorted().collect(Collectors.toList()));
        tasks.close();
    }

    @Test
    public void completedTaskRemovesItsTimeout() {
        Scheduler scheduler = new Scheduler();
        KeyedLatestTask<String> tasks = new KeyedLatestTask<>(new DirectExecutor(), scheduler, error -> { });

        tasks.submit("content", () -> "ready", 1_000, value -> { }, error -> "fallback", null, null);

        assertEquals(0, scheduler.size());
        tasks.close();
    }

    @Test
    public void timeoutCancelsWorkAndPublishesFallbackOnce() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Scheduler scheduler = new Scheduler();
        KeyedLatestTask<String> tasks = new KeyedLatestTask<>(executor, scheduler, error -> { });
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        List<String> values = new ArrayList<>();

        tasks.submit("content", () -> { started.countDown(); try { Thread.sleep(10_000); return "late"; } catch (InterruptedException error) { interrupted.countDown(); throw error; } }, 1_000, values::add, error -> "timeout", null, null);
        assertTrue(started.await(1, TimeUnit.SECONDS));
        scheduler.runLast();

        assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        assertEquals(List.of("timeout"), values);
        tasks.close();
    }

    @Test
    public void cancellationPreventsLatePublication() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Scheduler scheduler = new Scheduler();
        KeyedLatestTask<String> tasks = new KeyedLatestTask<>(executor, scheduler, error -> { });
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        List<String> values = new ArrayList<>();

        tasks.submit("history", () -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
                return "late";
            } catch (InterruptedException error) {
                interrupted.countDown();
                throw error;
            }
        }, 1_000, values::add, error -> "fallback", null, null);
        assertTrue(started.await(1, TimeUnit.SECONDS));

        tasks.cancel("history", null);

        assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        assertTrue(values.isEmpty());
        assertEquals(0, scheduler.size());
        tasks.close();
    }

    @Test
    public void closedSchedulerDoesNotAcceptNewWork() {
        Scheduler scheduler = new Scheduler();
        KeyedLatestTask<String> tasks = new KeyedLatestTask<>(new DirectExecutor(), scheduler, error -> { });
        List<String> values = new ArrayList<>();

        tasks.close();
        tasks.submit("history", () -> "late", 1_000, values::add, error -> "fallback", null, null);

        assertTrue(values.isEmpty());
        assertFalse(scheduler.size() > 0);
    }

    @Test
    public void closingTasksDoesNotShutdownSharedExecutor() {
        DirectExecutor executor = new DirectExecutor();
        KeyedLatestTask<String> tasks = new KeyedLatestTask<>(executor, new Scheduler(), error -> { }, false);

        tasks.close();

        assertFalse(executor.isShutdown());
    }

    @Test
    public void replacingQueuedTaskRemovesCancelledFutureFromThreadPool() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4));
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        KeyedLatestTask<String> tasks = new KeyedLatestTask<>(executor, new Scheduler(), error -> { }, false);
        try {
            tasks.submit("running", () -> { running.countDown(); release.await(); return "running"; }, 1_000, value -> { }, error -> "", null, null);
            assertTrue(running.await(1, TimeUnit.SECONDS));
            tasks.submit("replace", () -> "old", 1_000, value -> { }, error -> "", null, null);
            tasks.submit("replace", () -> "new", 1_000, value -> { }, error -> "", null, null);

            assertEquals(1, executor.getQueue().size());
        } finally {
            release.countDown();
            tasks.close();
            executor.shutdownNow();
        }
    }

    private static final class Scheduler implements TaskScheduler {
        private final List<Runnable> tasks = new ArrayList<>();
        @Override public void post(Runnable task, long delayMillis) { tasks.add(task); }
        @Override public void remove(Runnable task) { tasks.remove(task); }
        int size() { return tasks.size(); }
        void runLast() { tasks.get(tasks.size() - 1).run(); }
    }

    private static final class DirectExecutor extends AbstractExecutorService {
        private boolean shutdown;
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
        @Override public void execute(Runnable command) { if (shutdown) throw new java.util.concurrent.RejectedExecutionException(); command.run(); }
    }
}
