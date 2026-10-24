package com.fongmi.android.tv.repository;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Vod;

import java.util.List;

/** Single data-access boundary for persisted playback history. */
public final class HistoryRepository {

    private static final HistoryRepository INSTANCE = new HistoryRepository();

    public static HistoryRepository get() {
        return INSTANCE;
    }

    private HistoryRepository() {
    }

    public History find(String key) {
        return HistoryStore.find(key);
    }

    public List<History> all() {
        return HistoryStore.get();
    }

    public List<History> all(int configId) {
        return HistoryStore.get(configId);
    }

    public List<History> loaded() {
        return HistoryStore.getLoaded();
    }

    public History prepare(History history, String key, int cid, Vod vod, float speed, String mark) {
        return HistoryStore.prepare(history, key, cid, vod, speed, mark);
    }

    public History prepareDefault(String key, int cid, Vod vod, float speed, String mark) {
        History history = new History();
        history.setKey(key);
        history.setCid(cid);
        history.setVodName(vod.getVodName());
        history.setVodPic(vod.getVodPic());
        history.setSpeed(speed);
        if (!vod.getVodFlags().isEmpty()) {
            com.fongmi.android.tv.bean.Flag flag = vod.getVodFlags().get(0);
            history.setVodFlag(flag.getFlag());
            if (!flag.getEpisodes().isEmpty()) history.setVodRemarks(flag.getEpisodes().get(0).getName());
        }
        if (mark != null && !mark.isEmpty()) history.setVodRemarks(mark);
        return history;
    }

    public History prepareBestMatch(String key, int cid, Vod vod, float speed, String mark) {
        History prepared = HistoryStore.prepare(find(key), key, cid, vod, speed, mark);
        History matched = HistoryStore.findBestMatch(prepared);
        return History.isNewerOrBetterSameVersion(matched, prepared)
                ? HistoryStore.prepare(matched, key, cid, vod, speed, mark)
                : prepared;
    }

    public History save(History history) {
        return HistoryStore.save(history);
    }

    public void saveAsync(History history) { HistoryStore.saveAsync(history); }

    public History delete(History history) {
        return HistoryStore.delete(history);
    }

    public History fromJson(String value) {
        return History.objectFrom(value);
    }

    public List<History> listFromJson(String value) {
        return History.arrayFrom(value);
    }

    public void delete(int cid) {
        HistoryStore.delete(cid);
    }

    public void deleteLoaded() {
        HistoryStore.deleteLoaded();
    }

    public Runnable prepareDeleteLoaded() {
        return HistoryStore.prepareDeleteLoaded();
    }

    public void sync(List<History> targets) {
        HistoryStore.sync(targets);
    }

    public void sync(int cid, List<History> targets, boolean force) {
        HistoryStore.sync(cid, targets, force);
    }

    public void clearMemoryCache() {
        HistoryStore.clearCache();
    }

    public void cancelPendingWrites() {
        HistoryStore.cancelPendingWrites();
    }

    public void withWritesPaused(Runnable action) {
        HistoryStore.withWritesPaused(action);
    }
}
