package com.fongmi.android.tv.bean;

import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class PersistedEntryProductTest {
    @Test public void historyAndFavoritesReopenTheCompleteOriginalVideoId() {
        for (int cid : new int[]{1, 7, 999}) {
            for (String site : new String[]{"site", cid + "@site"}) {
                for (String id : new String[]{"123", "https://video.example/a?b=1", "video@@@part", "video@@@123@@@part", "视频@@@分集"}) {
                    String key = History.historyKey(site, id, "title", cid);
                    History history = new History();
                    history.setKey(key); history.setCid(cid);
                    Keep keep = new Keep();
                    keep.setKey(key); keep.setCid(cid);
                    assertEquals(key, id, history.getVodId());
                    assertEquals(key, id, keep.getVodId());
                }
            }
        }
    }

    @Test public void embeddedSeparatorsAndNumericVideoPartsCannotChangeTheOwningConfig() throws Exception {
        for (java.lang.Class<?> type : new java.lang.Class<?>[]{com.fongmi.android.tv.repository.HistoryStore.class, com.fongmi.android.tv.repository.KeepStore.class}) {
            Method method = type.getDeclaredMethod("keyCid", String.class);
            method.setAccessible(true);
            for (int cid : new int[]{1, 7, 999}) {
                for (String id : new String[]{"video", "video@@@123", "video@@@123@@@part"}) {
                    String key = History.historyKey("site", id, "title", cid);
                    assertEquals(type.getSimpleName() + ":" + key, cid, method.invoke(null, key));
                }
            }
        }
    }

    @Test public void legacyKeysWithoutConfigSuffixStillOpenTheVideo() {
        for (String key : new String[]{"site@@@video", "7@site@@@video"}) {
            History history = new History(); history.setKey(key);
            Keep keep = new Keep(); keep.setKey(key);
            assertEquals("video", history.getVodId());
            assertEquals("video", keep.getVodId());
        }
    }
}
