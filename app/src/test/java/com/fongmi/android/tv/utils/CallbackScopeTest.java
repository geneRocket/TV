package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CallbackScopeTest {
    @Test public void resetAndCloseRejectCallbacksEvenIfDeliveryWasAlreadyQueued() {
        List<Runnable> queued = new ArrayList<>();
        CallbackScope scope = new CallbackScope(new TaskScheduler() {
            @Override public void post(Runnable task, long delay) { queued.add(task); }
            @Override public void remove(Runnable task) { queued.remove(task); }
        });
        int[] calls = {0};
        scope.post(() -> calls[0]++);
        Runnable stale = queued.get(0);
        long ticket = scope.capture();
        scope.reset();
        stale.run();
        assertFalse(scope.accepts(ticket));
        assertTrue(queued.isEmpty());
        assertEquals(0, calls[0]);
        scope.post(() -> calls[0]++);
        Runnable afterReset = queued.get(0);
        queued.remove(afterReset);
        afterReset.run();
        assertEquals(1, calls[0]);
        scope.close();
        afterReset.run();
        assertEquals(1, calls[0]);
        scope.post(() -> calls[0]++);
        assertTrue(queued.isEmpty());
    }

    @Test public void removingAnOriginalCancelsAllOfItsPendingDeliveries() {
        List<Runnable> queued = new ArrayList<>();
        CallbackScope scope = new CallbackScope(new TaskScheduler() {
            @Override public void post(Runnable task, long delay) { queued.add(task); }
            @Override public void remove(Runnable task) { queued.remove(task); }
        });
        Runnable original = () -> {};
        scope.post(original, 1);
        scope.post(original, 2);
        Runnable alreadyDequeued = queued.get(0);
        assertEquals(2, queued.size());
        scope.remove(original);
        alreadyDequeued.run();
        assertTrue(queued.isEmpty());
    }
}
