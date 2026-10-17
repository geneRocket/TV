package com.fongmi.android.tv.player;

import android.net.Uri;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.assertEquals;

public class SourceLifecycleTest {
    private static class Extractor implements Source.Extractor {
        int stops;
        public boolean match(Uri uri) { return true; }
        public String fetch(String url) { return url; }
        public void stop() { stops++; }
        public void exit() {}
    }

    @Test public void deferredStopDoesNotStopANewerPlaybackUsingTheSameExtractor() {
        Extractor extractor = new Extractor();
        Source source = new Source(Collections.singletonList(extractor));
        source.prepare(extractor);
        Runnable cleanup = source.deferStop();
        source.prepare(extractor);
        int stops = extractor.stops;
        cleanup.run();
        assertEquals(stops, extractor.stops);
    }

    @Test public void deferredStopCleansUpWhenNoNewPlaybackStarted() {
        Extractor extractor = new Extractor();
        Source source = new Source(Collections.singletonList(extractor));
        source.prepare(extractor);
        Runnable cleanup = source.deferStop();
        assertEquals(0, extractor.stops);
        cleanup.run();
        assertEquals(1, extractor.stops);
    }

    @Test public void directPlaybackAlsoSupersedesDeferredCleanup() {
        Extractor extractor = new Extractor();
        Source source = new Source(Collections.singletonList(extractor));
        source.prepare(extractor);
        Runnable cleanup = source.deferStop();
        source.prepare(null);
        assertEquals(1, extractor.stops);
        cleanup.run();
        assertEquals(1, extractor.stops);
    }

    @Test public void explicitStopSupersedesQueuedCleanup() {
        Extractor extractor = new Extractor();
        Source source = new Source(Collections.singletonList(extractor));
        source.prepare(extractor);
        Runnable cleanup = source.deferStop();
        source.stop();
        cleanup.run();
        assertEquals(1, extractor.stops);
    }
}
