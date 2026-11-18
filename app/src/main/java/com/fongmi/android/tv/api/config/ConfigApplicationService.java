package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.repository.ConfigRepository;
import com.fongmi.android.tv.utils.ConfigUrlParser;
import com.fongmi.android.tv.utils.ThreadPools;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The only loading path that publishes runtime state and applies cross-config effects. */
final class ConfigApplicationService {
    private static final Object APPLY_LOCK = new Object();
    private ConfigApplicationService() {}

    private enum VodChange { REPLACE, EXTEND }

    static void replaceVod(VodConfig candidate, ConfigDocument document) {
        applyVod(candidate, document, VodChange.REPLACE);
    }

    static void extendVod(VodConfig candidate, ConfigDocument document) {
        applyVod(candidate, document, VodChange.EXTEND);
    }

    private static void applyVod(VodConfig candidate, ConfigDocument document, VodChange change) {
        ConfigLoadTask.commit(() -> {
            synchronized (APPLY_LOCK) {
                ConfigRuntime.Snapshot previous = ConfigRuntime.snapshot();
                LinkedLive linked = prepareLinkedLive(candidate, document.object(), previous);
                candidate.persistSelection(document);
                if (linked != null) linked.candidate.persistSelection();
                if (change == VodChange.REPLACE) BaseLoader.get().clear();
                candidate.document(document);
                ConfigRuntime.publish(candidate, linked == null ? previous.live : linked.candidate);
                candidate.preloadJars(document.object());
                candidate.prewarmHome();
                if (linked != null) {
                    previous.live.retireReplacedBy(linked.candidate);
                    linked.candidate.preloadJars();
                    linked.saveSelection();
                }
                String wall = candidate.getWall();
                if (!wall.isEmpty() && WallConfig.get().needSync(wall)) {
                    WallConfig.synchronize(ConfigRepository.get().find(wall, candidate.getConfig().getName(), 2));
                }
                ConfigNetworkService.refresh();
                if (linked != null) linked.candidate.afterPublication();
            }
        });
    }

    static void live(LiveConfig candidate) {
        ConfigLoadTask.commit(() -> {
            synchronized (APPLY_LOCK) {
                candidate.persistSelection();
                ConfigRuntime.Snapshot previous = ConfigRuntime.snapshot();
                ConfigRuntime.publish(previous.vod, candidate);
                previous.live.retireReplacedBy(candidate);
                candidate.preloadJars();
                ConfigNetworkService.refresh();
                candidate.afterPublication();
            }
        });
    }

    static void clearVod() {
        ConfigLoadTask.commit(() -> {
            synchronized (APPLY_LOCK) {
                ConfigRuntime.publishVod(new VodConfig());
                BaseLoader.get().clear();
                ConfigNetworkService.refresh();
            }
        });
    }

    static boolean isInheritedSelection(String saved, String marker, List<String> previousVodUrls) {
        if (saved.isEmpty()) return false;
        if (marker != null) return saved.equals(marker);
        List<String> liveUrls = ConfigUrlParser.parse(saved);
        return !liveUrls.isEmpty() && liveUrls.containsAll(previousVodUrls) && previousVodUrls.containsAll(liveUrls);
    }

    static void commitUrlEdit(Config active, List<Config> selected) {
        List<Config> configs = selected.isEmpty() ? Collections.singletonList(active) : selected;
        Config changed = null;
        for (int i = 0; i < configs.size(); i++) {
            Config item = configs.get(i);
            if (!item.isUrlEdit()) continue;
            if (item != active && active.isUrlEdit() && active.getId() == item.getId()) {
                active.json(item.getJson()).time(item.getTime());
                active.commitUrlEdit();
                configs.set(i, active.copy());
            } else {
                item.commitUrlEdit();
            }
            changed = item;
        }
        if (changed == null) return;
        int type = changed.getType();
        List<String> urls = new ArrayList<>(), names = new ArrayList<>();
        for (Config item : configs) {
            urls.add(item.getUrl());
            names.add(item.getDesc());
        }
        String selection = String.join("\n", urls);
        String desc = names.get(0) + (names.size() == 1 ? "" : names.size() == 2
                ? " + " + names.get(1) : " +" + (names.size() - 1));
        if (type == 0) Setting.commitVodConfigSelection(Setting.beginVodConfigSelection(), selection, desc);
        else Setting.putExplicitLiveConfig(selection, desc);
    }

    private static LinkedLive prepareLinkedLive(VodConfig vod, JsonObject object, ConfigRuntime.Snapshot previous) {
        LiveConfig current = previous.live;
        if (current.isExplicitPlaylist() || !vod.loadsLive() || !object.has("lives") || object.get("lives").isJsonNull()) return null;
        List<String> urls = vod.getLoadUrls();
        if (urls.isEmpty()) urls = Collections.singletonList(vod.getConfig().getUrl());
        String savedSelection = Setting.getLiveConfigUrls();
        List<String> selected = ConfigUrlParser.parse(savedSelection);
        if (selected.isEmpty()) {
            String saved = current.getConfig().isEmpty() ? Config.live().getUrl() : current.getConfig().getUrl();
            if (!saved.isEmpty()) selected = Collections.singletonList(saved);
        }
        List<String> previousVodUrls = previous.vod.getLoadUrls();
        if (previousVodUrls.isEmpty()) previousVodUrls = ConfigUrlParser.parse(Setting.getVodConfigUrls());
        String marker = Setting.getInheritedLiveUrls();
        if ("".equals(marker) && !selected.isEmpty()) return null;
        boolean inherited = isInheritedSelection(savedSelection, marker, previousVodUrls);
        if (!inherited && !selected.isEmpty() && !(urls.containsAll(selected) && selected.containsAll(urls))) return null;
        boolean sync = inherited;
        for (String url : urls) if (current.needSync(url)) { sync = true; break; }
        if (!sync) return null;
        try {
            Config owner = ConfigRepository.get().find(vod.getConfig(), 1).save();
            LiveConfig candidate = LiveConfig.inherit(owner, object);
            if (!inherited && !selected.isEmpty()) {
                return new LinkedLive(candidate, Collections.emptyList(), Collections.emptyList());
            }
            List<String> values = new ArrayList<>(), names = new ArrayList<>();
            for (String url : urls) {
                if (url == null || url.isEmpty() || values.contains(url)) continue;
                values.add(url); names.add(ConfigRepository.get().find(url, 1).getDesc());
            }
            return new LinkedLive(candidate, values, names);
        } catch (Throwable error) {
            ConfigLoadTask.check();
            ThreadPools.log(error, "Inherited live build failed; retaining current live sources.");
            return null;
        }
    }

    private static final class LinkedLive {
        final LiveConfig candidate;
        final List<String> urls, names;
        LinkedLive(LiveConfig candidate, List<String> urls, List<String> names) {
            this.candidate = candidate; this.urls = urls; this.names = names;
        }
        void saveSelection() {
            if (urls.isEmpty()) return;
            String description = names.size() == 1 ? names.get(0) : names.size() == 2
                    ? names.get(0) + " + " + names.get(1) : names.get(0) + " +" + (names.size() - 1);
            Setting.putInheritedLiveConfig(String.join("\n", urls), description);
        }
    }
}
