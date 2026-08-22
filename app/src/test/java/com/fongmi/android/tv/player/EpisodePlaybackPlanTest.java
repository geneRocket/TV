package com.fongmi.android.tv.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpisodePlaybackPlanTest {

    @Test
    public void newEpisodeStartsAtOpeningAndClearsHistoryProgress() {
        EpisodePlaybackPlan plan = EpisodePlaybackPlan.resolve("episode-1", "第1集", "episode-2", "第2集", 900_000, 60_000, false, false, 0);
        assertEquals(0, plan.getHistoryPosition());
        assertEquals(60_000, plan.getStartPosition());
        assertTrue(plan.isEpisodeChanged());
        assertTrue(plan.shouldSkipOpening());
    }

    @Test
    public void sameEpisodeResumesSavedPosition() {
        EpisodePlaybackPlan plan = EpisodePlaybackPlan.resolve("episode-1", "第1集", "episode-1", "第1集", 300_000, 60_000, false, false, 0);
        assertEquals(300_000, plan.getHistoryPosition());
        assertEquals(300_000, plan.getStartPosition());
        assertFalse(plan.isEpisodeChanged());
        assertFalse(plan.shouldSkipOpening());
    }

    @Test
    public void sourceSwitchPreservesCurrentPosition() {
        EpisodePlaybackPlan plan = EpisodePlaybackPlan.resolve("old-url", "第1集", "new-url", "第1集", 300_000, 60_000, false, true, 450_000);
        assertEquals(450_000, plan.getHistoryPosition());
        assertEquals(450_000, plan.getStartPosition());
        assertFalse(plan.isEpisodeChanged());
    }

    @Test
    public void duplicateNamesWithDifferentUrlsAreDifferentEpisodes() {
        assertFalse(EpisodePlaybackPlan.sameEpisode("episode-1", "正片", "episode-2", "正片"));
    }
}
