package com.fongmi.android.tv.player;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.player.engine.EnginePhase;
import com.fongmi.android.tv.player.engine.EngineSnapshot;
import com.fongmi.android.tv.player.session.PlayerSession;
import com.google.gson.Gson;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class LiveReplayPlaybackTest {
    @Test public void finiteHlsReplayKeepsVodControls() throws Exception {
        boolean live = PlaybackController.shouldForceLive("https://media.example/program.m3u8", "", true);
        assertTrue(controller(live).isVod());
    }

    @Test public void dashAndExtensionlessReplayDoNotAcquireLiveHints() {
        assertFalse(PlaybackController.shouldForceLive("https://media.example/program.mpd", "dash", true));
        assertFalse(PlaybackController.shouldForceLive("https://media.example/program?id=1", "", true));
    }

    @Test public void replayWindowCanRemainSeekableEvenWithALiveManifest() {
        assertFalse(PlaybackController.resolveLiveFlag(false, true, true));
    }

    @Test public void ordinaryLiveHlsStillSuppressesVodControls() throws Exception {
        assertFalse(controller(PlaybackController.shouldForceLive("https://media.example/live.m3u8", "", false)).isVod());
        assertTrue(PlaybackController.resolveLiveFlag(false, false, true));
        assertFalse(PlaybackController.shouldForceLive("https://media.example/movie.mp4", "", false));
    }

    @Test public void replayIntentDoesNotChangePersistedChannelConfiguration() {
        Channel channel = new Channel();
        assertFalse(channel.isReplay());
        channel.setReplay(true);
        assertTrue(channel.isReplay());
        Gson gson = new Gson();
        String json = gson.toJson(channel);
        assertFalse(json.contains("replay"));
        assertFalse(gson.fromJson(json, Channel.class).isReplay());
    }

    private PlaybackController controller(boolean forceLive) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        PlaybackController controller = (PlaybackController) unsafe.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), PlaybackController.class);
        Field live = PlaybackController.class.getDeclaredField("forceLive"); live.setAccessible(true);
        live.setBoolean(controller, forceLive);
        PlayerSession session = (PlayerSession) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PlayerSession.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("snapshot")) return new EngineSnapshot(1, EnginePhase.READY, 1000, 3_600_000, 1000, true, 1, 1920, 1080);
                    throw new UnsupportedOperationException(method.getName());
                });
        Field playback = PlaybackController.class.getDeclaredField("engines"); playback.setAccessible(true);
        playback.set(controller, new PlaybackEngineHost(session));
        return controller;
    }
}
