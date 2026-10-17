package com.fongmi.android.tv.player.vod;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.session.PlaybackItem;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

public class DefaultPlaybackItemFactoryTest {

    @Test
    public void selectedSubtitleReplacesMatchingRemoteEntryAndTakesPriority() throws Exception {
        Sub first = sub("https://example.com/first.srt");
        Sub remote = sub("https://example.com/selected.srt");
        Sub selected = sub("https://example.com/selected.srt");
        PlaybackItem item = factory(selected).create(
                new VodEpisode("episode", "site", "flag", "name", "url"),
                new TestResult(Arrays.asList(first, remote)), 0);

        assertEquals(2, item.getSubtitles().size());
        assertSame(selected, item.getSubtitles().get(0));
        assertSame(first, item.getSubtitles().get(1));
    }

    @Test
    public void subtitlesDropNullEntriesAndKeepSelectedFirst() throws Exception {
        Sub remote = sub("https://example.com/remote.srt");
        Sub selected = sub("https://example.com/selected.srt");
        Result result = new TestResult(Arrays.asList(null, remote));
        DefaultPlaybackItemFactory factory = factory(selected);

        PlaybackItem item = factory.create(new VodEpisode("episode", "site", "flag", "name", "url"), result, 0);

        assertEquals(2, item.getSubtitles().size());
        assertEquals(selected.getUrl(), item.getSubtitles().get(0).getUrl());
        assertEquals(remote.getUrl(), item.getSubtitles().get(1).getUrl());
        assertFalse(item.getSubtitles().contains(null));
    }

    @Test
    public void existingSelectedSubtitleIsNotDuplicated() throws Exception {
        Sub selected = sub("https://example.com/selected.srt");
        Result result = new TestResult(Arrays.asList(selected));
        DefaultPlaybackItemFactory factory = factory(selected);

        PlaybackItem item = factory.create(new VodEpisode("episode", "site", "flag", "name", "url"), result, 0);

        assertEquals(1, item.getSubtitles().size());
    }

    private static Sub sub(String url) throws Exception {
        Sub sub = new Sub();
        Field field = Sub.class.getDeclaredField("url");
        field.setAccessible(true);
        field.set(sub, url);
        return sub;
    }

    private static DefaultPlaybackItemFactory factory(Sub selected) {
        return new DefaultPlaybackItemFactory(() -> selected, url -> url, url -> Collections.emptyMap(), headers -> headers);
    }

    private static final class TestResult extends Result {
        private final List<Sub> subtitles;

        private TestResult(List<Sub> subtitles) {
            this.subtitles = subtitles;
        }

        @Override public String getRealUrl() { return "https://example.com/video.m3u8"; }
        @Override public Map<String, String> getHeaders() { return Collections.emptyMap(); }
        @Override public List<Sub> getSubs() { return subtitles; }
    }

    @Test(expected = IllegalArgumentException.class)
    public void blocksResolvedAdvertisementAfterNormalizingItsUrl() {
        DefaultPlaybackItemFactory factory = new DefaultPlaybackItemFactory(() -> null,
                url -> "https://example.com/ad.mp4", url -> Collections.emptyMap(), headers -> headers,
                url -> url.endsWith("/ad.mp4"));
        factory.create(new VodEpisode("episode", "site", "flag", "name", "url"), new TestResult(Collections.emptyList()), 0);
    }

}
