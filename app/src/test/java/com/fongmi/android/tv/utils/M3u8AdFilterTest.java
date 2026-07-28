package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class M3u8AdFilterTest {

    @Test
    public void repeatedDurationsNeedStrongAdSignal() {
        assertFalse(M3u8AdFilter.shouldRemoveRepeatedPod(2, false));
        assertTrue(M3u8AdFilter.shouldRemoveRepeatedPod(2, true));
        assertFalse(M3u8AdFilter.shouldRemoveRepeatedPod(1, true));
    }

    @Test
    public void capsCueOutOnlyWhenDurationIsMissing() {
        assertFalse(M3u8AdFilter.shouldEndCue(0, 19));
        assertTrue(M3u8AdFilter.shouldEndCue(0, 20));
        assertFalse(M3u8AdFilter.shouldEndCue(30, 20));
        assertTrue(M3u8AdFilter.shouldEndCue(30, 240));
    }

    @Test
    public void commonCdnQueryKeysAreNotAdsByThemselves() {
        assertFalse(M3u8AdFilter.containsAdQueryKey("segment.ts?vid=movie&output=hls&sz=720"));
        assertTrue(M3u8AdFilter.containsAdQueryKey("segment.ts?adid=123"));
    }

    @Test
    public void removesAdMediaRenditionWithoutRestoringWholeMasterPlaylist() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Ad\",URI=\"ads/audio.m3u8\"\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1000000,AUDIO=\"audio\"\n"
                + "video/main.m3u8\n";

        String filtered = M3u8AdFilter.filterAdVariantPlaylists(playlist, "https://video.example/master.m3u8");

        assertFalse(filtered.contains("ads/audio.m3u8"));
        assertTrue(filtered.contains("video/main.m3u8"));
    }

    @Test
    public void countsLowLatencyPartsFromUriAttribute() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-PART:DURATION=0.5,URI=\"part-1.m4s\"\n"
                + "#EXT-X-PART:DURATION=0.5,URI=\"part-2.m4s\"\n";

        assertEquals(2, M3u8AdFilter.countSegmentsFromString(playlist));
    }
}
