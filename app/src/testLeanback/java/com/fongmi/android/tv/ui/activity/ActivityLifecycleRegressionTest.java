package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActivityLifecycleRegressionTest {

    @Test
    public void disconnectedServiceRemainsBoundUntilUnbind() throws Exception {
        CastActivity activity = allocate(CastActivity.class);
        Field bound = CastActivity.class.getDeclaredField("mServiceBound");
        bound.setAccessible(true);
        bound.setBoolean(activity, true);
        activity.onServiceDisconnected(null);
        assertTrue(bound.getBoolean(activity));
    }

    @Test
    public void disconnectedServiceDoesNotCreateBinding() throws Exception {
        CastActivity activity = allocate(CastActivity.class);
        Field bound = CastActivity.class.getDeclaredField("mServiceBound");
        bound.setAccessible(true);
        activity.onServiceDisconnected(null);
        assertFalse(bound.getBoolean(activity));
    }

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
