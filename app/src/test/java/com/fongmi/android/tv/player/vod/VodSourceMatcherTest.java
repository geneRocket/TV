package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import org.junit.Test;
import static org.junit.Assert.*;

public class VodSourceMatcherTest {
    @Test public void missingCurrentEpisodeIsRejected() {
        assertFalse(VodSourceMatcher.canResume(flag("第1集", "第2集"), "第12集", false));
    }

    @Test public void equivalentEpisodeLabelIsAccepted() {
        assertTrue(VodSourceMatcher.canResume(flag("11", "12"), "第12集", false));
    }

    @Test public void singleMovieCanUseDifferentLabel() {
        assertTrue(VodSourceMatcher.canResume(flag("高清"), "正片", true));
        assertFalse(VodSourceMatcher.canResume(flag("高清"), "第12集", false));
    }

    @Test public void emptyRoutesAreRejected() {
        assertFalse(VodSourceMatcher.canResume(null, "", true));
        assertFalse(VodSourceMatcher.canResume(flag(), "", true));
        assertTrue(VodSourceMatcher.canResume(flag("第1集"), "", false));
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
