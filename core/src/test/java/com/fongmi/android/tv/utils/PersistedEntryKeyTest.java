package com.fongmi.android.tv.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class PersistedEntryKeyTest {
    @Test public void identitiesRoundTripAndRebaseWithoutTouchingOpaqueVideoIds() {
        for (int cid : new int[]{1, 9, Integer.MAX_VALUE}) {
            for (String site : new String[]{"site", cid + "@site", "name@site"}) {
                for (String video : new String[]{"123", "video@@@456@@@end", "@@@tail@@@", "https://host/path?a=1", "视频"}) {
                    String key = PersistedEntryKey.create(site, video, "ignored", cid);
                    assertEquals(video, PersistedEntryKey.video(key));
                    assertEquals(cid, PersistedEntryKey.config(key, 0));
                    assertEquals(PersistedEntryKey.rawSite(site), PersistedEntryKey.site(key));
                    String rebased = PersistedEntryKey.rebase(key, 77);
                    assertEquals(77, PersistedEntryKey.config(rebased, 0));
                    assertEquals(video, PersistedEntryKey.video(rebased));
                }
            }
        }
    }
    @Test public void legacyAndMalformedKeysUseExplicitFallbackWithoutThrowing() {
        assertEquals("video", PersistedEntryKey.video("7@site@@@video"));
        assertEquals(7, PersistedEntryKey.config("7@site@@@video", 9));
        assertEquals(9, PersistedEntryKey.config(null, 9));
        assertEquals(9, PersistedEntryKey.config("site@@@video@@@bad", 9));
        assertEquals("", PersistedEntryKey.video(null));
        assertEquals("", PersistedEntryKey.video("no-separator"));
    }
    @Test public void missingVideoIdUsesTheExistingNormalizedTitleFormat() {
        assertEquals("site@@@movie name@@@7", PersistedEntryKey.create("7@site", "", "Movie: Name", 7));
        assertNull(PersistedEntryKey.rebase(null, 7));
        assertEquals("legacy", PersistedEntryKey.rebase("legacy", 7));
    }
}
