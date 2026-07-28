package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OrderedParallelMapperTest {

    @Test
    public void resultsKeepInputOrderWhenTasksFinishOutOfOrder() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            List<Integer> result = OrderedParallelMapper.map(executor, List.of(3, 1, 2), value -> {
                try {
                    Thread.sleep(value * 10L);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
                return value;
            }, error -> { });

            assertEquals(List.of(3, 1, 2), result);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void interruptionCancelsOutstandingTasks() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch interrupted = new CountDownLatch(2);
        Thread caller = new Thread(() -> {
            try {
                OrderedParallelMapper.map(executor, List.of(1, 2), value -> {
                    started.countDown();
                    try {
                        Thread.sleep(10_000L);
                    } catch (InterruptedException error) {
                        interrupted.countDown();
                        Thread.currentThread().interrupt();
                    }
                    return value;
                }, error -> { });
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            caller.start();
            assertTrue(started.await(1, TimeUnit.SECONDS));
            caller.interrupt();
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        } finally {
            caller.join(1_000L);
            executor.shutdownNow();
        }
    }

    @Test
    public void failedTaskDoesNotDiscardOtherOrderedResults() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Throwable> errors = new ArrayList<>();
        try {
            List<Integer> result = OrderedParallelMapper.map(executor, List.of(1, 2, 3), value -> {
                if (value == 2) throw new IllegalStateException("broken");
                return value;
            }, errors::add);

            assertEquals(List.of(1, 3), result);
            assertEquals(1, errors.size());
        } finally {
            executor.shutdownNow();
        }
    }
}
