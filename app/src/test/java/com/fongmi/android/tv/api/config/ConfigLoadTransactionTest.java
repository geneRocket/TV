package com.fongmi.android.tv.api.config;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigLoadTransactionTest {
    @Test public void compositeBuildPublishesOnceAfterEverySourceIsMerged() throws Throwable {
        List<String> events = new ArrayList<>();
        List<String> loaded = ConfigLoadTransaction.execute(() -> {
            List<String> sources = new ArrayList<>();
            for (String source : Arrays.asList("text", "json", "cached")) {
                sources.add(source); events.add("build:" + source);
            }
            return Collections.unmodifiableList(sources);
        }, candidate -> {
            assertEquals(Arrays.asList("text", "json", "cached"), candidate);
            events.add("publish");
        });
        assertEquals(3, loaded.size());
        assertEquals(Arrays.asList("build:text", "build:json", "build:cached", "publish"), events);
    }
    @Test public void invalidLastSourceCannotPublishAnEarlierPartialResult() throws Throwable {
        AtomicInteger published = new AtomicInteger();
        try {
            ConfigLoadTransaction.execute(() -> {
                List<String> partiallyBuilt = new ArrayList<>(); partiallyBuilt.add("valid");
                throw new IllegalStateException("invalid merged payload");
            }, candidate -> published.incrementAndGet());
            fail();
        } catch (IllegalStateException expected) { assertEquals("invalid merged payload", expected.getMessage()); }
        assertEquals(0, published.get());
    }
    @Test public void cancellationDuringParsingPreventsTheCommit() {
        ConfigLoadTask.Session session = new ConfigLoadTask.Session();
        AtomicInteger published = new AtomicInteger();
        session.run(() -> {
            try {
                ConfigLoadTransaction.execute(() -> { session.cancel(); return "late"; }, candidate -> published.incrementAndGet());
                fail();
            } catch (java.util.concurrent.CancellationException expected) { }
            catch (Throwable error) { throw new AssertionError(error); }
        });
        assertEquals(0, published.get());
    }
    @Test public void failedCandidateDoesNotPoisonTheNextIndependentLoad() throws Throwable {
        List<String> published = new ArrayList<>();
        try {
            ConfigLoadTransaction.<String>execute(() -> { throw new IllegalStateException("bad"); }, published::add);
            fail();
        } catch (IllegalStateException expected) { }
        ConfigLoadTransaction.execute(() -> "cached-working", published::add);
        assertEquals(Collections.singletonList("cached-working"), published);
    }
}
