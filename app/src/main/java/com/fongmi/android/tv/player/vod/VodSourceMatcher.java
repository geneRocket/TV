package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Flag;

/** Selects recovery routes without silently moving to an unrelated episode. */
public final class VodSourceMatcher {
    private VodSourceMatcher() {}

    public static boolean canResume(Flag candidate, String episode, boolean singleEpisode) {
        if (candidate == null || candidate.getEpisodes().isEmpty()) return false;
        if (singleEpisode && candidate.getEpisodes().size() == 1) return true;
        return episode == null || episode.isEmpty() || candidate.find(episode, true) != null;
    }
}
