package com.fongmi.android.tv.ui.activity;

import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.player.RecoveryWindow;
import com.fongmi.android.tv.player.PlaybackRecovery;
import com.fongmi.android.tv.player.session.PlaybackState;
import com.fongmi.android.tv.player.session.PlayerSession;
import com.fongmi.android.tv.player.session.Subscription;
import com.fongmi.android.tv.player.vod.VodPlaybackCoordinator;
import com.fongmi.android.tv.player.vod.VodPlaybackPhase;
import com.fongmi.android.tv.utils.TaskScheduler;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

public class PlaybackReselectionTest {
    @Test public void takingControlAfterReadyDoesNotStopSuccessfulPlayback() throws Exception {
        Fixture fixture = new Fixture();
        Field recovery = VideoActivity.class.getDeclaredField("mRecovery");
        recovery.setAccessible(true);
        recovery.set(fixture.activity, new PlaybackRecovery());
        PlayerSession session = (PlayerSession) Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class[]{PlayerSession.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("observe")) return (Subscription) () -> {};
                            if (method.getName().equals("state")) return PlaybackState.IDLE;
                            return null;
                        });
        VodPlaybackCoordinator coordinator = new VodPlaybackCoordinator(
                (request, callback) -> { throw new AssertionError("must not resolve"); },
                (episode, result, position) -> { throw new AssertionError("must not prepare"); }, session);
        Field playback = VideoActivity.class.getDeclaredField("mPlaybackCoordinator");
        playback.setAccessible(true);
        playback.set(fixture.activity, coordinator);
        Method takeControl = VideoActivity.class.getDeclaredMethod("takePlaybackControl");
        takeControl.setAccessible(true);

        takeControl.invoke(fixture.activity);

        assertFalse(fixture.window.isActive());
        assertTrue(fixture.tasks.isEmpty());
        assertEquals(VodPlaybackPhase.IDLE, coordinator.state().getPhase());
    }

    @Test public void currentRouteDoesNotStopPlaybackDuringRecoveryObservation() throws Exception {
        Fixture fixture = new Fixture();
        Flag current = new Flag();
        current.setActivated(current);

        fixture.activity.onItemClick(current);

        assertTrue(fixture.window.isActive());
        assertTrue(fixture.tasks.containsValue(5_000L));
    }

    private static class Fixture {
        final VideoActivity activity;
        final Map<Runnable, Long> tasks = new LinkedHashMap<>();
        final RecoveryWindow window = new RecoveryWindow(new TaskScheduler() {
            @Override public void post(Runnable task, long delay) { tasks.put(task, delay); }
            @Override public void remove(Runnable task) { tasks.remove(task); }
        }, () -> {});

        Fixture() throws Exception {
            Class<?> unsafe = Class.forName("sun.misc.Unsafe");
            Field instance = unsafe.getDeclaredField("theUnsafe");
            instance.setAccessible(true);
            activity = (VideoActivity) unsafe.getMethod("allocateInstance", Class.class)
                    .invoke(instance.get(null), VideoActivity.class);
            Field recovery = VideoActivity.class.getDeclaredField("mRecoveryWindow");
            recovery.setAccessible(true);
            recovery.set(activity, window);
            window.begin();
            window.ready();
        }
    }
}
