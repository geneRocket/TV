package com.fongmi.android.tv.service;

import com.fongmi.android.tv.player.PlaybackController;
import com.fongmi.android.tv.ui.activity.LiveActivity;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class LiveServiceOwnershipTest {
    @Test public void delayedCleanupFromAnOldLivePageDoesNotStopAnotherPlayersService() throws Exception {
        // Skip Android constructors: only the actual cleanup handler and service-owner guard run.
        Class<?> allocator = Class.forName("sun.misc.Unsafe");
        Field singleton = allocator.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        Method allocate = allocator.getMethod("allocateInstance", Class.class);
        Object unsafe = singleton.get(null);
        PlaybackController oldPlayer = (PlaybackController) allocate.invoke(unsafe, PlaybackController.class);
        PlaybackController currentPlayer = (PlaybackController) allocate.invoke(unsafe, PlaybackController.class);
        LiveActivity oldPage = (LiveActivity) allocate.invoke(unsafe, LiveActivity.class);
        Field controller = LiveActivity.class.getDeclaredField("mPlayers"); controller.setAccessible(true);
        controller.set(oldPage, oldPlayer);
        Field servicePlayer = PlaybackService.class.getDeclaredField("player"); servicePlayer.setAccessible(true);
        Field serviceInstance = PlaybackService.class.getDeclaredField("instance"); serviceInstance.setAccessible(true);
        Object previous = serviceInstance.get(null);
        PlaybackService currentService = (PlaybackService) allocate.invoke(unsafe, PlaybackService.class);
        Method cleanup = LiveActivity.class.getDeclaredMethod("stopService"); cleanup.setAccessible(true);
        try {
            servicePlayer.set(currentService, currentPlayer);
            serviceInstance.set(null, new java.lang.ref.WeakReference<>(currentService));
            try { cleanup.invoke(oldPage); }
            catch (InvocationTargetException error) {
                throw new AssertionError("Stale live cleanup reached the global Android service-stop path", error.getCause());
            }
            org.junit.Assert.assertSame(currentPlayer, servicePlayer.get(currentService));
        } finally { serviceInstance.set(null, previous); }
    }
}
