package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VodTest {

    @Test
    public void setVodFlagsKeepsPlayableUrlsWhenFlagNameIsMissing() {
        Vod vod = new Vod();
        vod.setVodFlags(null);
        setPlay(vod, "", "第1集$http://a.com/1.m3u8#第2集$http://a.com/2.m3u8");

        vod.setVodFlags();

        assertEquals(1, vod.getVodFlags().size());
        assertEquals("play1", flag(vod.getVodFlags().get(0)));
        assertEquals(2, vod.getVodFlags().get(0).getEpisodes().size());
    }

    @Test
    public void setVodFlagsSkipsEmptyEpisodesAndEmptyLines() {
        Vod vod = new Vod();
        vod.setVodFlags(null);
        setPlay(vod, "线路1$$$线路2", "第1集$http://a.com/1.m3u8##空$ $$$");

        vod.setVodFlags();

        assertEquals(1, vod.getVodFlags().size());
        assertEquals("线路1", flag(vod.getVodFlags().get(0)));
        assertEquals(1, vod.getVodFlags().get(0).getEpisodes().size());
    }

    private void setPlay(Vod vod, String from, String url) {
        try {
            java.lang.reflect.Field playFrom = Vod.class.getDeclaredField("vodPlayFrom");
            java.lang.reflect.Field playUrl = Vod.class.getDeclaredField("vodPlayUrl");
            playFrom.setAccessible(true);
            playUrl.setAccessible(true);
            playFrom.set(vod, from);
            playUrl.set(vod, url);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private String flag(Flag item) {
        try {
            java.lang.reflect.Field flag = Flag.class.getDeclaredField("flag");
            flag.setAccessible(true);
            return (String) flag.get(item);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
