package com.fongmi.android.tv.ui.adapter;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class TvAdapterIdentityTest {
    // The JVM android.jar Observable constructor does not initialize its list.
    static void initializeObservers(RecyclerView.Adapter<?> adapter) {
        try {
            Field observable = RecyclerView.Adapter.class.getDeclaredField("mObservable");
            observable.setAccessible(true);
            Field observers = Class.forName("android.database.Observable").getDeclaredField("mObservers");
            observers.setAccessible(true);
            observers.set(observable.get(adapter), new ArrayList<>());
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    static WordAdapter words() {
        return new WordAdapter(null) {
            { initializeObservers(this); }
            @Override public void setHasStableIds(boolean stable) {
                initializeObservers(this);
                super.setHasStableIds(stable);
            }
        };
    }

    private void assertIdentityContract(RecyclerView.Adapter<?> adapter) {
        assertEquals(2, adapter.getItemCount());
        if (!adapter.hasStableIds()) return;
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < adapter.getItemCount(); i++) {
            assertTrue("Different rows must have distinct stable IDs", ids.add(adapter.getItemId(i)));
        }
    }

    @Test public void collidingSuggestionTextRemainsSeparateRows() {
        WordAdapter adapter = words();
        adapter.addAll(Arrays.asList("Aa", "BB"));
        assertIdentityContract(adapter);
    }

    @Test public void collidingBackupNamesRemainSeparateRows() {
        BackupAdapter adapter = new BackupAdapter(null) {
            { initializeObservers(this); }
            @Override public void setHasStableIds(boolean stable) {
                initializeObservers(this);
                super.setHasStableIds(stable);
            }
        };
        adapter.setItems(Arrays.asList("Aa", "BB"));
        assertIdentityContract(adapter);
    }

    @Test public void collidingCachePathsRemainSeparateRows() {
        CacheDirAdapter adapter = new CacheDirAdapter(null, Arrays.asList("/cache/Aa", "/cache/BB")) {
            { initializeObservers(this); }
            @Override public void setHasStableIds(boolean stable) {
                initializeObservers(this);
                super.setHasStableIds(stable);
            }
        };
        assertIdentityContract(adapter);
    }
}
