package com.fongmi.android.tv.player.vod;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class VodEpisodeTest {

    @Test
    public void resumeIdentityIgnoresResolverUrlChanges() {
        String first = VodEpisode.resumeIdentity("site", "line", "第01集");
        String second = VodEpisode.resumeIdentity("site", "line", "第01集");

        assertEquals(first, second);
        assertNotEquals(VodEpisode.identity("site", "line", "第01集", "http://a/1.m3u8"),
                VodEpisode.identity("site", "line", "第01集", "http://b/1.m3u8"));
    }
}
