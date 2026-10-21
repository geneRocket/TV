package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Episode;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class VodEpisodeCatalogTest {
    private Episode episode(String name, String url) {
        return Episode.create(name, url);
    }

    @Test public void displayedOrderAndScopedResumeIdentityArePreserved() {
        List<VodEpisode> list = VodEpisodeCatalog.episodes("configA:site", "route", Arrays.asList(
                episode("2", "https://a/2.mp4"), episode("1", "https://a/1.mp4")));
        assertEquals("2", list.get(0).getName());
        assertEquals("https://a/2.mp4", list.get(0).getResolverUrl());
        assertEquals(VodEpisode.resumeIdentity("configA:site", "route", "1"), list.get(1).getIdentity());
        assertNotEquals(list.get(1).getIdentity(), VodEpisodeCatalog.episodes("configB:site", "route",
                Collections.singletonList(episode("1", "https://a/1.mp4"))).get(0).getIdentity());
    }

    @Test public void conversionReturnsIndependentListWithoutChangingActivation() {
        Episode item = episode("1", "https://a/1.mp4");
        item.setActivated(true);
        List<VodEpisode> list = VodEpisodeCatalog.episodes("site", "route", Collections.singletonList(item));
        list.clear();
        assertTrue(item.isActivated());
        assertEquals(1, VodEpisodeCatalog.episodes("site", "route", Collections.singletonList(item)).size());
        assertTrue(VodEpisodeCatalog.episodes("site", "route", Collections.emptyList()).isEmpty());
    }
}
