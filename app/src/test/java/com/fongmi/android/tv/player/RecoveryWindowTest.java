package com.fongmi.android.tv.player;

import com.fongmi.android.tv.utils.TaskScheduler;
import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class RecoveryWindowTest {
    private static class Scheduler implements TaskScheduler {
        Map<Runnable, Long> tasks = new LinkedHashMap<>();
        public void post(Runnable task, long delay) { tasks.put(task, delay); }
        public void remove(Runnable task) { tasks.remove(task); }
        void run(long delay) {
            for (Runnable task : tasks.keySet().toArray(new Runnable[0])) {
                if (tasks.get(task) == delay) { tasks.remove(task); task.run(); }
            }
        }
    }
    @Test public void retriesCannotExtendDeadline() {
        Scheduler scheduler = new Scheduler();
        int[] expired = {0};
        RecoveryWindow window = new RecoveryWindow(scheduler, () -> expired[0]++);
        assertTrue(window.begin());
        Runnable deadline = scheduler.tasks.keySet().iterator().next();
        assertTrue(window.begin());
        assertEquals(1, scheduler.tasks.size());
        assertSame(deadline, scheduler.tasks.keySet().iterator().next());
        scheduler.run(45_000);
        assertEquals(1, expired[0]);
        assertFalse(window.begin());
        window.reset();
        assertTrue(window.begin());
    }
    @Test public void cancelRejectsLateTimeoutAndRetry() {
        Scheduler scheduler = new Scheduler();
        int[] expired = {0};
        RecoveryWindow window = new RecoveryWindow(scheduler, () -> expired[0]++);
        window.begin();
        Runnable stale = scheduler.tasks.keySet().iterator().next();
        window.cancel();
        stale.run();
        assertEquals(0, expired[0]);
        assertFalse(window.begin());
        assertTrue(scheduler.tasks.isEmpty());
    }
    @Test public void briefReadyDoesNotResetBudget() {
        Scheduler scheduler = new Scheduler();
        RecoveryWindow window = new RecoveryWindow(scheduler, () -> {});
        window.begin();
        window.ready();
        window.unstable();
        scheduler.run(5_000);
        assertTrue(window.isActive());
        window.ready();
        scheduler.run(5_000);
        assertFalse(window.isActive());
        assertTrue(scheduler.tasks.isEmpty());
    }
}
