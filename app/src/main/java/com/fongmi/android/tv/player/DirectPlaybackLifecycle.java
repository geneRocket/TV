package com.fongmi.android.tv.player;

/** The same foreground/engine restoration transaction for live TV and the DLNA receiver. */
public final class DirectPlaybackLifecycle {
    public interface Port {
        void acquireOwnership();
        boolean needsEngine();
        void rebuildEngine();
        void restoreSource();
    }
    private final DirectPlaybackFlow flow;
    private final Port port;
    public DirectPlaybackLifecycle(DirectPlaybackFlow flow, Port port) { this.flow = flow; this.port = port; }
    public void resume() {
        if (flow.isClosed()) return;
        port.acquireOwnership();
        if (port.needsEngine()) {
            port.rebuildEngine();
            flow.restoreAfterHandoff(port::restoreSource);
        } else flow.setPlaybackAllowed(true);
    }
    public void pause() { flow.setPlaybackAllowed(false); }
}
