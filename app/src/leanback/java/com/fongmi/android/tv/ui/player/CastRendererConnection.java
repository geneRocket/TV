package com.fongmi.android.tv.ui.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.android.cast.dlna.dmr.DLNARendererService;
import com.android.cast.dlna.dmr.RenderControl;
import com.android.cast.dlna.dmr.RenderState;
import com.android.cast.dlna.dmr.RendererServiceBinder;
import com.fongmi.android.tv.service.StableDLNARendererService;

/** Owns renderer binding and state synchronization independently of the cast page's views. */
public final class CastRendererConnection implements ServiceConnection, AutoCloseable {
    public interface Peer {
        void player(RenderControl player);
        void state(RenderState state);
    }
    public interface Binding {
        boolean bind(ServiceConnection connection);
        void unbind(ServiceConnection connection);
        Peer peer(IBinder binder);
    }

    private final RenderControl player;
    private final Binding binding;
    private Peer peer;
    private RenderState state;
    private boolean bound;
    private boolean closed;

    public CastRendererConnection(Context context, RenderControl player) {
        this(player, new Binding() {
            private final Context app = context.getApplicationContext();
            @Override public boolean bind(ServiceConnection connection) {
                return app.bindService(new Intent(app, StableDLNARendererService.class), connection, Context.BIND_AUTO_CREATE);
            }
            @Override public void unbind(ServiceConnection connection) { app.unbindService(connection); }
            @Override public Peer peer(IBinder binder) {
                DLNARendererService service = ((RendererServiceBinder) binder).getService();
                return new Peer() {
                    @Override public void player(RenderControl player) { service.bindRealPlayer(player); }
                    @Override public void state(RenderState state) { service.notifyAvTransportLastChange(state); }
                };
            }
        });
    }

    public CastRendererConnection(RenderControl player, Binding binding) {
        this.player = player;
        this.binding = binding;
        this.state = player.getState();
    }

    public void bind() {
        if (!closed && !bound) bound = binding.bind(this);
    }

    public void state(RenderState state) {
        if (closed) return;
        this.state = state;
        if (peer != null) peer.state(state);
    }

    @Override public void onServiceConnected(ComponentName name, IBinder binder) {
        if (closed || !bound) return;
        peer = binding.peer(binder);
        peer.player(player);
        peer.state(state);
    }

    @Override public void onServiceDisconnected(ComponentName name) { peer = null; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (peer != null) peer.player(null);
        peer = null;
        if (bound) {
            bound = false;
            binding.unbind(this);
        }
    }
}
