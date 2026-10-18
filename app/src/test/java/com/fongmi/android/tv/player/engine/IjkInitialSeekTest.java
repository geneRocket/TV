package com.fongmi.android.tv.player.engine;

import com.fongmi.android.tv.utils.TaskScheduler;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;
import static org.junit.Assert.*;

public class IjkInitialSeekTest {
    @Test public void preparedCallbackSeeksToInitialPositionOnlyOnce() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.view.onPrepared(fixture.nativePlayer);
        assertEquals(Collections.singletonList(12_500L), fixture.seeks);
        assertEquals(EnginePhase.READY, fixture.engine.snapshot().getPhase());
        assertEquals(1, fixture.starts);
    }

    @Test public void seekDuringPreparationUsesTheLatestPositionOnlyOnce() throws Exception {
        Fixture fixture = new Fixture(true);
        fixture.engine.seekTo(42_000L);
        fixture.view.onPrepared(fixture.nativePlayer);
        assertEquals(Collections.singletonList(42_000L), fixture.seeks);
        assertEquals(1, fixture.starts);
    }

    @Test public void pausedPreparationAppliesPositionWithoutStartingPlayback() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.view.onPrepared(fixture.nativePlayer);
        assertEquals(Collections.singletonList(12_500L), fixture.seeks);
        assertEquals(EnginePhase.PAUSED, fixture.engine.snapshot().getPhase());
        assertEquals(0, fixture.starts);
    }

    private static final class Fixture {
        final TestView view;
        final IMediaPlayer nativePlayer;
        final IjkPlayerEngine engine;
        final List<Long> seeks = new ArrayList<>();
        int starts;

        Fixture(boolean playWhenReady) throws Exception {
            Class<?> allocator = Class.forName("sun.misc.Unsafe");
            Field singleton = allocator.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            view = (TestView) allocator.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), TestView.class);
            nativePlayer = (IMediaPlayer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IMediaPlayer.class}, (proxy, method, args) -> {
                if (method.getName().equals("seekTo")) seeks.add(((Number) args[0]).longValue());
                if (method.getName().equals("start")) starts++;
                if (method.getName().equals("getTrackInfo")) return Collections.emptyList();
                Class<?> type = method.getReturnType();
                if (type == int.class) return 0;
                if (type == long.class) return 0L;
                if (type == float.class) return 1f;
                if (type == boolean.class) return false;
                return null;
            });
            field(view, IjkVideoView.class, "mPlayer", nativePlayer);
            engine = new IjkPlayerEngine(view, 1, 0, 0, new TaskScheduler() {
                @Override public void post(Runnable task, long delay) {}
                @Override public void remove(Runnable task) {}
            });
            // Reproduce the state after setMediaSource without Android View/native construction.
            // The actual View.onPrepared, View.seekTo and engine listener callbacks execute.
            field(view, IjkVideoView.class, "mCurrentState", IjkVideoView.STATE_PREPARING);
            field(view, IjkVideoView.class, "mTargetState", playWhenReady ? IjkVideoView.STATE_PLAYING : IjkVideoView.STATE_PAUSED);
            field(view, IjkVideoView.class, "mStartPosition", 12_500L);
            field(engine, IjkPlayerEngine.class, "requestedStartPosition", 12_500L);
            field(engine, IjkPlayerEngine.class, "pendingStartSeek", true);
            field(engine, IjkPlayerEngine.class, "pendingReady", true);
            field(engine, IjkPlayerEngine.class, "playWhenReady", playWhenReady);
            field(engine, IjkPlayerEngine.class, "phase", EnginePhase.PREPARING);
        }
    }

    private static void field(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class TestView extends IjkVideoView {
        TestView() { super(null); }
        @Override public IjkVideoView render(int render) { return this; }
        @Override public void setPlayer(int type) {}
    }
}
