package com.fongmi.android.tv.player.progress;

import com.fongmi.android.tv.bean.PlaybackProgress;

public interface EpisodeProgressStore {
    PlaybackProgress find(String historyKey, String episodeId);
    void save(PlaybackProgress progress);
    void delete(String historyKey);
    void deleteByConfig(int cid);
}
