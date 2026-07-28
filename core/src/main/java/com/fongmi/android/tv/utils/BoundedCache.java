package com.fongmi.android.tv.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Small synchronized LRU cache for process-wide metadata and decoded content. */
public final class BoundedCache<K, V> {

    private final Map<K, V> values;
    private final int maxWeight;
    private final ToIntFunction<V> weigher;
    private int weight;

    public BoundedCache(int capacity) {
        this(capacity, value -> 1);
    }

    public BoundedCache(int maxWeight, ToIntFunction<V> weigher) {
        if (maxWeight < 1) throw new IllegalArgumentException("maxWeight must be positive");
        if (weigher == null) throw new NullPointerException("weigher");
        this.maxWeight = maxWeight;
        this.weigher = weigher;
        this.values = new LinkedHashMap<>(16, 0.75f, true);
    }

    public synchronized V get(K key) {
        return values.get(key);
    }

    public synchronized void put(K key, V value) {
        V previous = values.put(key, value);
        if (previous != null) weight -= weigh(previous);
        weight += weigh(value);
        while (weight > maxWeight && !values.isEmpty()) {
            K eldest = values.keySet().iterator().next();
            V removed = values.remove(eldest);
            weight -= weigh(removed);
        }
    }

    public synchronized V remove(K key) {
        V removed = values.remove(key);
        if (removed != null) weight -= weigh(removed);
        return removed;
    }

    public synchronized int size() {
        return values.size();
    }

    public synchronized int weight() {
        return weight;
    }

    private int weigh(V value) {
        return Math.max(0, weigher.applyAsInt(value));
    }
}
