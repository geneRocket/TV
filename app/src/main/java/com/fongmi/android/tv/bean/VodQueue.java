package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public final class VodQueue {

    private static final List<Vod> ITEMS = new ArrayList<>();
    private static String siteKey = "";

    private VodQueue() {
    }

    public static synchronized void set(String key, List<Vod> items) {
        siteKey = key == null ? "" : key;
        ITEMS.clear();
        if (items == null) return;
        for (Vod item : items) {
            if (item == null || item.isAction() || item.isFolder() || item.isManga()) continue;
            ITEMS.add(item);
        }
    }

    public static synchronized Item next(String key, String id, String name) {
        if (!TextUtils.equals(siteKey, key) || TextUtils.isEmpty(id)) return null;
        for (int i = 0; i < ITEMS.size() - 1; i++) {
            Vod item = ITEMS.get(i);
            if (TextUtils.equals(item.getVodId(), id) && TextUtils.equals(item.getVodName(), name)) return new Item(siteKey, ITEMS.get(i + 1));
        }
        return null;
    }

    public static final class Item {

        private final String siteKey;
        private final Vod vod;

        private Item(String siteKey, Vod vod) {
            this.siteKey = siteKey;
            this.vod = vod;
        }

        public String getSiteKey() {
            return siteKey;
        }

        public Vod getVod() {
            return vod;
        }
    }
}
