package com.fongmi.android.tv.player.vod;

import android.text.TextUtils;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.SearchSorter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Owns cross-site source discovery and candidate selection. */
public final class VodSourceSearch implements AutoCloseable {

    private static final double MIN_SCORE = 0.5;

    public interface Port {
        boolean accepts(Result result);
        String expectedKeyword();
        String sourceSwitchKeyword();
        boolean automatic();
        String sourceKey(Vod vod);
        void mergeCandidates(List<Vod> items, String requestToken);
        void cancelEmptyFallback();
        void removeCandidate(Vod vod);
        void beginSourceSwitch();
        void markCurrentSourceBroken();
        void setManual();
        void startSourceLoading();
        void openDetail(Vod vod);
        int candidateCount();
        void selectNextCandidate();
        void cancelSearch();
        void clearCandidates();
        void setSearchKeyword(String keyword);
        void startSearch(List<Site> sites, String keyword, boolean automatic, Runnable settled);
        boolean loading();
        boolean hasPending();
        boolean shouldSelectFirst();
        boolean switching();
        boolean earlyThresholdReached();
        void flushCandidates();
        void clearSourceSwitch();
        void empty(boolean automatic);
    }

    private final Set<String> candidateKeys = new HashSet<>();
    private final Port port;
    private final Executor callbackDelivery;
    private final Supplier<List<Site>> sites;
    private boolean closed;
    private long generation;

    public VodSourceSearch(Port port) {
        this(port, Runnable::run, () -> VodConfig.get().getSites());
    }

    public VodSourceSearch(Port port, Executor delivery) {
        this(port, delivery, () -> VodConfig.get().getSites());
    }

    public VodSourceSearch(Port port, Executor delivery, Supplier<List<Site>> sites) {
        this.port = port;
        callbackDelivery = delivery;
        this.sites = sites;
    }

    public void accept(Result result) {
        if (closed) return;
        if (!port.accepts(result) || !TextUtils.equals(result.getKeyword(), port.expectedKeyword())) return;
        List<Vod> items = result.getList();
        if (items.isEmpty()) return;
        List<Vod> accepted = new ArrayList<>();
        String keyword = port.sourceSwitchKeyword();
        for (Vod item : items) {
            if (item == null) continue;
            if (!SearchSorter.isSourceCandidate(item, keyword, port.automatic(), MIN_SCORE)) continue;
            String sourceKey = port.sourceKey(item);
            if (!TextUtils.isEmpty(sourceKey) && candidateKeys.add(sourceKey)) accepted.add(item);
        }
        if (accepted.isEmpty()) return;
        port.mergeCandidates(accepted, result.getRequestToken());
        port.cancelEmptyFallback();
        selectReady(false);
    }

    public void select(Vod item) {
        if (closed || item == null) return;
        port.removeCandidate(item);
        port.beginSourceSwitch();
        port.markCurrentSourceBroken();
        port.setManual();
        port.startSourceLoading();
        port.openDetail(item);
    }

    public void recover() {
        if (closed) return;
        if (port.candidateCount() == 0) start(port.sourceSwitchKeyword(), true);
        else port.selectNextCandidate();
    }

    public void start(String keyword, boolean automatic) {
        if (closed) return;
        stop();
        port.setSearchKeyword(keyword);
        List<Site> sites = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Site site : this.sites.get()) {
            if (site.isSearchable() && keys.add(site.getKey())) sites.add(site);
        }
        long expected = generation;
        port.startSearch(sites, keyword, automatic, () -> callbackDelivery.execute(() -> {
            if (!closed && expected == generation) settled();
        }));
    }

    public void settled() { if (!closed) selectReady(true); }

    private void selectReady(boolean settled) {
        if (settled && !port.loading() && !port.hasPending()) port.flushCandidates();
        switch (VodSourcePolicy.decide(settled, port.loading(), port.hasPending(), port.shouldSelectFirst(),
                port.automatic(), port.switching(), port.candidateCount(), port.earlyThresholdReached())) {
            case SELECT:
                port.flushCandidates();
                port.selectNextCandidate();
                break;
            case CLEAR_SWITCH: port.clearSourceSwitch(); break;
            case FINISH_MANUAL:
                finish();
                if (port.candidateCount() == 0) port.empty(false);
                break;
            case EMPTY_AUTOMATIC: finish(); port.empty(true); break;
            default: break;
        }
    }

    /** Stops result delivery while retaining discovered sources for later recovery. */
    public void finish() {
        generation++;
        port.cancelSearch();
    }

    public void stop() {
        finish();
        port.clearCandidates();
        candidateKeys.clear();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        finish();
        candidateKeys.clear();
    }
}
