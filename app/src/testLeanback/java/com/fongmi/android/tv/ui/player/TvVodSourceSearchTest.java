package com.fongmi.android.tv.ui.player;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class TvVodSourceSearchTest {

    @Test public void completedManualSearchRetainsCandidatesForRecovery() {
        Port port = new Port();
        Vod first = new Vod();
        Vod backup = new Vod();
        port.candidates.addAll(Arrays.asList(first, backup));
        TvVodSourceSearch search = new TvVodSourceSearch(port);
        search.finish();
        assertEquals(2, port.candidates.size());
        search.recover();
        assertSame(first, port.opened);
        assertSame(backup, port.candidates.get(0));
        assertEquals(1, port.cancelled);
        assertEquals(0, port.searches);
    }

    @Test public void readyAfterManualSelectionRetainsTheRemainingBackup() {
        Port port = new Port();
        Vod selected = new Vod();
        Vod backup = new Vod();
        port.candidates.addAll(Arrays.asList(selected, backup));
        TvVodSourceSearch search = new TvVodSourceSearch(port);
        search.select(selected);
        assertSame(selected, port.opened);
        search.finish();
        assertEquals(1, port.candidates.size());
        search.recover();
        assertSame(backup, port.opened);
        assertTrue(port.candidates.isEmpty());
        assertEquals(0, port.searches);
    }

    @Test public void explicitStopStillDiscardsCandidatesAfterSearchHasFinished() {
        Port port = new Port();
        port.candidates.add(new Vod());
        TvVodSourceSearch search = new TvVodSourceSearch(port);
        search.finish();
        search.stop();
        assertTrue(port.candidates.isEmpty());
        assertEquals(2, port.cancelled);
    }

    private static final class Port implements TvVodSourceSearch.Port {
        final List<Vod> candidates = new ArrayList<>();
        Vod opened;
        int cancelled;
        int searches;

        @Override public boolean accepts(Result result) { return true; }
        @Override public String expectedKeyword() { return "film"; }
        @Override public String sourceSwitchKeyword() { return "film"; }
        @Override public boolean automatic() { return false; }
        @Override public String sourceKey(Vod vod) { return ""; }
        @Override public void mergeCandidates(List<Vod> items, String token) { candidates.addAll(items); }
        @Override public void cancelEmptyFallback() {}
        @Override public void removeCandidate(Vod vod) { candidates.remove(vod); }
        @Override public void beginSourceSwitch() {}
        @Override public void markCurrentSourceBroken() {}
        @Override public void setManual() {}
        @Override public void startSourceLoading() {}
        @Override public void openDetail(Vod vod) { opened = vod; }
        @Override public int candidateCount() { return candidates.size(); }
        @Override public void selectNextCandidate() { opened = candidates.remove(0); }
        @Override public void cancelSearch() { cancelled++; }
        @Override public void clearCandidates() { candidates.clear(); }
        @Override public void setSearchKeyword(String keyword) {}
        @Override public void startSearch(List<Site> sites, String keyword, boolean auto, Runnable settled) { searches++; }
        @Override public void onSearchSettled() {}
    }
}
