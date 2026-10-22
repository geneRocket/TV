package com.fongmi.android.tv.ui.player;

import android.content.ServiceConnection;
import android.os.IBinder;
import com.android.cast.dlna.dmr.RenderControl;
import com.android.cast.dlna.dmr.RenderState;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class CastRendererConnectionTest {
    @Test public void disconnectedServiceRemainsBoundUntilUnbind() {
        Fixture f = new Fixture(); f.connection.bind();
        f.connection.onServiceDisconnected(null); f.connection.close();
        assertEquals(1, f.unbinds);
    }
    @Test public void disconnectedServiceDoesNotCreateBinding() {
        Fixture f = new Fixture(); f.connection.onServiceDisconnected(null); f.connection.close();
        assertEquals(0, f.binds); assertEquals(0, f.unbinds);
    }
    @Test public void bindOnceAndPublishLatestStateWhenServiceArrives() {
        Fixture f = new Fixture();
        f.connection.bind(); f.connection.bind();
        assertEquals(1, f.binds);
        f.connection.state(RenderState.PAUSED);
        f.connection.onServiceConnected(null, null);
        assertSame(f.player, f.peer.players.get(0));
        assertEquals(RenderState.PAUSED, f.peer.states.get(0));
    }
    @Test public void closingDetachesPlayerAndUnbindsOnce() {
        Fixture f = new Fixture(); f.connection.bind(); f.connection.onServiceConnected(null, null);
        f.connection.close(); f.connection.close();
        assertEquals(1, f.unbinds);
        assertNull(f.peer.players.get(1));
        int states = f.peer.states.size();
        f.connection.state(RenderState.PLAYING);
        assertEquals(states, f.peer.states.size());
    }
    @Test public void lateConnectionAfterCloseCannotReattachDestroyedPage() {
        Fixture f = new Fixture(); f.connection.bind(); f.connection.close();
        f.connection.onServiceConnected(null, null); f.connection.bind();
        assertEquals(0, f.resolves); assertEquals(1, f.binds);
    }
    @Test public void disconnectedServiceGetsLatestStateOnReconnect() {
        Fixture f = new Fixture(); f.connection.bind(); f.connection.onServiceConnected(null, null);
        f.connection.onServiceDisconnected(null);
        f.connection.state(RenderState.PLAYING);
        assertEquals(1, f.peer.states.size());
        f.peer = new Peer(); f.connection.onServiceConnected(null, null);
        assertEquals(RenderState.PLAYING, f.peer.states.get(0));
    }
    @Test public void failedBindDoesNotUnbindOrAcceptUnsolicitedConnection() {
        Fixture f = new Fixture(); f.success = false; f.connection.bind();
        f.connection.onServiceConnected(null, null); f.connection.close();
        assertEquals(0, f.unbinds); assertEquals(0, f.resolves);
    }
    @Test public void failedBindMayBeRetriedBeforeClose() {
        Fixture f = new Fixture(); f.success = false; f.connection.bind();
        f.success = true; f.connection.bind(); f.connection.onServiceConnected(null, null);
        assertEquals(2, f.binds); assertSame(f.player, f.peer.players.get(0));
    }
    static class Peer implements CastRendererConnection.Peer {
        List<RenderControl> players = new ArrayList<>(); List<RenderState> states = new ArrayList<>();
        @Override public void player(RenderControl player) { players.add(player); }
        @Override public void state(RenderState state) { states.add(state); }
    }
    static class Fixture implements CastRendererConnection.Binding {
        final RenderControl player = (RenderControl) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{RenderControl.class}, (p, m, a) -> m.getName().equals("getState") ? RenderState.IDLE : null);
        final CastRendererConnection connection = new CastRendererConnection(player, this);
        Peer peer = new Peer(); int binds, unbinds, resolves; boolean success = true;
        @Override public boolean bind(ServiceConnection connection) { binds++; return success; }
        @Override public void unbind(ServiceConnection connection) { unbinds++; }
        @Override public CastRendererConnection.Peer peer(IBinder binder) { resolves++; return peer; }
    }
}
