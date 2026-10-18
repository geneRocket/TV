package com.fongmi.android.tv.player.vod;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class VodEpisodeTest {

    @Test
    public void resumeIdentityNormalizesLabelsAndDistinguishesRoutes() {
        String first = VodEpisode.resumeIdentity("site", "line", "第01集");

        assertEquals(first, VodEpisode.resumeIdentity(" site ", " line ", " 第01集 "));
        assertNotEquals(first, VodEpisode.resumeIdentity("other", "line", "第01集"));
        assertNotEquals(first, VodEpisode.resumeIdentity("site", "other", "第01集"));
        assertNotEquals(first, VodEpisode.resumeIdentity("site", "line", "第02集"));
        assertNotEquals(VodEpisode.identity("site", "line", "第01集", "http://a/1.m3u8"),
                VodEpisode.identity("site", "line", "第01集", "http://b/1.m3u8"));
    }
}
