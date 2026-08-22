package com.fongmi.android.tv.ui.player;

/** Small explicit state machine for automatic source replacement. */
public final class TvVodSourceSwitchState {

    public enum Phase { IDLE, SEARCHING, LOADING }

    private Phase phase = Phase.IDLE;
    private String episode = "";
    private long position;

    public Phase getPhase() { return phase; }
    public String getEpisode() { return episode; }
    public long getPosition() { return position; }
    public boolean isActive() { return phase != Phase.IDLE; }
    public boolean isLoading() { return phase == Phase.LOADING; }

    public void search(String episode, long position) {
        phase = Phase.SEARCHING;
        this.episode = episode == null ? "" : episode;
        this.position = Math.max(position, 0);
    }

    public void resumeSearch() {
        if (isActive()) phase = Phase.SEARCHING;
    }

    public void startLoading() {
        if (isActive()) phase = Phase.LOADING;
    }

    public void clear() {
        phase = Phase.IDLE;
        episode = "";
        position = 0;
    }
}
