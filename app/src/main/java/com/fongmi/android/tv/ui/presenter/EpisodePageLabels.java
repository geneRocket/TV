package com.fongmi.android.tv.ui.presenter;

import java.util.ArrayList;
import java.util.List;

/** Inclusive episode ranges matching the slices displayed by the episode pager. */
public final class EpisodePageLabels {
    private EpisodePageLabels() {}

    public static List<String> create(int total, int pageSize, boolean reverse) {
        if (total < 0 || pageSize <= 0) throw new IllegalArgumentException("Invalid episode page size");
        List<String> labels = new ArrayList<>();
        for (int start = 0; start < total;) {
            int end = start + Math.min(pageSize, total - start);
            int first = reverse ? total - start : start + 1;
            int last = reverse ? total - end + 1 : end;
            labels.add(first + " - " + last);
            start = end;
        }
        return labels;
    }
}
