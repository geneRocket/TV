package com.fongmi.android.tv.ui.activity;

import android.widget.LinearLayout;

import com.fongmi.android.tv.databinding.ActivityLiveBinding;
import com.fongmi.android.tv.databinding.ViewWidgetLiveBinding;
import com.fongmi.android.tv.player.PlaybackController;
import com.fongmi.android.tv.player.PlaybackSeekGesture;
import com.fongmi.android.tv.player.engine.EnginePhase;
import com.fongmi.android.tv.player.engine.EngineSnapshot;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.session.PlayerSession;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LiveSeekUiTest {

    @Test
    public void pausedReplaySeekDoesNotInvokePlayOrChangePlaybackControls() throws Exception {
        Fixture fixture = new Fixture();
        fixture.gesture.preview(10_000, 600_000, 5_000);

        fixture.activity.onSeekEnd(5_000);

        assertEquals(15_000, fixture.seekPosition);
        assertEquals(1, fixture.seeks);
        assertFalse(fixture.gesture.isActive());
        assertEquals(LinearLayout.GONE, fixture.seekView.visibility);
    }

    @Test
    public void cancelledReplaySeekOnlyDismissesPreview() throws Exception {
        Fixture fixture = new Fixture();
        fixture.gesture.preview(10_000, 600_000, 5_000);

        fixture.activity.onSeekCancel();

        assertEquals(0, fixture.seeks);
        assertFalse(fixture.gesture.isActive());
        assertEquals(LinearLayout.GONE, fixture.seekView.visibility);
    }

    @Test
    public void endingGestureAfterTimelineDisappearsStillDismissesPreview() throws Exception {
        Fixture fixture = new Fixture();
        fixture.gesture.preview(10_000, 600_000, 5_000);
        fixture.duration = -1;

        fixture.activity.onSeekEnd(5_000);

        assertEquals(0, fixture.seeks);
        assertFalse(fixture.gesture.isActive());
        assertEquals(LinearLayout.GONE, fixture.seekView.visibility);
    }

    private static final class Fixture {
        final LiveActivity activity = allocate(LiveActivity.class);
        final PlaybackSeekGesture gesture = new PlaybackSeekGesture();
        final SeekView seekView = allocate(SeekView.class);
        long duration = 600_000;
        long seekPosition;
        int seeks;

        Fixture() throws Exception {
            ActivityLiveBinding binding = allocate(ActivityLiveBinding.class);
            ViewWidgetLiveBinding widget = allocate(ViewWidgetLiveBinding.class);
            set(widget, "seek", seekView);
            set(binding, "widget", widget);
            set(activity, "mBinding", binding);
            set(activity, "mSeekGesture", gesture);
            PlaybackController controller = allocate(PlaybackController.class);
            set(controller, "danmaku", allocate(Class.forName("com.fongmi.android.tv.player.DanmakuController")));
            set(controller, "engine", Proxy.newProxyInstance(PlayerEngine.class.getClassLoader(), new Class<?>[]{PlayerEngine.class},
                    (proxy, method, args) -> { throw new AssertionError("Unexpected engine command " + method); }));
            set(controller, "playbackSession", Proxy.newProxyInstance(PlayerSession.class.getClassLoader(), new Class<?>[]{PlayerSession.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("snapshot")) return new EngineSnapshot(1, EnginePhase.PAUSED, 10_000, duration, 0, false, 1, 0, 0);
                        if (method.getName().equals("seekTo")) { seeks++; seekPosition = (Long) args[0]; return null; }
                        throw new AssertionError("Unexpected session command " + method);
                    }));
            set(activity, "mPlayers", controller);
        }
    }

    public static final class SeekView extends LinearLayout {
        int visibility;
        private SeekView() { super(null); }
        @Override public void setVisibility(int visibility) { this.visibility = visibility; }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field instance = unsafe.getDeclaredField("theUnsafe");
        instance.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(instance.get(null), type));
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
