package com.fongmi.android.tv.player.engine;

import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

public class ExoPlayerEngineSeekTest {
    @Test public void preparingSeekUpdatesSnapshotUsedByImmediateReload() throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field instance = unsafe.getDeclaredField("theUnsafe");
        instance.setAccessible(true);
        ExoPlayerEngine engine = (ExoPlayerEngine) unsafe.getMethod("allocateInstance", Class.class)
                .invoke(instance.get(null), ExoPlayerEngine.class);
        long[] position = {10_000};
        ExoPlayer player = (ExoPlayer) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ExoPlayer.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "seekTo": position[0] = (Long) args[args.length - 1]; return null;
                case "getCurrentPosition": case "getBufferedPosition": return position[0];
                case "getDuration": return 100_000L;
                case "isPlaying": return false;
                case "getPlaybackParameters": return PlaybackParameters.DEFAULT;
                case "getVideoSize": return VideoSize.UNKNOWN;
                default: return null;
            }
        });
        set(engine, "player", player);
        set(engine, "phase", EnginePhase.PREPARING);
        set(engine, "requestedStartPosition", 10_000L);
        set(engine, "pendingStartSeek", true);

        engine.seekTo(40_000);

        assertEquals(40_000, position[0]);
        assertEquals(40_000, engine.snapshot().getPosition());
        engine.seekTo(-1);
        assertEquals(0, engine.snapshot().getPosition());
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
