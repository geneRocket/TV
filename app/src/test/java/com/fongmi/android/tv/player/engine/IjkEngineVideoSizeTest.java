package com.fongmi.android.tv.player.engine;

import com.fongmi.android.tv.player.session.DefaultPlayerSession;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

import static org.junit.Assert.*;

public class IjkEngineVideoSizeTest {
    @Test public void nativeSizeUpdateReachesThePlayerSessionSnapshot() throws Exception {
        Fixture fixture = new Fixture();
        fixture.size(1920, 1080);
        assertEquals(1920, fixture.view.getVideoWidth());
        assertEquals(1080, fixture.view.getVideoHeight());
        assertEquals(1920, fixture.session.snapshot().getVideoWidth());
        assertEquals(1080, fixture.session.snapshot().getVideoHeight());
    }

    @Test public void sessionSeesPortraitToLandscapeSizeChanges() throws Exception {
        Fixture fixture = new Fixture();
        fixture.size(720, 1280);
        EngineSnapshot portrait = fixture.session.snapshot();
        assertTrue(portrait.getVideoHeight() > portrait.getVideoWidth());
        fixture.size(1280, 720);
        EngineSnapshot landscape = fixture.session.snapshot();
        assertEquals(1280, landscape.getVideoWidth());
        assertEquals(720, landscape.getVideoHeight());
        assertFalse(landscape.getVideoHeight() > landscape.getVideoWidth());
    }

    @Test public void unknownVideoSizeRemainsZero() throws Exception {
        Fixture fixture = new Fixture();
        assertEquals(0, fixture.session.snapshot().getVideoWidth());
        assertEquals(0, fixture.session.snapshot().getVideoHeight());
    }

    private static final class Fixture {
        final TestView view;
        final IMediaPlayer mediaPlayer;
        final DefaultPlayerSession session;
        final int[] dimensions = new int[2];

        Fixture() throws Exception {
            // Bypass only Android View construction and native player creation. The actual
            // IjkVideoView size callback/getters, engine and session snapshot all execute.
            Class<?> allocator = Class.forName("sun.misc.Unsafe");
            Field singleton = allocator.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
            view = (TestView) allocator.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), TestView.class);
            mediaPlayer = (IMediaPlayer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IMediaPlayer.class}, (proxy, method, args) -> {
                if (method.getName().equals("getVideoWidth")) return dimensions[0];
                if (method.getName().equals("getVideoHeight")) return dimensions[1];
                Class<?> type = method.getReturnType();
                if (type == int.class) return 0;
                if (type == long.class) return 0L;
                if (type == float.class) return 1.0f;
                if (type == boolean.class) return false;
                return null;
            });
            Field player = IjkVideoView.class.getDeclaredField("mPlayer"); player.setAccessible(true); player.set(view, mediaPlayer);
            session = new DefaultPlayerSession(new IjkPlayerEngine(view, 1, 0, 0));
        }

        void size(int width, int height) {
            dimensions[0] = width;
            dimensions[1] = height;
            view.onVideoSizeChanged(mediaPlayer, width, height, 1, 1);
        }
    }

    private static class TestView extends IjkVideoView {
        TestView() { super(null); }
        @Override public IjkVideoView render(int render) { return this; }
        @Override public void setPlayer(int type) { }
    }
}
