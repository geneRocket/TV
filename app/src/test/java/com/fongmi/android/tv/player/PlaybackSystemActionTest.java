package com.fongmi.android.tv.player;

import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class PlaybackSystemActionTest {
    @Test public void notificationDismissalAndExplicitStopHaveDifferentForegroundSemantics() throws Exception {
        PlaybackController previous = PlaybackOwners.current();
        if (previous != null) PlaybackOwners.release(previous);
        PlaybackController owner = allocate();
        int[] stops = {0}; boolean[] foreground = {true};
        owner.setInputHandler(input -> { if (input.getType() == PlaybackInput.Type.STOP) stops[0]++; });
        owner.setForegroundSource(() -> foreground[0]);
        try {
            PlaybackOwners.acquire(owner);
            long lease = PlaybackOwners.lease(owner);
            PlaybackInput stop = PlaybackInput.action(PlaybackInput.Type.STOP);
            PlaybackOwners.dispatchSystem(lease, stop, true);
            assertEquals(0, stops[0]);
            PlaybackOwners.dispatchSystem(lease, stop, false);
            assertEquals(1, stops[0]);
            foreground[0] = false;
            PlaybackOwners.dispatchSystem(lease, stop, true);
            assertEquals(2, stops[0]);
        } finally {
            PlaybackOwners.release(owner);
            if (previous != null) PlaybackOwners.acquire(previous);
        }
    }
    @Test public void resumedControllerDoesNotAcceptItsPreviousNotificationsLease() throws Exception {
        PlaybackController previous = PlaybackOwners.current();
        if (previous != null) PlaybackOwners.release(previous);
        PlaybackController owner = allocate(); int[] commands = {0};
        owner.setInputHandler(input -> commands[0]++);
        try {
            PlaybackOwners.acquire(owner); long old = PlaybackOwners.lease(owner);
            PlaybackOwners.release(owner); PlaybackOwners.acquire(owner);
            assertFalse(PlaybackOwners.dispatchSystem(old, PlaybackInput.action(PlaybackInput.Type.STOP), true));
            assertEquals(0, commands[0]);
            assertTrue(PlaybackOwners.dispatch(PlaybackOwners.lease(owner), PlaybackInput.action(PlaybackInput.Type.PAUSE)));
            assertEquals(1, commands[0]);
        } finally {
            PlaybackOwners.release(owner);
            if (previous != null) PlaybackOwners.acquire(previous);
        }
    }
    private static PlaybackController allocate() throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field instance = unsafe.getDeclaredField("theUnsafe"); instance.setAccessible(true);
        return (PlaybackController) unsafe.getMethod("allocateInstance", Class.class).invoke(instance.get(null), PlaybackController.class);
    }
}
