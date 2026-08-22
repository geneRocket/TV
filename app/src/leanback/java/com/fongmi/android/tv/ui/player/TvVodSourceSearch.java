package com.fongmi.android.tv.ui.player;

import android.text.TextUtils;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.SearchSorter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Owns cross-site source discovery and candidate selection. */
public final class TvVodSourceSearch {

    private static final double MIN_SCORE = 0.5;

    public interface Port {
        boolean accepts(Result result);
        String expectedKeyword();
        String sourceSwitchKeyword();
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
        void onSearchSettled();
    }

    private final Set<String> candidateKeys = new HashSet<>();
    private final Port port;

    public TvVodSourceSearch(Port port) {
        this.port = port;
    }

    public void accept(Result result) {
        if (!port.accepts(result) || !TextUtils.equals(result.getKeyword(), port.expectedKeyword())) return;
        List<Vod> items = result.getList();
        if (items.isEmpty()) return;
        Iterator<Vod> iterator = items.iterator();
        String keyword = port.sourceSwitchKeyword();
        while (iterator.hasNext()) {
            Vod item = iterator.next();
            SearchSorter.prepare(item, keyword);
            if (item.getScore() < MIN_SCORE || !candidateKeys.add(port.sourceKey(item))) iterator.remove();
        }
        if (items.isEmpty()) return;
        port.mergeCandidates(items, result.getRequestToken());
        port.cancelEmptyFallback();
    }

    public void select(Vod item) {
        port.removeCandidate(item);
        port.beginSourceSwitch();
        port.markCurrentSourceBroken();
        port.setManual();
        port.startSourceLoading();
        port.openDetail(item);
    }

    public void recover() {
        if (port.candidateCount() == 0) start(port.sourceSwitchKeyword(), true);
        else port.selectNextCandidate();
    }

    public void start(String keyword, boolean automatic) {
        stop();
        port.setSearchKeyword(keyword);
        List<Site> sites = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Site site : VodConfig.get().getSites()) {
            if (site.isSearchable() && keys.add(site.getKey())) sites.add(site);
        }
        port.startSearch(sites, keyword, automatic, port::onSearchSettled);
    }

    public void stop() {
        port.cancelSearch();
        port.clearCandidates();
        candidateKeys.clear();
    }
}
