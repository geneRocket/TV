package com.fongmi.android.tv.player.vod;

/** Only a successfully started route may replace persisted playback progress. */
public final class VodHistoryCommitGate {
    private VodEpisode committed;

    public void reset() { committed = null; }

    public void onState(VodPlaybackState state) {
        if (state.getPhase() == VodPlaybackPhase.READY) committed = state.getEpisode();
    }

    public boolean canCommit(VodPlaybackState state) {
        return VodHistoryPolicy.canCommit(committed, state);
    }
}
