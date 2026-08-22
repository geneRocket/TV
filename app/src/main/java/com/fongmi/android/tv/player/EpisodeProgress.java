package com.fongmi.android.tv.player;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;

public final class EpisodeProgress {

    private EpisodeProgress() {
    }

    public static void apply(History history, Flag flag, Episode episode, EpisodePlaybackPlan plan, long now) {
        if (history == null || flag == null || episode == null || plan == null) return;
        history.setPosition(plan.getHistoryPosition());
        if (plan.isEpisodeChanged()) history.setDuration(0);
        history.setEpisodeUrl(episode.getUrl());
        history.setVodRemarks(episode.getName());
        history.setVodFlag(flag.getFlag());
        history.setCreateTime(now);
    }
}
