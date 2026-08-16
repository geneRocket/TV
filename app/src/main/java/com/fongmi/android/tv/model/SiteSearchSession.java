package com.fongmi.android.tv.model;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.utils.ThreadPools;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public final class SiteSearchSession implements AutoCloseable {

    private static final AtomicLong TOKENS = new AtomicLong();
    private enum Mode { MANUAL, SELECT_FIRST, RECOVERING }

    private final SiteViewModel viewModel;
    private final String executorName;
    private final int concurrency;
    private ExecutorService executor;
    private String token;
    private int generation;
    private int pending;
    private boolean active;
    private Mode mode = Mode.MANUAL;

    public SiteSearchSession(SiteViewModel viewModel, String executorName, int concurrency) {
        this.viewModel = viewModel;
        this.executorName = executorName;
        this.concurrency = concurrency;
    }

    public synchronized void start(List<Site> sites, String keyword, boolean auto, Runnable onComplete) {
        cancel();
        this.token = "search:" + TOKENS.incrementAndGet();
        this.active = true;
        this.pending = sites.size();
        this.mode = auto ? Mode.SELECT_FIRST : Mode.MANUAL;
        String currentToken = this.token;
        int currentGeneration = ++generation;
        if (sites.isEmpty()) {
            App.post(() -> complete(currentGeneration, onComplete));
            return;
        }
        executor = ThreadPools.newFixedQueued(executorName, concurrency);
        for (Site site : sites) executor.execute(() -> search(site, keyword, currentToken, currentGeneration, onComplete));
    }

    public synchronized boolean accepts(Result result) {
        return active && result != null && TextUtils.equals(token, result.getRequestToken());
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized boolean hasPending() {
        return active && pending > 0;
    }

    public synchronized String getToken() {
        return token;
    }

    public synchronized boolean isAutomatic() {
        return mode != Mode.MANUAL;
    }

    public synchronized boolean shouldSelectFirst() {
        return mode == Mode.SELECT_FIRST;
    }

    public synchronized void markSelected() {
        if (mode == Mode.SELECT_FIRST) mode = Mode.RECOVERING;
    }

    public synchronized void setManual() {
        mode = Mode.MANUAL;
    }

    public synchronized void cancel() {
        active = false;
        pending = 0;
        generation++;
        if (!TextUtils.isEmpty(token)) viewModel.cancelSearch(token);
        token = null;
        if (executor != null) executor.shutdownNow();
        executor = null;
    }

    @Override
    public void close() {
        cancel();
    }

    private void search(Site site, String keyword, String token, int generation, Runnable onComplete) {
        try {
            if (!isCurrent(generation, token)) return;
            viewModel.searchContent(site, keyword, false, token);
        } catch (Throwable error) {
            ThreadPools.log(error, "Video search failed for " + site.getName());
        } finally {
            if (finish(generation)) App.post(() -> complete(generation, onComplete));
        }
    }

    private synchronized boolean isCurrent(int generation, String token) {
        return active
                && this.generation == generation
                && TextUtils.equals(this.token, token)
                && !Thread.currentThread().isInterrupted();
    }

    private synchronized boolean finish(int generation) {
        if (!active || this.generation != generation) return false;
        if (pending > 0) pending--;
        if (pending == 0 && executor != null) {
            executor.shutdown();
            executor = null;
        }
        return pending == 0;
    }

    private void complete(int generation, Runnable callback) {
        synchronized (this) {
            if (!active || this.generation != generation || pending > 0) return;
        }
        callback.run();
    }
}
