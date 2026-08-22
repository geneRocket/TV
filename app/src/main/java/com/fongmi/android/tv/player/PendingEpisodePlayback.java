package com.fongmi.android.tv.player;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;

public final class PendingEpisodePlayback {

    private final Flag flag;
    private final Episode episode;
    private final EpisodePlaybackPlan plan;

    public PendingEpisodePlayback(Flag flag, Episode episode, EpisodePlaybackPlan plan) {
        this.flag = flag;
        this.episode = episode;
        this.plan = plan;
    }

    public long getStartPosition() {
        return plan.getStartPosition();
    }

    public long getHistoryPosition() {
        return plan.getHistoryPosition();
    }

    public boolean shouldSkipOpening() {
        return plan.shouldSkipOpening();
    }

    public void commit(History history, long now) {
        EpisodeProgress.apply(history, flag, episode, plan, now);
    }
}
