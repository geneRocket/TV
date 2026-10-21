package com.fongmi.android.tv.ui.activity;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.player.RecoveryWindow;
import com.fongmi.android.tv.utils.TaskScheduler;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class PlaybackReselectionTest {
    @Test
    public void takingControlAfterReadyDoesNotStopSuccessfulPlayback() throws Exception {
        Fixture fixture = new Fixture();
        fixture.window.ready();
        Field recovery = VideoActivity.class.getDeclaredField("mRecovery");
        recovery.setAccessible(true);
        recovery.set(fixture.activity, new com.fongmi.android.tv.player.PlaybackRecovery());
        Method takeControl = VideoActivity.class.getDeclaredMethod("takePlaybackControl");
        takeControl.setAccessible(true);

        takeControl.invoke(fixture.activity);

        assertFalse(fixture.window.isActive());
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

        assertTrue(fixture.window.isActive());
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

        assertTrue(fixture.window.isActive());
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

        assertTrue(fixture.window.isActive());
        assertEquals(1, fixture.tasks.size());
        assertTrue(fixture.activity.isFullscreen());
        assertEquals(1, fixture.activity.fullscreenEntries);
    }

    private static final class Fixture {
        final Map<Runnable, Long> tasks = new LinkedHashMap<>();
        final RecoveryWindow window = new RecoveryWindow(new TaskScheduler() {
            @Override public void post(Runnable task, long delay) { tasks.put(task, delay); }
            @Override public void remove(Runnable task) { tasks.remove(task); }
        }, () -> {});
        final FullscreenVideoActivity activity;

        Fixture() throws Exception {
            Class<?> unsafe = Class.forName("sun.misc.Unsafe");
            Field instance = unsafe.getDeclaredField("theUnsafe");
            instance.setAccessible(true);
            activity = (FullscreenVideoActivity) unsafe.getMethod("allocateInstance", Class.class)
                    .invoke(instance.get(null), FullscreenVideoActivity.class);
            Field recovery = VideoActivity.class.getDeclaredField("mRecoveryWindow");
            recovery.setAccessible(true);
            recovery.set(activity, window);
            window.begin();
        }
    }

    public static class FullscreenVideoActivity extends VideoActivity {
        int fullscreenEntries;
        @Override public boolean isFullscreen() { return fullscreenEntries > 0; }
        @Override void enterFullscreen() { fullscreenEntries++; }
    }
}
