package com.fongmi.android.tv.ui.player;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Flag;

import java.util.List;

/** Small explicit state machine for automatic source replacement. */
public final class TvVodSourceSwitchState {

    public enum Phase { IDLE, SEARCHING, LOADING }

    private Phase phase = Phase.IDLE;
    private String episode = "";
    private boolean singleEpisode;

    public String getEpisode() { return episode; }
    public boolean isActive() { return phase != Phase.IDLE; }
    public boolean isLoading() { return phase == Phase.LOADING; }

    public Flag findTargetFlag(List<Flag> flags) {
        if (flags == null) return null;
        for (Flag flag : flags) if (matchesTargetFlag(flag)) return flag;
        return firstPlayableFlag(flags);
    }

    public void search(String episode, Flag currentFlag) {
        phase = Phase.SEARCHING;
        this.episode = episode == null ? "" : episode;
        this.singleEpisode = isSingleEpisode(currentFlag);
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
        singleEpisode = false;
    }

    private boolean matchesTargetFlag(Flag flag) {
        if (flag == null || !isLoading()) return false;
        int count = flag.getEpisodes().size();
        if (!acceptsTargetEpisodeCount(count)) return false;
        if (count == 1) return true;
        return flag.find(episode, !TextUtils.isEmpty(episode)) != null;
    }

    private boolean acceptsTargetEpisodeCount(int count) {
        return singleEpisode ? count == 1 : count > 1;
    }

    private boolean isSingleEpisode(Flag flag) {
        return flag != null && flag.getEpisodes().size() == 1;
    }

    private Flag firstPlayableFlag(List<Flag> flags) {
        for (Flag flag : flags) if (flag != null && !flag.getEpisodes().isEmpty()) return flag;
        return null;
    }
}
