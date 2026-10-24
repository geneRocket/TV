package com.fongmi.android.tv.player;

import android.view.View;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.player.engine.ExoPlayerEngine;
import com.fongmi.android.tv.player.engine.IjkPlayerEngine;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.session.DefaultPlayerSession;
import com.fongmi.android.tv.player.session.PlaybackObserver;
import com.fongmi.android.tv.player.session.PlayerSession;
import com.fongmi.android.tv.player.session.Subscription;
import com.fongmi.android.tv.utils.ThreadPools;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

/** Owns native engine, delegate session and their attachment lifetime as one unit. */
public final class PlaybackEngineHost implements AutoCloseable {
    private PlayerEngine engine;
    private PlayerSession session;
    private Subscription subscription;
    private PlayerView exo;
    private IjkVideoView ijk;
    private int type, decode;
    private boolean closed;
    public PlaybackEngineHost() {}
    public PlaybackEngineHost(PlayerSession session) { this.session = session; }
    public void views(PlayerView exo, IjkVideoView ijk) {
        if (closed) throw new IllegalStateException("engine host is closed");
        this.exo = exo; this.ijk = ijk;
    }
    public boolean isClosed() { return closed; }
    public PlayerSession session() { return session; }
    public PlayerEngine engine() { return engine; }
    public IjkVideoView ijk() { return ijk; }
    public ExoPlayer exo() { return engine instanceof ExoPlayerEngine ? ((ExoPlayerEngine) engine).player() : null; }
    public int type() { return type; }
    public int decode() { return decode; }

    public void initialize(int type, int decode, PlaybackObserver observer) {
        if (closed) throw new IllegalStateException("engine host is closed");
        releaseEngine();
        this.type = type;
        this.decode = decode;
        if (exo == null || ijk == null) return;
        if (type == PlayerType.EXO) {
            exo.setVisibility(View.VISIBLE);
            ijk.setVisibility(View.GONE);
            try { engine = new ExoPlayerEngine(exo, decode, Setting.isPlayWithOthers(), Setting.isTunnel()); }
            catch (RuntimeException error) {
                ThreadPools.log(error, "Exo initialization failed; falling back to IJK.");
                this.type = PlayerType.IJK;
                this.decode = Setting.getDecode(this.type);
            }
        }
        if (engine == null) {
            exo.setVisibility(View.GONE);
            ijk.setVisibility(View.VISIBLE);
            engine = new IjkPlayerEngine(ijk, this.type, Setting.getRender(), this.decode);
        }
        session = new DefaultPlayerSession(engine);
        subscription = session.observe(observer);
    }

    public void releaseEngine() {
        PlayerSession previous = session;
        PlayerEngine previousEngine = engine;
        Subscription previousSubscription = subscription;
        session = null;
        engine = null;
        subscription = null;
        try { if (previousSubscription != null) previousSubscription.cancel(); }
        catch (RuntimeException error) { ThreadPools.log(error, "Player subscription release failed."); }
        try {
            if (previous != null) previous.release();
            else if (previousEngine != null) previousEngine.release();
        } catch (RuntimeException error) { ThreadPools.log(error, "Player release failed."); }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        releaseEngine();
        exo = null;
        ijk = null;
    }
}
