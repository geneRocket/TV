package com.fongmi.android.tv.ui.player;

import com.fongmi.android.tv.player.vod.VodPlaybackPhase;

/** Immutable state rendered by the TV playback screen. */
public final class TvVodViewState {

    public static final TvVodViewState IDLE = new TvVodViewState(VodPlaybackPhase.IDLE, -1, "", 0, -1, false, false, null);

    private final VodPlaybackPhase phase;
    private final int episodeIndex;
    private final String episodeName;
    private final long position;
    private final long duration;
    private final boolean progressVisible;
    private final boolean controlsEnabled;
    private final Throwable error;

    public TvVodViewState(VodPlaybackPhase phase, int episodeIndex, String episodeName, long position, long duration,
                          boolean progressVisible, boolean controlsEnabled, Throwable error) {
        this.phase = phase;
        this.episodeIndex = episodeIndex;
        this.episodeName = episodeName == null ? "" : episodeName;
        this.position = Math.max(position, 0);
        this.duration = duration;
        this.progressVisible = progressVisible;
        this.controlsEnabled = controlsEnabled;
        this.error = error;
    }

    public VodPlaybackPhase getPhase() { return phase; }
    public int getEpisodeIndex() { return episodeIndex; }
    public String getEpisodeName() { return episodeName; }
    public long getPosition() { return position; }
    public long getDuration() { return duration; }
    public boolean isProgressVisible() { return progressVisible; }
    public boolean isControlsEnabled() { return controlsEnabled; }
    public Throwable getError() { return error; }
}
