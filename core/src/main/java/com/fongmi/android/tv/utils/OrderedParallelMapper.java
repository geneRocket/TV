package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

/** Runs independent work concurrently while preserving input order for stateful consumers. */
public final class OrderedParallelMapper {

    private OrderedParallelMapper() {
    }

    public static <I, O> List<O> map(ExecutorService executor, List<I> inputs, Function<I, O> mapper, Consumer<Throwable> logger) throws InterruptedException {
        List<Future<O>> futures = new ArrayList<>(inputs.size());
        List<O> results = new ArrayList<>(inputs.size());
        try {
            for (I input : inputs) futures.add(executor.submit(() -> mapper.apply(input)));
            for (Future<O> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException error) {
                    logger.accept(error.getCause() == null ? error : error.getCause());
                }
            }
            return results;
        } catch (InterruptedException error) {
            cancel(futures);
            throw error;
        } catch (RuntimeException error) {
            cancel(futures);
            logger.accept(error);
            return results;
        }
    }

    private static void cancel(List<? extends Future<?>> futures) {
        for (Future<?> future : futures) future.cancel(true);
    }
}
