package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.Subtitle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One complete screen state, including the accepted page rather than the requested page. */
public final class SubtitleSearchState {
    public enum Screen { SEARCH, FILES }
    public enum Phase { IDLE, LOADING, READY, ERROR, RESOLVING }
    public final String query;
    public final int page;
    public final Screen screen;
    public final Phase phase;
    public final List<Subtitle> items;
    public final boolean hasNext;
    public final SubtitleSource source;
    public final SubtitlePreference preference;
    public final List<String> unavailable;

    SubtitleSearchState(String query, int page, Screen screen, Phase phase, List<Subtitle> items, boolean hasNext) {
        this(query,page,screen,phase,items,hasNext,SubtitleSource.ALL,SubtitlePreference.CHINESE,Collections.emptyList());
    }
    SubtitleSearchState(String query, int page, Screen screen, Phase phase, List<Subtitle> items, boolean hasNext,
                        SubtitleSource source, SubtitlePreference preference, List<String> unavailable) {
        this.query = query; this.page = page; this.screen = screen; this.phase = phase;
        this.items = Collections.unmodifiableList(new ArrayList<>(items)); this.hasNext = hasNext;
        this.source = source; this.preference = preference;
        this.unavailable = Collections.unmodifiableList(new ArrayList<>(unavailable));
    }
    public boolean loading() { return phase == Phase.LOADING || phase == Phase.RESOLVING; }
    public boolean canPrevious() { return !loading() && screen == Screen.SEARCH && page > 1; }
    public boolean canNext() { return !loading() && screen == Screen.SEARCH && hasNext; }
    static SubtitleSearchState initial() {
        return new SubtitleSearchState("", 1, Screen.SEARCH, Phase.IDLE, Collections.emptyList(), false);
    }
}
