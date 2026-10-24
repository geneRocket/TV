package com.fongmi.android.tv.player;

import com.fongmi.android.tv.player.session.PlayerSession;
import org.junit.Test;
import java.lang.reflect.Proxy;
import static org.junit.Assert.*;

public class PlaybackEngineHostTest {
    @Test public void temporaryReleaseAllowsRebuildButFinalDisposalIsTerminal() {
        PlaybackEngineHost host = new PlaybackEngineHost();
        host.releaseEngine();
        host.initialize(PlayerType.EXO, 0, ignored -> {});
        assertFalse(host.isClosed());
        host.close();
        assertTrue(host.isClosed());
        try { host.initialize(PlayerType.EXO, 0, ignored -> {}); fail("Disposed host was reopened"); }
        catch (IllegalStateException expected) { assertEquals("engine host is closed", expected.getMessage()); }
        try { host.views(null, null); fail("Disposed host accepted views"); }
        catch (IllegalStateException expected) { assertEquals("engine host is closed", expected.getMessage()); }
    }
    @Test public void teardownDetachesTheSessionBeforeReleasingItAndIsIdempotent() {
        int[] released = {0};
        PlaybackEngineHost[] host = new PlaybackEngineHost[1];
        PlayerSession session = (PlayerSession) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PlayerSession.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("release")) {
                        assertNull(host[0].session());
                        released[0]++;
                        return null;
                    }
                    throw new AssertionError("Unexpected command " + method.getName());
                });
        host[0] = new PlaybackEngineHost(session);
        host[0].close(); host[0].close();
        assertEquals(1, released[0]);
    }
    @Test public void aNativeTeardownFailureCannotLeaveTheOldSessionAttached() {
        PlayerSession session = (PlayerSession) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PlayerSession.class},
                (proxy, method, args) -> { throw new IllegalStateException("native player unavailable"); });
        PlaybackEngineHost host = new PlaybackEngineHost(session);
        host.close();
        assertNull(host.session());
        assertNull(host.engine());
    }
}
