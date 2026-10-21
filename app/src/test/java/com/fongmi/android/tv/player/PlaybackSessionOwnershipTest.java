package com.fongmi.android.tv.player;

import com.fongmi.android.tv.player.session.PlaybackPhase;
import com.fongmi.android.tv.player.session.PlaybackState;
import com.fongmi.android.tv.player.session.PlayerSession;
import com.fongmi.android.tv.server.Server;

import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import static org.junit.Assert.*;

public class PlaybackSessionOwnershipTest {
    @Test public void callbackCannotUseAnotherOwnerDelegateOrPrepareGeneration() throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field field = unsafe.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        PlaybackController controller = (PlaybackController) unsafe.getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), PlaybackController.class);
        PlaybackState[] state = {new PlaybackState(7, PlaybackPhase.PAUSED, null, 0, 1, null)};
        PlayerSession session = (PlayerSession) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PlayerSession.class},
                (proxy, method, args) -> method.getName().equals("state") ? state[0] : null);
        Field delegate = PlaybackController.class.getDeclaredField("playbackSession");
        delegate.setAccessible(true);
        delegate.set(controller, session);
        PlaybackController previous = Server.get().getPlayer();
        try {
            Server.get().setPlayer(controller);
            assertTrue(controller.ownsSession(session, 7));
            state[0] = new PlaybackState(8, PlaybackPhase.PREPARING, null, 0, 1, null);
            assertFalse(controller.ownsSession(session, 7));
            state[0] = new PlaybackState(7, PlaybackPhase.RELEASED, null, 0, 1, null);
            assertFalse(controller.ownsSession(session, 7));
            state[0] = new PlaybackState(7, PlaybackPhase.PAUSED, null, 0, 1, null);
            delegate.set(controller, null);
            assertFalse(controller.ownsSession(session, 7));
            delegate.set(controller, session);
            Server.get().setPlayer(null);
            assertFalse(controller.ownsSession(session, 7));
        } finally { Server.get().setPlayer(previous); }
    }
}
