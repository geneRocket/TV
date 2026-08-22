package com.fongmi.android.tv.player;

import java.util.Objects;

public final class EpisodePlaybackPlan {

    private final long historyPosition;
    private final long startPosition;
    private final boolean episodeChanged;
    private final boolean skipOpening;

    private EpisodePlaybackPlan(long historyPosition, long startPosition, boolean episodeChanged, boolean skipOpening) {
        this.historyPosition = historyPosition;
        this.startPosition = startPosition;
        this.episodeChanged = episodeChanged;
        this.skipOpening = skipOpening;
    }

    public static EpisodePlaybackPlan resolve(String historyUrl, String historyName, String targetUrl, String targetName, long savedPosition, long opening, boolean replay, boolean preservePosition, long preservedPosition) {
        boolean sameEpisode = preservePosition || sameEpisode(historyUrl, historyName, targetUrl, targetName);
        boolean startFresh = replay || !sameEpisode;
        long position = preservePosition ? Math.max(preservedPosition, 0) : startFresh ? 0 : Math.max(savedPosition, 0);
        return new EpisodePlaybackPlan(position, Math.max(Math.max(opening, 0), position), !sameEpisode, startFresh);
    }

    static boolean sameEpisode(String historyUrl, String historyName, String targetUrl, String targetName) {
        if (historyUrl != null && !historyUrl.isEmpty()) return Objects.equals(historyUrl, targetUrl);
        return historyName != null && targetName != null && historyName.equalsIgnoreCase(targetName);
    }

    public long getHistoryPosition() { return historyPosition; }

    public long getStartPosition() { return startPosition; }

    public boolean isEpisodeChanged() { return episodeChanged; }

    public boolean shouldSkipOpening() { return skipOpening; }
}
