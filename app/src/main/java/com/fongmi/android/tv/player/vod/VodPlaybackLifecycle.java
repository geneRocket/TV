package com.fongmi.android.tv.player.vod;

/** Platform-independent foreground/rebuild protocol shared by TV and mobile VOD pages. */
public final class VodPlaybackLifecycle {
    public interface Port {
        void acquireOwnership();
        boolean needsEngine();
        void rebuildAndAttachEngine();
        default void restoreInitialSelection() {}
    }
    private final VodPlaybackFlow flow;
    private final Port port;
    public VodPlaybackLifecycle(VodPlaybackFlow flow, Port port) {
        this.flow = flow;
        this.port = port;
    }
    public boolean resume() {
        if (flow.state().getPhase() == VodPlaybackPhase.RELEASED) return false;
        port.acquireOwnership();
        boolean rebuild = port.needsEngine();
        if (rebuild) port.rebuildAndAttachEngine();
        flow.setPlaybackAllowed(true);
        if (rebuild || flow.state().getPhase() == VodPlaybackPhase.SUSPENDED) {
            if (flow.state().getEpisode() == null) port.restoreInitialSelection();
            else flow.reload();
        }
        return rebuild;
    }
    public void pause() { flow.setPlaybackAllowed(false); }
}
