package com.fongmi.android.tv.ui.activity;

import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.player.vod.TestVodFlow;
import com.fongmi.android.tv.player.vod.VodPlaybackPhase;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class PlaybackReselectionTest {
    @Test public void bufferingUiUsesFlowSnapshotInsteadOfControllerCache() throws Exception {
        Fixture fixture = new Fixture();
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafe.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object player = unsafe.getMethod("allocateInstance", Class.class).invoke(singleton.get(null),
                com.fongmi.android.tv.player.PlaybackController.class);
        Field cache = player.getClass().getDeclaredField("playerState");
        cache.setAccessible(true);
        cache.setInt(player, androidx.media3.common.Player.STATE_BUFFERING);
        Field players = VideoActivity.class.getDeclaredField("mPlayers");
        players.setAccessible(true);
        players.set(fixture.activity, player);
        Method buffering = VideoActivity.class.getDeclaredMethod("isBuffering");
        buffering.setAccessible(true);
        assertEquals(VodPlaybackPhase.IDLE, fixture.playback.flow.state().getPhase());
        assertFalse((Boolean) buffering.invoke(fixture.activity));
    }

    @Test public void takingControlAfterReadyDoesNotStopSuccessfulPlayback() throws Exception {
        Fixture fixture = new Fixture();
        Method takeControl = VideoActivity.class.getDeclaredMethod("takePlaybackControl");
        takeControl.setAccessible(true);

        takeControl.invoke(fixture.activity);

        assertFalse(fixture.playback.recovery.isActive());
        assertTrue(fixture.playback.tasks.isEmpty());
        assertEquals(VodPlaybackPhase.IDLE, fixture.playback.coordinator.state().getPhase());
    }

    @Test public void currentRouteDoesNotStopPlaybackDuringRecoveryObservation() throws Exception {
        Fixture fixture = new Fixture();
        Flag current = new Flag();
        current.setActivated(current);

        fixture.activity.onItemClick(current);

        assertTrue(fixture.playback.recovery.isActive());
        assertTrue(fixture.playback.tasks.containsValue(5_000L));
    }

    private static class Fixture {
        final VideoActivity activity;
        final TestVodFlow playback = new TestVodFlow();

        Fixture() throws Exception {
            Class<?> unsafe = Class.forName("sun.misc.Unsafe");
            Field instance = unsafe.getDeclaredField("theUnsafe");
            instance.setAccessible(true);
            activity = (VideoActivity) unsafe.getMethod("allocateInstance", Class.class)
                    .invoke(instance.get(null), VideoActivity.class);
            Field flow = VideoActivity.class.getDeclaredField("mPlaybackFlow");
            flow.setAccessible(true);
            flow.set(activity, playback.flow);
            playback.recovery.onPhase(VodPlaybackPhase.READY);
        }
    }
}
