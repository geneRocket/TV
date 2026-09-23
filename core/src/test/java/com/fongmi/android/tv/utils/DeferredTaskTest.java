package com.fongmi.android.tv.utils;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class DeferredTaskTest {
    private final Scheduler scheduler = new Scheduler();
    private final QueueExecutor executor = new QueueExecutor();
    private final DeferredTask<String> task = new DeferredTask<>(executor, scheduler, error -> {});
    private final List<String> delivered = new ArrayList<>();
    private final List<Work> attempts = new ArrayList<>();

    private Work work(String value) {
        Work work = new Work(value);
        attempts.add(work);
        return work;
    }

    @Test public void waitsWithoutPollingAndCompletesOnlyOnce() {
        task.load(() -> work("result"), delivered::add);
        assertTrue(scheduler.tasks.isEmpty());
        task.setReady(true);
        task.setReady(true);
        assertEquals(1, scheduler.tasks.size());
        scheduler.next();
        executor.runAll();
        scheduler.next();
        assertEquals(List.of("result"), delivered);
        task.setReady(false);
        task.setReady(true);
        assertTrue(scheduler.tasks.isEmpty());
        assertEquals(1, attempts.size());
    }

    @Test public void bufferingInvalidatesPostedResultAndResumesWithFreshWork() {
        task.load(() -> work("result"), delivered::add);
        task.setReady(true);
        scheduler.next();
        executor.runAll();
        Runnable stale = scheduler.tasks.keySet().iterator().next();
        task.setReady(false);
        assertTrue(attempts.get(0).cancelled);
        stale.run();
        assertTrue(delivered.isEmpty());
        assertTrue(scheduler.tasks.isEmpty());
        task.setReady(true);
        scheduler.next();
        executor.runAll();
        scheduler.next();
        assertEquals(List.of("result"), delivered);
        assertEquals(2, attempts.size());
    }

    @Test public void replacingDetailAndClosingSuppressOldWork() {
        task.setReady(true);
        task.load(() -> work("old"), delivered::add);
        scheduler.next();
        executor.runAll();
        Runnable stale = scheduler.tasks.keySet().iterator().next();
        task.load(() -> work("new"), delivered::add);
        stale.run();
        scheduler.next();
        task.close();
        executor.runAll();
        assertTrue(delivered.isEmpty());
        assertTrue(scheduler.tasks.isEmpty());
        assertFalse(executor.isShutdown());
        task.setReady(false);
        task.setReady(true);
        assertTrue(scheduler.tasks.isEmpty());
    }

    private static final class Work implements DeferredTask.Work<String> {
        final String value;
        boolean cancelled;
        Work(String value) { this.value = value; }
        public String call() { return value; }
        public void cancel() { cancelled = true; }
    }
    private static final class Scheduler implements TaskScheduler {
        final Map<Runnable, Long> tasks = new LinkedHashMap<>();
        public void post(Runnable task, long delay) { tasks.put(task, delay); }
        public void remove(Runnable task) { tasks.remove(task); }
        void next() {
            Runnable task = tasks.keySet().iterator().next();
            tasks.remove(task);
            task.run();
        }
    }
    private static final class QueueExecutor extends AbstractExecutorService {
        final List<Runnable> tasks = new ArrayList<>();
        boolean shutdown;
        public void execute(Runnable task) { tasks.add(task); }
        void runAll() { for (Runnable task : new ArrayList<>(tasks)) task.run(); tasks.clear(); }
        public void shutdown() { shutdown = true; }
        public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        public boolean isShutdown() { return shutdown; }
        public boolean isTerminated() { return shutdown; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
    }
}
