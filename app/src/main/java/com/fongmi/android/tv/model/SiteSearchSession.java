package com.fongmi.android.tv.model;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.utils.AppTaskScheduler;
import com.fongmi.android.tv.utils.TaskScheduler;
import com.fongmi.android.tv.utils.ThreadPools;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.concurrent.ExecutorService;

public final class SiteSearchSession implements AutoCloseable {

    private enum Mode { MANUAL, SELECT_FIRST, RECOVERING }

    private static final long SEARCH_TIMEOUT_MS = 30_000;

    interface Backend {
        void search(Site site, String keyword, String token) throws Throwable;
        void cancel(String token);
        void finish(String token);
    }

    private final Backend backend;
    private final Supplier<ExecutorService> executorFactory;
    private final TaskScheduler scheduler;
    private ExecutorService executor;
    private String token;
    private Runnable completion;
    private Runnable deadline;
    private boolean settled;
    private boolean timedOut;
    private int generation;
    private int pending;
    private boolean active;
    private Mode mode = Mode.MANUAL;

    public SiteSearchSession(SiteViewModel viewModel, String executorName, int concurrency) {
        this(new Backend() {
            @Override public void search(Site site, String keyword, String token) throws Throwable { viewModel.searchContent(site, keyword, false, token); }
            @Override public void cancel(String token) { viewModel.cancelSearch(token); }
            @Override public void finish(String token) { viewModel.finishSearch(token); }
        }, () -> ThreadPools.newFixedQueued(executorName, Math.max(1, concurrency)), AppTaskScheduler.get());
    }

    SiteSearchSession(Backend backend, Supplier<ExecutorService> executorFactory, TaskScheduler scheduler) {
        this.backend = backend;
        this.executorFactory = executorFactory;
        this.scheduler = scheduler;
    }

    public synchronized void start(List<Site> sites, String keyword, boolean auto, Runnable onComplete) {
        cancel();
        String query = keyword == null ? "" : keyword.trim();
        Map<String, Site> unique = new LinkedHashMap<>();
        if (!query.isEmpty() && sites != null) {
            for (Site site : sites) if (site != null && !site.getKey().isEmpty()) unique.putIfAbsent(site.getKey(), site);
        }
        List<Site> requests = new ArrayList<>(unique.values());
        settled = false;
        timedOut = false;
        this.token = SearchRequestToken.next("search");
        this.active = true;
        this.pending = requests.size();
        this.mode = auto ? Mode.SELECT_FIRST : Mode.MANUAL;
        String currentToken = this.token;
        int currentGeneration = ++generation;
        if (requests.isEmpty()) {
            scheduleComplete(currentGeneration, onComplete);
            return;
        }
        deadline = () -> expire(currentGeneration, onComplete);
        scheduler.post(deadline, SEARCH_TIMEOUT_MS);
        executor = executorFactory.get();
        for (Site site : requests) executor.execute(() -> search(site, query, currentToken, currentGeneration, onComplete));
    }

    public synchronized boolean accepts(Result result) {
        return active && !timedOut && result != null && Objects.equals(token, result.getRequestToken());
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
        if (completion != null) scheduler.remove(completion);
        completion = null;
        clearDeadline();
        if (token != null) backend.cancel(token);
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
            backend.search(site, keyword, token);
        } catch (Throwable error) {
            ThreadPools.log(error, "Video search failed for " + site.getName());
        } finally {
            if (finish(generation)) scheduleComplete(generation, onComplete);
        }
    }

    private void scheduleComplete(int generation, Runnable callback) {
        Runnable delivery = () -> complete(generation, callback);
        synchronized (this) {
            if (!active || settled || this.generation != generation || pending > 0) return;
            completion = delivery;
            scheduler.post(delivery, 0);
        }
    }

    private synchronized boolean isCurrent(int generation, String token) {
        return active
                && this.generation == generation
                && !timedOut
                && Objects.equals(this.token, token)
                && !Thread.currentThread().isInterrupted();
    }

    private synchronized boolean finish(int generation) {
        if (!active || timedOut || this.generation != generation || pending <= 0) return false;
        if (pending > 0) pending--;
        if (pending == 0 && executor != null) {
            executor.shutdown();
            executor = null;
        }
        return pending == 0;
    }

    private synchronized void expire(int generation, Runnable callback) {
        if (!active || settled || this.generation != generation || pending <= 0) return;
        timedOut = true;
        pending = 0;
        backend.cancel(token);
        if (executor != null) executor.shutdownNow();
        executor = null;
        complete(generation, callback);
    }

    private void clearDeadline() {
        if (deadline != null) scheduler.remove(deadline);
        deadline = null;
    }

    private synchronized void complete(int generation, Runnable callback) {
        if (!active || settled || this.generation != generation || pending > 0) return;
        settled = true;
        clearDeadline();
        if (completion != null) scheduler.remove(completion);
        completion = null;
        backend.finish(token);
        if (callback != null) callback.run();
    }
}
