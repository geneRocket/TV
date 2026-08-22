package com.fongmi.android.tv.player.vod;

public final class VodPlaybackState {

    public static final VodPlaybackState IDLE = new VodPlaybackState(VodPlaybackPhase.IDLE, null, -1, 0, -1, null);

    private final VodPlaybackPhase phase;
    private final VodEpisode episode;
    private final int episodeIndex;
    private final long position;
    private final long duration;
    private final Throwable error;

    public VodPlaybackState(VodPlaybackPhase phase, VodEpisode episode, int episodeIndex, long position, long duration, Throwable error) {
        if (phase == null) throw new IllegalArgumentException("phase must not be null");
        if (episodeIndex < -1) throw new IllegalArgumentException("episodeIndex must be at least -1");
        this.phase = phase;
        this.episode = episode;
        this.episodeIndex = episodeIndex;
        this.position = Math.max(position, 0);
        this.duration = duration;
        this.error = error;
    }

    public VodPlaybackPhase getPhase() { return phase; }

    public VodEpisode getEpisode() { return episode; }

    public int getEpisodeIndex() { return episodeIndex; }

    public long getPosition() { return position; }

    public long getDuration() { return duration; }

    public Throwable getError() { return error; }
}
