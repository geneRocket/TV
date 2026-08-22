package com.fongmi.android.tv.player.progress;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.PlaybackProgress;

/** Owns stable per-episode resume identity and persistence. */
public final class EpisodeProgressManager {

    private static final EpisodeProgressManager INSTANCE = new EpisodeProgressManager(new RoomEpisodeProgressStore());

    private final EpisodeProgressStore store;

    EpisodeProgressManager(EpisodeProgressStore store) {
        this.store = store;
    }

    public static EpisodeProgressManager get() {
        return INSTANCE;
    }

    public long position(String historyKey, Episode episode, long fallback) {
        if (isEmpty(historyKey) || episode == null) return Math.max(fallback, 0);
        PlaybackProgress progress = store.find(historyKey, identity(episode));
        return progress == null ? Math.max(fallback, 0) : progress.getPosition();
    }

    public void checkpoint(String historyKey, int cid, Episode episode, long position, long duration) {
        if (isEmpty(historyKey) || episode == null || duration <= 0) return;
        PlaybackProgress progress = new PlaybackProgress();
        progress.setHistoryKey(historyKey);
        progress.setEpisodeId(identity(episode));
        progress.setCid(cid);
        progress.setEpisodeName(episode.getName());
        progress.setEpisodeUrl(episode.getUrl());
        progress.setPosition(position);
        progress.setDuration(duration);
        progress.setUpdatedAt(System.currentTimeMillis());
        store.save(progress);
    }

    public String identity(Episode episode) {
        if (episode == null) return "";
        String url = episode.getUrl() == null ? "" : episode.getUrl().trim();
        if (!isEmpty(url)) return url;
        String name = episode.getName() == null ? "" : episode.getName().trim();
        return name;
    }

    public void delete(String historyKey) {
        if (!isEmpty(historyKey)) store.delete(historyKey);
    }

    public void deleteByConfig(int cid) {
        store.deleteByConfig(cid);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
