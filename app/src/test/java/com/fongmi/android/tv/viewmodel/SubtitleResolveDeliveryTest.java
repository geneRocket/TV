package com.fongmi.android.tv.viewmodel;

import com.fongmi.android.tv.bean.Subtitle;
import com.fongmi.android.tv.utils.TaskScheduler;
import okhttp3.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class SubtitleResolveDeliveryTest {
    private final Queue<Runnable> main = new ArrayDeque<>();
    private final List<String> delivered = new ArrayList<>();
    private SubtitleViewModel model;
    private AtomicInteger sequence;

    @Before public void setUp() throws Exception {
        model = new SubtitleViewModel(new OkHttpClient(), new TaskScheduler() {
            public void post(Runnable task, long delay) { main.add(task); }
            public void remove(Runnable task) { main.remove(task); }
        });
        Field field = SubtitleViewModel.class.getDeclaredField("resolveSeq"); field.setAccessible(true);
        sequence = (AtomicInteger) field.get(model);
    }

    @After public void tearDown() { model.onCleared(); main.clear(); }

    @Test public void cancelledResolveCannotApplyAnAlreadyQueuedSubtitle() {
        Subtitle item = new Subtitle("old", "https://example.com/original", false);
        int old = sequence.incrementAndGet();
        model.postResolvedSubtitle(old, item, "https://example.com/stale", value -> delivered.add(value.getUrl()));
        model.cancelSubtitleResolve(); drain();
        assertTrue(delivered.isEmpty());
        assertEquals("https://example.com/original", item.getUrl());
    }

    @Test public void oldResponseCannotReplaceTheLatestSelection() {
        int old = sequence.incrementAndGet(), current = sequence.incrementAndGet();
        Subtitle item = new Subtitle("new", "https://example.com/new", false);
        model.postResolvedSubtitle(current, item, "https://example.com/current", value -> delivered.add(value.getUrl()));
        model.postResolvedSubtitle(old, item, "https://example.com/stale", value -> delivered.add(value.getUrl()));
        drain();
        assertEquals(java.util.Collections.singletonList("https://example.com/current"), delivered);
        assertEquals("https://example.com/current", item.getUrl());
    }

    @Test public void modelTeardownDiscardsQueuedFallbackDelivery() {
        int current = sequence.incrementAndGet();
        model.postResolvedSubtitle(current, new Subtitle("old", "https://example.com/original", false), null,
                value -> delivered.add(value.getUrl()));
        model.onCleared(); drain();
        assertTrue(delivered.isEmpty());
    }

    @Test public void currentResolveDeliversOnMainAndKeepsOriginalUrlForMissingLocation() {
        int current = sequence.incrementAndGet();
        model.postResolvedSubtitle(current, new Subtitle("new", "https://example.com/original", false), null,
                value -> delivered.add(value.getUrl()));
        assertTrue(delivered.isEmpty()); drain();
        assertEquals(java.util.Collections.singletonList("https://example.com/original"), delivered);
    }

    @Test public void cancellationAlsoCancelsTheOwnedHttpCall() throws Exception {
        Call call = new OkHttpClient().newCall(new Request.Builder().url("http://localhost/subtitle").build());
        Field field = SubtitleViewModel.class.getDeclaredField("currentResolveCall"); field.setAccessible(true);
        field.set(model, call);
        model.cancelSubtitleResolve();
        assertTrue(call.isCanceled()); assertNull(field.get(model));
    }

    private void drain() { while (!main.isEmpty()) main.remove().run(); }
}
