package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ConfigTest {
    @Test
    public void invalidationDuringDefaultSelectionKeepsTheReadSnapshot() throws Exception {
        Field field = Config.class.getDeclaredField("TYPE_CACHE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, List<Config>> cache = (Map<Integer, List<Config>>) field.get(null);
        Config expected = new Config();
        for (int type = 0; type < 3; type++) {
            final int key = type;
            List<Config> items = new ArrayList<Config>() {
                @Override public boolean isEmpty() {
                    // Model cache invalidation after lookup, before the selected item is read.
                    cache.remove(key);
                    return super.isEmpty();
                }
            };
            items.add(expected);
            cache.put(type, items);
            try {
                assertSame(expected, Config.firstCached(type));
                assertNull(Config.firstCached(type));
            } finally {
                cache.remove(type);
            }
        }
    }
}
