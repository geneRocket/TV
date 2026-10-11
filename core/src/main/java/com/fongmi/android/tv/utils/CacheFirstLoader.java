package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Returns available cached inputs without waiting for missing or expired inputs. */
public final class CacheFirstLoader {
    private CacheFirstLoader() { }

    public static <I, O> List<O> load(List<I> inputs, Function<I, O> readCache,
                                    Predicate<I> expired, Consumer<I> refreshAsync,
                                    Supplier<List<O>> fetchWhenEmpty) {
        return load(inputs, readCache, expired, refreshAsync, refreshAsync, fetchWhenEmpty);
    }

    public static <I, O> List<O> load(List<I> inputs, Function<I, O> readCache,
                                    Predicate<I> expired, Consumer<I> refreshAsync,
                                    Consumer<I> fetchMissingAsync, Supplier<List<O>> fetchWhenEmpty) {
        List<O> cached = new ArrayList<>();
        List<Runnable> updates = new ArrayList<>();
        for (I input : inputs) {
            O value = readCache.apply(input);
            if (value != null) cached.add(value);
            if (value == null) updates.add(() -> fetchMissingAsync.accept(input));
            else if (expired.test(input)) updates.add(() -> refreshAsync.accept(input));
        }
        if (cached.isEmpty()) return fetchWhenEmpty.get();
        for (Runnable update : updates) update.run();
        return cached;
    }
}
