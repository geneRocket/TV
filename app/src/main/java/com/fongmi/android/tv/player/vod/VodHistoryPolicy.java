package com.fongmi.android.tv.player.vod;

/** The sole policy deciding when playback may replace a persisted resume point. */
public final class VodHistoryPolicy {
    private VodHistoryPolicy() {}
    public static boolean acceptsPhase(VodPlaybackPhase phase) {
        return phase == VodPlaybackPhase.READY || phase == VodPlaybackPhase.PAUSED
                || phase == VodPlaybackPhase.BUFFERING || phase == VodPlaybackPhase.ENDED
                || phase == VodPlaybackPhase.SUSPENDED;
    }
    public static boolean canCommit(VodEpisode proven, VodPlaybackState current) {
        VodEpisode episode = current.getEpisode();
        return proven != null && episode != null && acceptsPhase(current.getPhase())
                && proven.getIdentity().equals(episode.getIdentity())
                && proven.getResolverUrl().equals(episode.getResolverUrl());
    }
}
