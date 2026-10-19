package com.fongmi.android.tv.model;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.fongmi.android.tv.model.HomeConfigViewModel.State.*;
import static org.junit.Assert.*;

public class HomeConfigViewModelTest {
    @Before public void useMainThread() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override public void executeOnDiskIO(Runnable runnable) { runnable.run(); }
            @Override public void postToMainThread(Runnable runnable) { runnable.run(); }
            @Override public boolean isMainThread() { return true; }
        });
    }

    @After public void restoreExecutor() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test public void lateHomeViewReceivesFailureWhichOccurredBeforeItExisted() {
        HomeConfigViewModel model = new HomeConfigViewModel();
        model.loading();
        model.failed();
        List<HomeConfigViewModel.State> received = new ArrayList<>();
        Owner view = new Owner();
        model.state().observe(view, received::add);
        view.state(Lifecycle.State.STARTED);
        assertEquals(Arrays.asList(FAILED), received);
        view.state(Lifecycle.State.DESTROYED);
    }

    @Test public void destroyedViewStopsReceivingAndReplacementGetsCurrentState() {
        HomeConfigViewModel model = new HomeConfigViewModel();
        List<HomeConfigViewModel.State> old = new ArrayList<>(), replacement = new ArrayList<>();
        Owner first = new Owner();
        first.state(Lifecycle.State.STARTED);
        model.state().observe(first, old::add);
        model.loading();
        first.state(Lifecycle.State.DESTROYED);
        model.ready();
        Owner next = new Owner();
        next.state(Lifecycle.State.STARTED);
        model.state().observe(next, replacement::add);
        assertEquals(Arrays.asList(IDLE, LOADING), old);
        assertEquals(Arrays.asList(READY), replacement);
        next.state(Lifecycle.State.DESTROYED);
    }

    @Test public void stoppedViewSeesLatestStateRatherThanReplayingOldFailure() {
        HomeConfigViewModel model = new HomeConfigViewModel();
        List<HomeConfigViewModel.State> received = new ArrayList<>();
        Owner view = new Owner();
        view.state(Lifecycle.State.STARTED);
        model.state().observe(view, received::add);
        view.state(Lifecycle.State.CREATED);
        model.failed();
        model.loading();
        model.ready();
        assertEquals(Arrays.asList(IDLE), received);
        view.state(Lifecycle.State.STARTED);
        assertEquals(Arrays.asList(IDLE, READY), received);
        view.state(Lifecycle.State.DESTROYED);
    }

    @Test public void failedRetryReturnsToLoadingAndThenReady() {
        HomeConfigViewModel model = new HomeConfigViewModel();
        Owner view = new Owner();
        view.state(Lifecycle.State.STARTED);
        List<HomeConfigViewModel.State> received = new ArrayList<>();
        model.state().observe(view, received::add);
        model.failed();
        model.loading();
        model.ready();
        assertEquals(Arrays.asList(IDLE, FAILED, LOADING, READY), received);
        view.state(Lifecycle.State.DESTROYED);
    }

    @Test public void activityStoreRetainsStateWithoutBroadcastingToAnotherActivity() {
        ViewModelStore first = new ViewModelStore(), other = new ViewModelStore();
        try {
            HomeConfigViewModel model = provider(first).get(HomeConfigViewModel.class);
            model.failed();
            assertSame(model, provider(first).get(HomeConfigViewModel.class));
            assertEquals(FAILED, provider(first).get(HomeConfigViewModel.class).state().getValue());
            assertEquals(IDLE, provider(other).get(HomeConfigViewModel.class).state().getValue());
        } finally {
            first.clear();
            other.clear();
        }
    }

    private static ViewModelProvider provider(ViewModelStore store) {
        return new ViewModelProvider(store, new ViewModelProvider.NewInstanceFactory());
    }

    private static final class Owner implements LifecycleOwner {
        private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);
        @Override public Lifecycle getLifecycle() { return lifecycle; }
        void state(Lifecycle.State state) { lifecycle.setCurrentState(state); }
    }
}
