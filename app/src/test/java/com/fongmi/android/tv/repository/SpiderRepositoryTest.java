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
