package com.fongmi.android.tv.player;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.resolver.PlaybackSourceResolver;
import com.fongmi.android.tv.player.resolver.ResolvedMedia;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns legacy direct-play resolution lifetime; cancelled or revoked owners cannot prepare media. */
public final class PlaybackSourcePipeline implements AutoCloseable {
    public interface Port {
        boolean isCurrent();
        void resolved(ResolvedMedia media);
        void failed();
    }
    public interface Backend {
        void resolve(Result result, boolean parse, boolean live, PlaybackSourceResolver.Callback callback);
        void cancel();
    }
    private final Backend backend;
    private final Port port;
    private volatile long generation;
    private volatile boolean closed;
    public PlaybackSourcePipeline(Port port) {
        this(port, new Backend() {
            private final PlaybackSourceResolver resolver = new PlaybackSourceResolver();
            @Override public void resolve(Result result, boolean parse, boolean live, PlaybackSourceResolver.Callback callback) {
                resolver.resolve(result, parse, live, callback);
            }
            @Override public void cancel() { resolver.cancel(); }
        });
    }
    public PlaybackSourcePipeline(Port port, Backend backend) { this.port = port; this.backend = backend; }
    public void resolve(Result result, boolean parse, boolean live) {
        cancel();
        if (closed || !port.isCurrent()) return;
        long expected = generation;
        PlaybackSourceResolver.Callback callback = new PlaybackSourceResolver.Callback() {
            private final AtomicBoolean delivered = new AtomicBoolean();
            private boolean claim() {
                return !closed && expected == generation && port.isCurrent() && delivered.compareAndSet(false, true);
            }
            @Override public void onResolved(ResolvedMedia media) { if (claim()) port.resolved(media); }
            @Override public void onFailure() { if (claim()) port.failed(); }
        };
        try { backend.resolve(result, parse, live, callback); }
        catch (RuntimeException error) { callback.onFailure(); }
    }
    public void cancel() { generation++; backend.cancel(); }
    @Override public void close() { if (!closed) { closed = true; cancel(); } }
}
