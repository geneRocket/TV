package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import org.junit.Test;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class SpiderRepositoryTest {
    @Test public void posterHydrationKeepsListingOrderAndMissingEntries() {
        Vod first = vod("a", "");
        Vod missing = vod("b", "");
        Vod existing = vod("c", "original.jpg");
        Result result = Result.empty();
        SpiderRepository.applyPictures(result, Arrays.asList(first, missing, existing),
                Arrays.asList(vod("c", "alternate.jpg"), vod("a", "a.jpg"), vod("unknown", "extra.jpg")));

        assertEquals(3, result.getList().size());
        assertSame(first, result.getList().get(0));
        assertSame(missing, result.getList().get(1));
        assertSame(existing, result.getList().get(2));
        assertEquals("a.jpg", first.getVodPic());
        assertEquals("", missing.getVodPic());
        assertEquals("original.jpg", existing.getVodPic());
    }

    private static Vod vod(String id, String picture) {
        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodPic(picture);
        return vod;
    }

    @Test public void legacySpiderStillSearchesFirstPage() throws Exception {
        com.github.catvod.crawler.Spider spider = new com.github.catvod.crawler.Spider() {
            @Override public String searchContent(String key, boolean quick) {
                return key + ":" + quick;
            }
        };
        assertEquals("news:true", spider.searchContent("news", true, "1"));
        assertEquals("", spider.searchContent("news", true, "2"));
    }

    @Test public void detailsExpandPlaylistsAfterInitializingFlags() throws Exception {
        Flag flag = new Flag();
        AtomicBoolean initialized = new AtomicBoolean();
        Vod vod = new Vod() {
            @Override public void setVodFlags() {
                setVodFlags(Arrays.asList(flag));
                initialized.set(true);
            }
        };
        Episode expanded = new Episode();
        expanded.setName("Expanded episode");
        Result result = Result.vod(vod);

        assertSame(result, SpiderRepository.prepareDetailResult(result, flags -> {
            assertTrue(initialized.get());
            assertSame(flag, flags.get(0));
            flags.get(0).getEpisodes().add(expanded);
        }));

        assertEquals(1, vod.getVodFlags().get(0).getEpisodes().size());
        assertSame(expanded, vod.getVodFlags().get(0).getEpisodes().get(0));
    }

    @Test public void emptyDetailsDoNotStartPlaylistWork() throws Exception {
        Result empty = Result.empty();
        assertSame(empty, SpiderRepository.prepareDetailResult(empty, flags -> fail("Empty details started parser")));
    }

    @Test public void cancelledExpansionCannotReturnPartialDetails() throws Exception {
        Vod vod = new Vod() { @Override public void setVodFlags() { } };
        try {
            SpiderRepository.prepareDetailResult(Result.vod(vod), flags -> { throw new InterruptedException("cancelled"); });
            fail("Cancellation was ignored");
        } catch (InterruptedException expected) {
            assertEquals("cancelled", expected.getMessage());
        }
    }
}
