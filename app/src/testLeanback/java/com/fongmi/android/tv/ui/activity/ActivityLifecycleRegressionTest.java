package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.lang.reflect.Field;

public class ActivityLifecycleRegressionTest {

    @Test
    public void finishingSearchIgnoresDelayedSubmission() throws Exception {
        allocate(FinishingSearch.class).onSearch();
    }

    @Test
    public void destroyedSearchIgnoresDelayedSubmission() throws Exception {
        allocate(DestroyedSearch.class).onSearch();
    }

    public static class FinishingSearch extends SearchActivity {
        @Override public boolean isFinishing() { return true; }
        @Override public boolean isDestroyed() { return false; }
    }

    public static class DestroyedSearch extends SearchActivity {
        @Override public boolean isFinishing() { return false; }
        @Override public boolean isDestroyed() { return true; }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field instance = unsafeClass.getDeclaredField("theUnsafe");
        instance.setAccessible(true);
        return type.cast(unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(instance.get(null), type));
    }
}
