package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BoundedCacheTest {

    @Test
    public void evictsLeastRecentlyUsedEntry() {
        BoundedCache<String, String> cache = new BoundedCache<>(2);
        cache.put("one", "1");
        cache.put("two", "2");
        assertEquals("1", cache.get("one"));

        cache.put("three", "3");

        assertNull(cache.get("two"));
        assertEquals("1", cache.get("one"));
        assertEquals("3", cache.get("three"));
    }

    @Test
    public void updatingEntryDoesNotConsumeAdditionalCapacity() {
        BoundedCache<String, String> cache = new BoundedCache<>(1);
        cache.put("key", "old");
        cache.put("key", "new");

        assertEquals(1, cache.size());
        assertEquals("new", cache.get("key"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidCapacity() {
        new BoundedCache<>(0);
    }

    @Test
    public void evictsByWeight() {
        BoundedCache<String, String> cache = new BoundedCache<>(5, String::length);
        cache.put("one", "123");
        cache.put("two", "45");
        cache.get("one");
        cache.put("three", "67");

        assertNull(cache.get("two"));
        assertEquals("123", cache.get("one"));
        assertEquals(5, cache.weight());
    }

    @Test
    public void doesNotRetainEntryLargerThanBudget() {
        BoundedCache<String, String> cache = new BoundedCache<>(2, String::length);
        cache.put("large", "123");

        assertNull(cache.get("large"));
        assertEquals(0, cache.weight());
    }
}
