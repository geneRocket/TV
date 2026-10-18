package com.fongmi.android.tv.ui.adapter;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class TvPersistedAdapterDiffTest {
    private History history(int cid, String name) {
        return new History() {
            @Override public String getKey() { return "same-key"; }
            @Override public int getCid() { return cid; }
            @Override public String getVodName() { return name; }
        };
    }

    private Keep keep(int cid, String name) {
        return new Keep() {
            @Override public String getKey() { return "same-key"; }
            @Override public int getCid() { return cid; }
            @Override public String getVodName() { return name; }
        };
    }

    @Test public void historyRefreshCannotTreatIdentityAsUnchangedContent() {
        assertFalse(HistoryAdapter.diffCallback(Collections.singletonList(history(1, "before")),
                Collections.singletonList(history(1, "after"))).areContentsTheSame(0, 0));
    }

    @Test public void favoriteRefreshCannotTreatIdentityAsUnchangedContent() {
        assertFalse(KeepAdapter.diffCallback(Collections.singletonList(keep(1, "before")),
                Collections.singletonList(keep(1, "after"))).areContentsTheSame(0, 0));
    }

    @Test public void historyIdentityIncludesConfiguration() {
        assertFalse(HistoryAdapter.diffCallback(Collections.singletonList(history(1, "same")),
                Collections.singletonList(history(2, "same"))).areItemsTheSame(0, 0));
    }

    @Test public void favoriteIdentityIncludesConfiguration() {
        assertFalse(KeepAdapter.diffCallback(Collections.singletonList(keep(1, "same")),
                Collections.singletonList(keep(2, "same"))).areItemsTheSame(0, 0));
    }
}
