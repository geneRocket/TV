package com.fongmi.android.tv.player;

import com.fongmi.android.tv.utils.TaskScheduler;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.fongmi.android.tv.player.RecoveryWindow.Phase.*;
import static org.junit.Assert.assertEquals;

public class RecoveryWindowStateMachineTest {
    private enum Event { BEGIN, READY, UNSTABLE, CANCEL, RESET, STABLE_TIMEOUT, DEADLINE }

    // Rows: IDLE, RECOVERING, STABILIZING, RECOVERED, CANCELLED. Columns: Event order.
    private static final RecoveryWindow.Phase[][] TRANSITIONS = {
            {RECOVERING, IDLE, IDLE, CANCELLED, IDLE, IDLE, IDLE},
            {RECOVERING, STABILIZING, RECOVERING, CANCELLED, IDLE, RECOVERING, CANCELLED},
            {RECOVERING, STABILIZING, RECOVERED, CANCELLED, IDLE, IDLE, IDLE},
            {RECOVERING, STABILIZING, RECOVERED, CANCELLED, IDLE, RECOVERED, IDLE},
            {CANCELLED, CANCELLED, CANCELLED, CANCELLED, IDLE, CANCELLED, CANCELLED}
    };

    @Test public void allFourEventSequencesRespectRecoveryAndBackInvariants() {
        int sequences = 1;
        for (int i = 0; i < 4; i++) sequences *= Event.values().length;
        for (RecoveryWindow.Phase initial : RecoveryWindow.Phase.values()) {
            for (int code = 0; code < sequences; code++) {
                Scheduler scheduler = new Scheduler();
                int[] expirations = {0};
                RecoveryWindow window = new RecoveryWindow(scheduler, () -> expirations[0]++);
                if (initial == CANCELLED) window.cancel();
                else if (initial != IDLE) {
                    window.begin();
                    if (initial != RECOVERING) window.ready();
                    if (initial == RECOVERED) window.unstable();
                }
                RecoveryWindow.Phase expected = initial;
                int expectedExpirations = 0;
                int remaining = code;
                for (int step = 0; step < 4; step++) {
                    Event event = Event.values()[remaining % Event.values().length];
                    remaining /= Event.values().length;
                    if (event == Event.DEADLINE && expected == RECOVERING) expectedExpirations++;
                    String context = initial + " sequence=" + code + " step=" + step + " " + event;
                    switch (event) {
                        case BEGIN: assertEquals(context, expected != CANCELLED, window.begin()); break;
                        case READY: window.ready(); break;
                        case UNSTABLE: window.unstable(); break;
                        case CANCEL: window.cancel(); break;
                        case RESET: window.reset(); break;
                        case STABLE_TIMEOUT: scheduler.run(5_000); break;
                        case DEADLINE: scheduler.run(45_000); break;
                    }
                    expected = TRANSITIONS[expected.ordinal()][event.ordinal()];
                    assertEquals(context, expected, window.getPhase());
                    assertEquals(context, expected == RECOVERING, window.isRecovering());
                    assertEquals(context, expectedExpirations, expirations[0]);
                    int timers = expected == STABILIZING ? 2 : expected == RECOVERING || expected == RECOVERED ? 1 : 0;
                    assertEquals(context, timers, scheduler.tasks.size());
                }
            }
        }
    }

    private static final class Scheduler implements TaskScheduler {
        final Map<Runnable, Long> tasks = new LinkedHashMap<>();
        @Override public void post(Runnable task, long delay) { tasks.put(task, delay); }
        @Override public void remove(Runnable task) { tasks.remove(task); }
        void run(long delay) {
            for (Runnable task : tasks.keySet().toArray(new Runnable[0])) {
                if (Long.valueOf(delay).equals(tasks.get(task))) {
                    tasks.remove(task);
                    task.run();
                }
            }
        }
    }
}
