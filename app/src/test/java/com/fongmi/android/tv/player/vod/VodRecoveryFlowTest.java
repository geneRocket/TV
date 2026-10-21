package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.player.RecoveryWindow;
import com.fongmi.android.tv.utils.TaskScheduler;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class VodRecoveryFlowTest {
    @Test public void userControlAfterReadyKeepsPlaybackAndStartsFreshBudget() {
        Fixture f = new Fixture();
        f.recovery.recover(ErrorEvent.url(1));
        f.recovery.onPhase(VodPlaybackPhase.READY);
        assertFalse(f.recovery.isRecovering());
        f.calls.clear();

        f.recovery.takeControl();

        assertTrue(f.calls.isEmpty());
        assertEquals(VodPlaybackPhase.IDLE, f.coordinator.state().getPhase());
        assertEquals(RecoveryWindow.Phase.IDLE, f.recovery.phase());
        assertTrue(f.tasks.isEmpty());
        f.recovery.recover(ErrorEvent.url(1));
        assertTrue(f.recovery.isRecovering());
    }

    @Test public void sourceFallbackKeepsDeadlineAndExhaustionStopsRecovery() {
        Fixture f = new Fixture();
        f.work = true;
        f.recovery.recover(ErrorEvent.url(0));
        assertEquals(java.util.Arrays.asList("started", "stopWithError", "selectNextParse", "resetParse", "selectNextFlag", "recoverSource"), f.calls);
        Runnable deadline = f.tasks.keySet().iterator().next();
        f.recovery.recover(ErrorEvent.url(0));
        assertEquals(1, f.tasks.size());
        assertSame(deadline, f.tasks.keySet().iterator().next());

        deadline.run();

        assertEquals(RecoveryWindow.Phase.CANCELLED, f.recovery.phase());
        assertEquals(VodPlaybackPhase.STOPPED, f.coordinator.state().getPhase());
        assertTrue(f.calls.contains("cancelSourceWork"));
        assertTrue(f.calls.contains("cancelled"));
    }

    @Test public void noMoreSourceWorkDoesNotKeepBackKeyOrLateDeadline() {
        Fixture f = new Fixture();
        f.recovery.recover(ErrorEvent.url(0));
        assertEquals(RecoveryWindow.Phase.CANCELLED, f.recovery.phase());
        assertFalse(f.recovery.isRecovering());
        assertTrue(f.tasks.isEmpty());
    }

    @Test public void cancellationAndClosedFlowRejectFurtherErrors() {
        Fixture f = new Fixture();
        f.recovery.recover(ErrorEvent.url(1));
        f.recovery.cancel(false);
        f.calls.clear();
        f.recovery.recover(ErrorEvent.url(1));
        assertTrue(f.calls.isEmpty());
        f.recovery.reset();
        f.recovery.recover(ErrorEvent.url(1));
        assertTrue(f.calls.contains("retry"));
        f.recovery.close();
        f.calls.clear();
        f.recovery.recover(ErrorEvent.url(1));
        assertTrue(f.calls.isEmpty());
        assertTrue(f.tasks.isEmpty());
    }

    private static class Fixture {
        final TestVodFlow core = new TestVodFlow();
        final VodPlaybackCoordinator coordinator = core.coordinator;
        final Map<Runnable, Long> tasks = new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();
        boolean work;
        final VodRecoveryFlow recovery = new VodRecoveryFlow(coordinator,
                (VodRecoveryFlow.Port) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{VodRecoveryFlow.Port.class}, (object, method, args) -> {
                    if (method.getName().equals("playerType")) return 2;
                    if (method.getReturnType() == boolean.class) {
                        switch (method.getName()) {
                            case "mayRecover": case "mayChangeSource": case "usesParse": return true;
                            case "hasRecoveryWork": return work;
                            default: calls.add(method.getName()); return false;
                        }
                    }
                    calls.add(method.getName());
                    return null;
                }), new TaskScheduler() {
                    @Override public void post(Runnable task, long delay) { tasks.put(task, delay); }
                    @Override public void remove(Runnable task) { tasks.remove(task); }
                });
    }
}
