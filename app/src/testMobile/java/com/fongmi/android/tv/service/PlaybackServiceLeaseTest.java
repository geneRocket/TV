package com.fongmi.android.tv.service;

import android.support.v4.media.session.MediaSessionCompat;
import com.fongmi.android.tv.player.PlaybackController;
import com.fongmi.android.tv.player.PlaybackOwners;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class PlaybackServiceLeaseTest {
    @Test public void oldServiceCannotRebindImplicitlyWhenTheSamePlayerReturnsToTheForeground() throws Exception {
        PlaybackController previous = PlaybackOwners.current();
        if (previous != null) PlaybackOwners.release(previous);
        PlaybackController player = allocate(PlaybackController.class);
        Object media = allocate(Class.forName("com.fongmi.android.tv.player.MediaSessionController"));
        set(media, "session", allocate(MediaSessionCompat.class));
        set(player, "mediaSession", media);
        PlaybackService service = allocate(PlaybackService.class);
        Method valid = PlaybackService.class.getDeclaredMethod("nonNull"); valid.setAccessible(true);
        try {
            PlaybackOwners.acquire(player);
            set(service, "player", player);
            set(service, "ownerLease", PlaybackOwners.lease(player));
            assertTrue((Boolean) valid.invoke(service));
            PlaybackOwners.release(player);
            assertFalse((Boolean) valid.invoke(service));
            PlaybackOwners.acquire(player);
            assertFalse("Same object has a different playback lease", (Boolean) valid.invoke(service));
            set(service, "ownerLease", PlaybackOwners.lease(player));
            assertTrue("Explicit service start may bind the new lease", (Boolean) valid.invoke(service));
        } finally {
            PlaybackOwners.release(player);
            if (previous != null) PlaybackOwners.acquire(previous);
        }
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field instance = unsafe.getDeclaredField("theUnsafe"); instance.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(instance.get(null), type));
    }
}
