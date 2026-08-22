package com.fongmi.android.tv.player.progress;

import com.fongmi.android.tv.bean.PlaybackProgress;
import com.fongmi.android.tv.db.AppDatabase;

public final class RoomEpisodeProgressStore implements EpisodeProgressStore {

    @Override
    public PlaybackProgress find(String historyKey, String episodeId) {
        return AppDatabase.get().getPlaybackProgressDao().find(historyKey, episodeId);
    }

    @Override
    public void save(PlaybackProgress progress) {
        AppDatabase.get().getPlaybackProgressDao().insertOrUpdate(progress);
    }

    @Override
    public void delete(String historyKey) {
        AppDatabase.get().getPlaybackProgressDao().delete(historyKey);
    }

    @Override
    public void deleteByConfig(int cid) {
        AppDatabase.get().getPlaybackProgressDao().delete(cid);
    }
}
