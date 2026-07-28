package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

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
        assertTrue(M3u8AdFilter.containsAdQueryKey("segment.ts?category=pre%72oll"));
        assertFalse(M3u8AdFilter.containsAdQueryKey("segment.ts?type=video#label=ad"));
    }

    @Test
    public void removesAdMediaRenditionWithoutRestoringWholeMasterPlaylist() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Ad\",URI=\"ads/audio.m3u8\"\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Main\",URI=\"audio/main.m3u8\"\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1000000,AUDIO=\"audio\"\n"
                + "video/main.m3u8\n";

        String filtered = M3u8AdFilter.filterAdVariantPlaylists(playlist, "https://video.example/master.m3u8");

        assertFalse(filtered.contains("ads/audio.m3u8"));
        assertTrue(filtered.contains("audio/main.m3u8"));
        assertTrue(filtered.contains("video/main.m3u8"));
    }

    @Test
    public void preservesOnlyRenditionInReferencedMediaGroup() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Ad\",URI=\"ads/audio.m3u8\"\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1000000,AUDIO=\"audio\"\n"
                + "video/main.m3u8\n";

        String filtered = M3u8AdFilter.filterAdVariantPlaylists(playlist, "https://video.example/master.m3u8");

        assertTrue(filtered.contains("ads/audio.m3u8"));
        assertTrue(filtered.contains("AUDIO=\"audio\""));
    }

    @Test
    public void countsLowLatencyPartsFromUriAttribute() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-PART:DURATION=0.5,URI=\"part-1.m4s\"\n"
                + "#EXT-X-PART:DURATION=0.5,URI=\"part-2.m4s\"\n";

        assertEquals(2, M3u8AdFilter.countSegmentsFromString(playlist));
    }

    @Test
    public void preservesLowLatencyPartStructureAfterFiltering() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-DISCONTINUITY\n"
                + "#EXT-X-PROGRAM-DATE-TIME:2026-07-29T00:00:00Z\n"
                + "#EXT-X-PART:DURATION=0.5,URI=\"part-1.m4s\"\n"
                + "#EXT-X-PART:DURATION=0.5,URI=\"part-2.m4s\"\n"
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertTrue(filtered.contains("#EXT-X-DISCONTINUITY\n#EXT-X-PROGRAM-DATE-TIME"));
        assertTrue(filtered.contains("URI=\"part-1.m4s\""));
        assertTrue(filtered.contains("URI=\"part-2.m4s\""));
        assertFalse(filtered.contains("\npart-1.m4s\n"));
        assertTrue(filtered.contains("#EXT-X-ENDLIST"));
    }

    @Test
    public void removesOnlyAdLowLatencyPart() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-PART:DURATION=0.5,URI=\"part-1.m4s\"\n"
                + "#EXT-X-PART:DURATION=0.5,URI=\"ads/ad-1.m4s\"\n"
                + "#EXT-X-PART:DURATION=0.5,URI=\"part-2.m4s\"\n"
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertTrue(filtered.contains("URI=\"part-1.m4s\""));
        assertFalse(filtered.contains("ads/ad-1.m4s"));
        assertTrue(filtered.contains("URI=\"part-2.m4s\""));
        assertTrue(filtered.contains("#EXT-X-ENDLIST"));
    }

    private static String filter(String playlist) {
        byte[] filtered = M3u8AdFilter.filterMinorHost(playlist.getBytes(StandardCharsets.UTF_8), "https://video.example/live.m3u8");
        return new String(filtered, StandardCharsets.UTF_8);
    }
}
