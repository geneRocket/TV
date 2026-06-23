package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Vod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class SearchSorter {

    private static final AtomicLong ORDER = new AtomicLong();

    private SearchSorter() {
    }

    public static List<Vod> merge(List<Vod> current, List<Vod> incoming, String keyword) {
        List<Vod> result = new ArrayList<>();
        Set<String> loaded = new HashSet<>();
        if (current != null) {
            for (Vod item : current) {
                String key = key(item);
                if (key.isEmpty() || !loaded.add(key)) continue;
                prepare(item, keyword);
                result.add(item);
            }
        }
        if (incoming != null) {
            for (Vod item : incoming) {
                String key = key(item);
                if (key.isEmpty() || !loaded.add(key)) continue;
                prepare(item, keyword);
                result.add(item);
            }
        }
        sort(result, keyword);
        return result;
    }

    public static void sort(List<Vod> items, String keyword) {
        if (items == null || items.size() < 2) return;
        for (Vod item : items) prepare(item, keyword);
        items.sort((left, right) -> compare(left, right, keyword, ""));
    }

    public static int compare(Vod left, Vod right, String keyword, String actor) {
        int result = Integer.compare(rank(left, keyword), rank(right, keyword));
        if (result != 0) return result;
        result = Double.compare(right.getScore(), left.getScore());
        if (result != 0) return result;
        result = Integer.compare(actorRank(left, actor), actorRank(right, actor));
        if (result != 0) return result;
        result = Long.compare(left.getInsertTimestamp(), right.getInsertTimestamp());
        if (result != 0) return result;
        return key(left).compareToIgnoreCase(key(right));
    }

    public static String key(Vod item) {
        if (item == null) return "";
        String site = item.getSiteKey();
        String id = item.getVodId();
        String name = Util.normalize(item.getVodName());
        if (TextUtils.isEmpty(site)) return "";
        return site + "@" + (TextUtils.isEmpty(id) ? name : id);
    }

    public static void prepare(Vod item, String keyword) {
        if (item == null) return;
        String target = Util.normalize(keyword);
        String name = Util.normalize(item.getVodName());
        String remark = Util.normalize(item.getVodRemarks());
        double score = Math.max(Util.similarity(name, target), Util.similarity(remark, target) * 0.9);
        item.setScore(score);
        if (item.getInsertTimestamp() == 0) item.setInsertTimestamp(ORDER.incrementAndGet());
    }

    private static int rank(Vod item, String keyword) {
        String target = Util.normalize(keyword);
        String name = Util.normalize(item == null ? "" : item.getVodName());
        String remark = Util.normalize(item == null ? "" : item.getVodRemarks());
        if (target.isEmpty()) return 9;
        if (name.equals(target)) return 0;
        if (name.startsWith(target)) return 1;
        if (remark.equals(target) || remark.startsWith(target)) return 2;
        if (name.contains(target)) return 3;
        if (remark.contains(target)) return 4;
        for (String token : target.split("\\s+")) if (!TextUtils.isEmpty(token) && (name.contains(token) || remark.contains(token))) return 5;
        return 6;
    }

    private static int actorRank(Vod item, String actor) {
        String target = Objects.toString(actor, "");
        String source = item == null ? "" : item.getVodActor();
        if (TextUtils.isEmpty(target) || TextUtils.isEmpty(source)) return 2;
        if (Util.similarity(source, target) >= 0.8) return 0;
        return hasActorOverlap(source, target) ? 1 : 3;
    }

    private static boolean hasActorOverlap(String source, String target) {
        for (String sourceActor : splitActors(source)) {
            if (sourceActor.isEmpty()) continue;
            for (String targetActor : splitActors(target)) {
                if (targetActor.isEmpty()) continue;
                if (sourceActor.equals(targetActor)) return true;
            }
        }
        return false;
    }

    private static String[] splitActors(String text) {
        return Objects.toString(text, "").trim().toLowerCase().split("[\\s,，、/／;；|｜]+");
    }
}
