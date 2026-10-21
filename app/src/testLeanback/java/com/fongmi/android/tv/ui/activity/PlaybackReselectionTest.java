package com.fongmi.android.tv.ui.activity;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.player.vod.TestVodFlow;
import com.fongmi.android.tv.player.vod.VodPlaybackPhase;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

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

    @Test
    public void takingControlAfterReadyDoesNotStopSuccessfulPlayback() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playback.recovery.onPhase(VodPlaybackPhase.READY);
        Method takeControl = VideoActivity.class.getDeclaredMethod("takePlaybackControl");
        takeControl.setAccessible(true);

        takeControl.invoke(fixture.activity);

        assertFalse(fixture.playback.recovery.isActive());
        assertTrue(fixture.tasks.isEmpty());
    }


    @Test
    public void clickingCurrentParserDoesNotInterruptRecovery() throws Exception {
        Fixture fixture = new Fixture();
        Parse current = new Parse();
        current.setActivated(true);
        Method click = VideoActivity.class.getDeclaredMethod("onParseClick", Parse.class);
        click.setAccessible(true);

        click.invoke(fixture.activity, current);

        assertTrue(fixture.playback.recovery.isActive());
        assertEquals(1, fixture.tasks.size());
        assertFalse(fixture.activity.isFullscreen());
    }

    @Test
    public void clickingCurrentRouteDoesNotInterruptRecovery() throws Exception {
        Fixture fixture = new Fixture();
        Flag current = new Flag();
        current.setActivated(current);
        Method click = VideoActivity.class.getDeclaredMethod("onFlagClick", Flag.class);
        click.setAccessible(true);

        click.invoke(fixture.activity, current);
        click.invoke(fixture.activity, current);

        assertTrue(fixture.playback.recovery.isActive());
        assertEquals(1, fixture.tasks.size());
        assertTrue(fixture.activity.isFullscreen());
        assertEquals(1, fixture.activity.fullscreenEntries);
    }

    @Test
    public void clickingCurrentEpisodeDoesNotInterruptRecovery() throws Exception {
        Fixture fixture = new Fixture();
        Episode current = new Episode();
        current.setActivated(true);

        fixture.activity.setEpisodeActivated(current);
        fixture.activity.setEpisodeActivated(current);

        assertTrue(fixture.playback.recovery.isActive());
        assertEquals(1, fixture.tasks.size());
        assertTrue(fixture.activity.isFullscreen());
        assertEquals(1, fixture.activity.fullscreenEntries);
    }

    private static final class Fixture {
        final TestVodFlow playback = new TestVodFlow();
        final Map<Runnable, Long> tasks = playback.tasks;
        final FullscreenVideoActivity activity;

        Fixture() throws Exception {
            Class<?> unsafe = Class.forName("sun.misc.Unsafe");
            Field instance = unsafe.getDeclaredField("theUnsafe");
            instance.setAccessible(true);
            activity = (FullscreenVideoActivity) unsafe.getMethod("allocateInstance", Class.class)
                    .invoke(instance.get(null), FullscreenVideoActivity.class);
            Field recovery = VideoActivity.class.getDeclaredField("mPlaybackFlow");
            recovery.setAccessible(true);
            recovery.set(activity, playback.flow);
        }
    }

    public static class FullscreenVideoActivity extends VideoActivity {
        int fullscreenEntries;
        @Override public boolean isFullscreen() { return fullscreenEntries > 0; }
        @Override void enterFullscreen() { fullscreenEntries++; }
    }
}
