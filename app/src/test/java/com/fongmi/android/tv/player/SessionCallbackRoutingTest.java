package com.fongmi.android.tv.player;

import com.fongmi.android.tv.impl.SessionCallback;
import com.fongmi.android.tv.server.Server;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SessionCallbackRoutingTest {
    @Test public void allTransportCommandsUseBoundHandlerAndIgnoreLostOrReleasedOwners() throws Exception {
        PlaybackController controller = allocate(PlaybackController.class);
        SessionCallback callback = allocate(SessionCallback.class);
        Field bound = SessionCallback.class.getDeclaredField("player");
        bound.setAccessible(true);
        bound.set(callback, controller);
        List<PlaybackInput.Type> inputs = new ArrayList<>();
        controller.setInputHandler(input -> inputs.add(input.getType()));
        PlaybackController previous = Server.get().getPlayer();
        try {
            Server.get().setPlayer(controller);
            callback.onPlay(); callback.onPause(); callback.onSeekTo(40_000);
            callback.onSkipToNext(); callback.onSkipToPrevious(); callback.onStop();
            assertEquals(Arrays.asList(PlaybackInput.Type.PLAY, PlaybackInput.Type.PAUSE, PlaybackInput.Type.SEEK,
                    PlaybackInput.Type.NEXT, PlaybackInput.Type.PREVIOUS, PlaybackInput.Type.STOP), inputs);
            Server.get().setPlayer(null);
            callback.onPlay(); callback.onPause(); callback.onSeekTo(1);
            callback.onSkipToNext(); callback.onSkipToPrevious(); callback.onStop();
            assertEquals(6, inputs.size());
            controller.setInputHandler(null);
            callback.onPlay(); callback.onPause(); callback.onSeekTo(1);
            callback.onSkipToNext(); callback.onSkipToPrevious(); callback.onStop();
            assertEquals(6, inputs.size());
        } finally { Server.get().setPlayer(previous); }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field instance = unsafe.getDeclaredField("theUnsafe");
        instance.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(instance.get(null), type));
    }
}
