package com.fongmi.android.tv.ui.player;

import android.text.TextUtils;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.SearchSorter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class TvVodSourceCandidates {

    private static final int LIMIT = 50;
    private static final int AUTO_FLUSH_THRESHOLD = 10;

    private TreeSet<Vod> set = new TreeSet<>(this::compare);
    private final Set<String> broken = new HashSet<>();
    private String keyword = "";

    public void setKeyword(String keyword) {
        String next = keyword == null ? "" : keyword;
        if (this.keyword.equals(next)) return;
        this.keyword = next;
        if (set.isEmpty()) return;
        TreeSet<Vod> reordered = new TreeSet<>(this::compare);
        reordered.addAll(set);
        while (reordered.size() > LIMIT) {
            reordered.pollLast();
        }
        this.set = reordered;
    }

    public boolean add(List<Vod> items) {
        boolean changed = false;
        for (Vod item : items) changed |= add(item);
        return changed;
    }

    public void markBroken(String key) {
        if (!TextUtils.isEmpty(key)) broken.add(key);
    }

    public Vod next(String currentBrokenKey) {
        Iterator<Vod> it = set.iterator();
        while (it.hasNext()) {
            Vod item = it.next();
            String key = SearchSorter.key(item);
            // 遇到坏源必须先从当前 set 中剔除，再跳过，防止污染候选池与阻塞状态
            if (!TextUtils.isEmpty(key) && (key.equals(currentBrokenKey) || broken.contains(key))) {
                it.remove();
                continue;
            }
            it.remove();
            return item;
        }
        return null;
    }

    public void remove(Vod item) {
        if (item == null) return;
        String targetKey = Objects.toString(SearchSorter.key(item), "");
        Iterator<Vod> it = set.iterator();
        while (it.hasNext()) {
            if (Objects.toString(SearchSorter.key(it.next()), "").equals(targetKey)) {
                it.remove();
                break;
            }
        }
    }

    public boolean hasItems() {
        return !set.isEmpty();
    }

    public int size() {
        return set.size();
    }

    public boolean shouldAutoFlush() {
        return set.size() >= AUTO_FLUSH_THRESHOLD;
    }

    public List<Vod> sortedItems() {
        return new ArrayList<>(set);
    }

    public void clearCandidates() {
        set.clear();
    }

    public void clearBroken() {
        broken.clear();
    }

    private boolean add(Vod item) {
        if (set.add(item)) {
            if (set.size() > LIMIT) {
                Vod last = set.pollLast();
                if (last == item) return false;
            }
            return true;
        }
        return false;
    }

    private int compare(Vod left, Vod right) {
        if (left == right) return 0;
        if (left == null) return -1;
        if (right == null) return 1;
        int c = SearchSorter.compare(left, right, keyword, "");
        if (c != 0) return c;

        // 防御 null，避免 NPE
        String k1 = Objects.toString(SearchSorter.key(left), "");
        String k2 = Objects.toString(SearchSorter.key(right), "");
        return k1.compareTo(k2);
    }
}