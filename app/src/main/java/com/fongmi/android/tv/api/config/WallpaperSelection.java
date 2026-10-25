package com.fongmi.android.tv.api.config;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Serializes selection intent and scheduling; publication never takes this scheduling lock. */
final class WallpaperSelection {
    static final class Choice {
        final String url;
        final boolean followsVod;
        Choice(String url, boolean followsVod) { this.url = url; this.followsVod = followsVod; }
    }
    private final AtomicReference<Choice> pending = new AtomicReference<>();

    synchronized void choose(String url, boolean followsVod, Consumer<Choice> schedule) {
        enqueue(new Choice(url, followsVod), schedule);
    }

    synchronized boolean inherit(String activeUrl, boolean followsVod, String url, Consumer<Choice> schedule) {
        if (!needSync(activeUrl, followsVod, url)) return false;
        enqueue(new Choice(url, true), schedule);
        return true;
    }

    boolean needSync(String activeUrl, boolean followsVod, String url) {
        Choice choice = pending.get();
        String selected = choice == null ? activeUrl : choice.url;
        boolean inherited = choice == null ? followsVod : choice.followsVod;
        return inherited || selected == null || selected.isEmpty() || selected.equals(url);
    }

    void finish(Choice choice) { pending.compareAndSet(choice, null); }

    private void enqueue(Choice choice, Consumer<Choice> schedule) {
        pending.set(choice);
        try { schedule.accept(choice); }
        catch (RuntimeException error) { finish(choice); throw error; }
    }
}
