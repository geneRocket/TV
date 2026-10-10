package com.fongmi.android.tv.model;
import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class OneShotLiveDataTest {
    @Test public void recreatingObserverDoesNotRepeatConsumedDownloadCommand() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            public void executeOnDiskIO(Runnable r) { r.run(); }
            public void postToMainThread(Runnable r) { r.run(); }
            public boolean isMainThread() { return true; }
        });
        try {
            OneShotLiveData<String> events = new OneShotLiveData<>();
            AtomicInteger executions = new AtomicInteger();
            Owner first = new Owner();
            first.registry.setCurrentState(Lifecycle.State.STARTED);
            events.observe(first, value -> executions.incrementAndGet());
            events.setValue("episode-1");
            first.registry.setCurrentState(Lifecycle.State.DESTROYED);
            Owner replacement = new Owner();
            replacement.registry.setCurrentState(Lifecycle.State.STARTED);
            events.observe(replacement, value -> executions.incrementAndGet());
            assertEquals(1, executions.get());
            events.setValue("episode-1");
            assertEquals(2, executions.get());
            replacement.registry.setCurrentState(Lifecycle.State.DESTROYED);
        } finally { ArchTaskExecutor.getInstance().setDelegate(null); }
    }
    private static final class Owner implements LifecycleOwner {
        final LifecycleRegistry registry = new LifecycleRegistry(this);
        public Lifecycle getLifecycle() { return registry; }
    }
}
