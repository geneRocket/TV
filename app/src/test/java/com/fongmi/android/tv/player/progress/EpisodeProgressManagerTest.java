package com.fongmi.android.tv.player.progress;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.PlaybackProgress;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class EpisodeProgressManagerTest {

    @Test
    public void duplicateEpisodeNamesDoNotShareProgress() {
        MemoryStore store = new MemoryStore();
        EpisodeProgressManager manager = new EpisodeProgressManager(store);
        Episode first = Episode.create("正片", "episode-1-url");
        Episode second = Episode.create("正片", "episode-2-url");

        manager.checkpoint("history", 1, first, 600_000, 1_000_000);

        assertNotEquals(manager.identity(first), manager.identity(second));
        assertEquals(600_000, manager.position("history", first, 0));
        assertEquals(0, manager.position("history", second, 0));
    }

    private static final class MemoryStore implements EpisodeProgressStore {
        private final Map<String, PlaybackProgress> values = new LinkedHashMap<>();

        @Override public PlaybackProgress find(String historyKey, String episodeId) { return values.get(historyKey + "|" + episodeId); }
        @Override public void save(PlaybackProgress progress) { values.put(progress.getHistoryKey() + "|" + progress.getEpisodeId(), progress); }
        @Override public void delete(String historyKey) { values.keySet().removeIf(key -> key.startsWith(historyKey + "|")); }
        @Override public void deleteByConfig(int cid) { values.values().removeIf(value -> value.getCid() == cid); }
    }
}
