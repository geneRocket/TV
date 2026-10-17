package com.fongmi.android.tv.player;

import com.fongmi.android.tv.player.extractor.Push;
import com.fongmi.android.tv.player.extractor.Video;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.assertEquals;

public class SourceExtractionTest {
    @Test public void videoWrapperForcesParsingOfTheUnwrappedPage() throws Exception {
        AtomicInteger parse = new AtomicInteger(0);
        String page = "https://example.com/watch?id=123";
        assertEquals(page, Source.extract(new Video(), "video://" + page, parse::set));
        assertEquals(1, parse.get());
        parse.set(0);
        assertEquals(page, Source.extract(new Video(), "video:" + page, parse::set));
        assertEquals(1, parse.get());
    }

    @Test public void pushWrapperKeepsDirectPlayback() throws Exception {
        AtomicInteger parse = new AtomicInteger(1);
        String media = "https://example.com/video.m3u8";
        assertEquals(media, Source.extract(new Push(), "push://" + media, parse::set));
        assertEquals(0, parse.get());
    }

    @Test public void unhandledUrlsKeepTheSpidersParseChoice() throws Exception {
        for (int selected : new int[]{0, 1}) {
            AtomicInteger parse = new AtomicInteger(selected);
            String url = "https://example.com/episode";
            assertEquals(url, Source.extract(null, url, parse::set));
            assertEquals(selected, parse.get());
        }
    }
}
