package com.fongmi.android.tv.ui.player;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class TvVodSourceSwitchStateTest {
    @Test public void missingEpisodeDoesNotFallBackToFirstEpisode() {
        TvVodSourceSwitchState state = new TvVodSourceSwitchState();
        state.search("第12集", flag("第11集", "第12集"));
        state.startLoading();
        assertNull(state.findTargetFlag(Collections.singletonList(flag("第1集", "第2集"))));
    }

    @Test public void selectsFlagContainingCurrentEpisode() {
        TvVodSourceSwitchState state = new TvVodSourceSwitchState();
        state.search("第12集", flag("第11集", "第12集"));
        state.startLoading();
        Flag matching = flag("第11集", "第12集");
        assertSame(matching, state.findTargetFlag(Arrays.asList(flag("第1集", "第2集"), matching)));
    }

    @Test public void movieCanUseDifferentEpisodeLabel() {
        TvVodSourceSwitchState state = new TvVodSourceSwitchState();
        state.search("正片", flag("正片"));
        state.startLoading();
        Flag matching = flag("高清");
        assertSame(matching, state.findTargetFlag(Collections.singletonList(matching)));
    }

    @Test public void ordinaryOpenStillUsesFirstPlayableFlag() {
        TvVodSourceSwitchState state = new TvVodSourceSwitchState();
        Flag first = flag("第1集", "第2集");
        assertSame(first, state.findTargetFlag(Arrays.asList(null, flag(), first)));
    }

    private static Flag flag(String... names) {
        Flag flag = new Flag();
        for (String name : names) {
            Episode episode = new Episode();
            episode.setName(name);
            flag.getEpisodes().add(episode);
        }
        return flag;
    }
}
