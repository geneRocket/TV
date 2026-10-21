package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.player.engine.EngineSnapshot;
import com.fongmi.android.tv.player.session.PlaybackState;
import com.fongmi.android.tv.player.session.PlayerSession;
import com.fongmi.android.tv.player.session.Subscription;
import com.fongmi.android.tv.utils.TaskScheduler;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

/** Real flow/recovery ownership with inert platform effects for headless Activity wiring tests. */
public final class TestVodFlow implements VodPlaybackFlow.Port {
    public final Map<Runnable, Long> tasks = new LinkedHashMap<>();
    public final VodPlaybackCoordinator coordinator;
    public final VodPlaybackFlow flow;
    public final VodRecoveryFlow recovery;

    public TestVodFlow() {
        PlayerSession session = (PlayerSession) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PlayerSession.class}, (proxy, method, args) -> {
            if (method.getName().equals("observe")) return (Subscription) () -> {};
            if (method.getName().equals("state")) return PlaybackState.IDLE;
            if (method.getName().equals("snapshot")) return EngineSnapshot.EMPTY;
            return null;
        });
        coordinator = new VodPlaybackCoordinator((request, callback) -> { throw new AssertionError("must not resolve"); },
                (episode, result, position) -> { throw new AssertionError("must not prepare"); }, session);
        flow = new VodPlaybackFlow(coordinator, this);
        VodRecoveryFlow.Port port = (VodRecoveryFlow.Port) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{VodRecoveryFlow.Port.class}, (proxy, method, args) -> {
            if (method.getName().equals("playerType")) return 2;
            if (method.getReturnType() == boolean.class) return method.getName().equals("mayRecover");
            return null;
        });
        flow.installRecovery(port, new TaskScheduler() {
            @Override public void post(Runnable task, long delay) { tasks.put(task, delay); }
            @Override public void remove(Runnable task) { tasks.remove(task); }
        });
        recovery = flow.recovery();
        flow.onError(ErrorEvent.url(1));
    }
    @Override public boolean isCurrent() { return true; }
    @Override public float speed() { return 1; }
    @Override public boolean selectSubtitle(Sub subtitle) { return true; }
    @Override public void attachSubtitleSession() {}
    @Override public void selectPlayer(int player) {}
    @Override public void toggleDecode(boolean persist) {}
    @Override public void nextPlayer() {}
    @Override public void seekPresentation(long position) {}
    @Override public void navigated(boolean next, boolean moved) {}
    @Override public void finish() {}
}
