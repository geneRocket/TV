package com.fongmi.android.tv.player.vod;

/** Only a successfully started route may replace persisted playback progress. */
public final class VodHistoryCommitGate {
    private VodEpisode committed;

    public void reset() { committed = null; }

    public void onState(VodPlaybackState state) {
        if (state.getPhase() == VodPlaybackPhase.READY) committed = state.getEpisode();
    }

    public boolean canCommit(VodPlaybackState state) {
        VodEpisode episode = state.getEpisode();
        if (committed == null || episode == null
                || !committed.getIdentity().equals(episode.getIdentity())
                || !committed.getResolverUrl().equals(episode.getResolverUrl())) return false;
        switch (state.getPhase()) {
            case READY:
            case PAUSED:
            case ENDED:
            case BUFFERING: return true;
            default: return false;
        }
    }
}
