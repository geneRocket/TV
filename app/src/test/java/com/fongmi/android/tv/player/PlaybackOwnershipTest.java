package com.fongmi.android.tv.player;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class PlaybackOwnershipTest {
    @Test public void deletingAnOldNotificationCannotStopTheNewOwnerOrAResumedOldOwner() {
        PlaybackOwnership<Object> owners = new PlaybackOwnership<>(ignored -> {});
        Object vod = new Object(), live = new Object();
        owners.acquire(vod);
        long oldVodNotification = owners.lease(vod);
        owners.acquire(live);
        long liveNotification = owners.lease(live);
        int[] stopped = {0};
        assertFalse(owners.dispatch(oldVodNotification, owner -> stopped[0]++));
        owners.acquire(vod);
        assertFalse(owners.dispatch(liveNotification, owner -> stopped[0]++));
        assertFalse(owners.dispatch(oldVodNotification, owner -> stopped[0]++));
        assertEquals(0, stopped[0]);
        assertTrue(owners.dispatch(owners.lease(vod), owner -> {
            assertSame(vod, owner); stopped[0]++;
        }));
        assertEquals(1, stopped[0]);
    }

    @Test public void unboundOrReleasedNotificationActionsAreRejected() {
        PlaybackOwnership<Object> owners = new PlaybackOwnership<>(ignored -> {});
        Object owner = new Object(); owners.acquire(owner);
        long lease = owners.lease(owner);
        assertEquals(0, owners.lease(new Object()));
        assertFalse(owners.dispatch(0, ignored -> fail("unbound action")));
        owners.release(owner);
        assertFalse(owners.dispatch(lease, ignored -> fail("released action")));
    }
    @Test public void acquiringPublishesTheNewOwnerBeforeRevokingTheOldOne() {
        List<String> events = new ArrayList<>();
        @SuppressWarnings("unchecked") PlaybackOwnership<String>[] owners = new PlaybackOwnership[1];
        owners[0] = new PlaybackOwnership<>(old -> {
            assertFalse(owners[0].owns(old));
            events.add(old + "->" + owners[0].current());
        });
        String vod = new String("vod"), cast = new String("cast");
        owners[0].acquire(vod);
        owners[0].acquire(cast);
        assertEquals(List.of("vod->cast"), events);
        assertTrue(owners[0].owns(cast));
    }

    @Test public void resumeOfCurrentOwnerDoesNotRevokeOrReloadIt() {
        List<String> revoked = new ArrayList<>();
        PlaybackOwnership<String> owners = new PlaybackOwnership<>(revoked::add);
        String vod = new String("vod");
        owners.acquire(vod); owners.acquire(vod); owners.acquire(vod);
        assertTrue(revoked.isEmpty());
    }

    @Test public void stalePageClosingCannotClearTheNewOwnersRegistration() {
        PlaybackOwnership<Object> owners = new PlaybackOwnership<>(ignored -> {});
        Object old = new Object(), current = new Object();
        owners.acquire(old); owners.acquire(current);
        assertFalse(owners.release(old));
        assertSame(current, owners.current());
        assertTrue(owners.release(current));
        assertNull(owners.current());
        assertFalse(owners.release(current));
    }

    @Test public void reentrantTakeoverCannotResurrectTheIntermediateOwner() {
        @SuppressWarnings("unchecked") PlaybackOwnership<Object>[] owners = new PlaybackOwnership[1];
        Object first = new Object(), second = new Object(), third = new Object();
        List<Object> revoked = new ArrayList<>();
        owners[0] = new PlaybackOwnership<>(old -> {
            revoked.add(old);
            if (old == first) owners[0].acquire(third);
        });
        owners[0].acquire(first); owners[0].acquire(second);
        assertSame(third, owners[0].current());
        assertEquals(List.of(first, second), revoked);
    }

    @Test public void failedCleanupNeverRestoresTheRevokedOwnersAuthority() {
        PlaybackOwnership<Object> owners = new PlaybackOwnership<>(old -> { throw new IllegalStateException("cleanup failed"); });
        Object first = new Object(), second = new Object();
        owners.acquire(first);
        try { owners.acquire(second); fail("Cleanup failure should be observable"); }
        catch (IllegalStateException expected) { assertEquals("cleanup failed", expected.getMessage()); }
        assertSame(second, owners.current());
        assertFalse(owners.owns(first));
    }
}
