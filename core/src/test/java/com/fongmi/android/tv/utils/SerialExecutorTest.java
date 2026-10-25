package com.fongmi.android.tv.utils;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class SerialExecutorTest {
    @Test public void reentrantInputsRunAfterCurrentCommand() {
        List<Integer> seen = new ArrayList<>();
        SerialExecutor serial = new SerialExecutor(Runnable::run, error -> fail(error.toString()));
        serial.execute(() -> { seen.add(1); serial.execute(() -> seen.add(3)); seen.add(2); });
        assertEquals(Arrays.asList(1, 2, 3), seen);
    }
    @Test public void parallelCallersNeverOverlapAndLoseNoCommands() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger(), completed = new AtomicInteger();
            CountDownLatch done = new CountDownLatch(300);
            SerialExecutor serial = new SerialExecutor(pool, error -> fail(error.toString()));
            List<Future<?>> inputs = new ArrayList<>();
            for (int i = 0; i < 300; i++) inputs.add(pool.submit(() -> serial.execute(() -> {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                Thread.yield(); completed.incrementAndGet(); active.decrementAndGet(); done.countDown();
            })));
            for (Future<?> input : inputs) input.get(5, TimeUnit.SECONDS);
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(1, maximum.get()); assertEquals(300, completed.get());
        } finally { pool.shutdownNow(); }
    }
    @Test public void exceptionIsReportedAndFollowingCommandRuns() {
        List<Runnable> pending = new ArrayList<>(); List<String> seen = new ArrayList<>();
        SerialExecutor serial = new SerialExecutor(pending::add, error -> seen.add(error.getMessage()));
        serial.execute(() -> { throw new IllegalStateException("failure"); });
        serial.execute(() -> seen.add("next")); pending.remove(0).run();
        assertEquals(Arrays.asList("failure", "next"), seen);
    }
    @Test public void closeDropsWaitingCommandsAndRejectsLaterWork() {
        List<Runnable> pending = new ArrayList<>(); List<Integer> seen = new ArrayList<>();
        SerialExecutor serial = new SerialExecutor(pending::add, error -> fail());
        serial.execute(() -> seen.add(1)); serial.close(); serial.execute(() -> seen.add(2));
        pending.remove(0).run(); assertTrue(seen.isEmpty());
    }
    @Test public void rejectedDrainDoesNotRetainCommandsForUnexpectedReplay() {
        AtomicInteger attempts = new AtomicInteger(); List<Integer> seen = new ArrayList<>();
        SerialExecutor serial = new SerialExecutor(task -> {
            if (attempts.getAndIncrement() == 0) throw new RejectedExecutionException(); task.run();
        }, error -> fail());
        try { serial.execute(() -> seen.add(1)); fail(); } catch (RejectedExecutionException expected) { }
        serial.execute(() -> seen.add(2)); assertEquals(Collections.singletonList(2), seen);
    }
}
