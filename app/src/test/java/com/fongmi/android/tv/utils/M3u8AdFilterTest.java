package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class M3u8AdFilterTest {

    private static final RealFixture[] REAL_FIXTURES = {
            new RealFixture("chimi-01.m3u8", 1645, 1645),
            new RealFixture("chimi-02.m3u8", 1647, 1647),
            new RealFixture("chimi-03.m3u8", 6551, 6533),
            new RealFixture("chimi-04.m3u8", 2214, 2178),
            new RealFixture("chimi-05.m3u8", 809, 809),
            new RealFixture("chimi-06.m3u8", 809, 809),
            new RealFixture("chimi-07.m3u8", 3267, 3267),
            new RealFixture("chimi-08.m3u8", 409, 409),
            new RealFixture("chimi-09.m3u8", 3179, 3179),
            new RealFixture("chimi-10.m3u8", 3262, 3262),
            new RealFixture("chimi-11.m3u8", 1315, 1315),
            new RealFixture("chimi-12.m3u8", 3303, 3279),
            new RealFixture("chimi-13.m3u8", 809, 809),
            new RealFixture("chimi-14.m3u8", 809, 809),
            new RealFixture("chimi-15.m3u8", 809, 809),
            new RealFixture("chimi-16.m3u8", 809, 809),
            new RealFixture("chimi-17.m3u8", 1651, 1651),
            new RealFixture("chimi-18.m3u8", 3346, 3265),
            new RealFixture("chimi-19.m3u8", 3267, 3267),
            new RealFixture("odyssey-01.m3u8", 1395, 1395, "https://1080p.huyall.com/play/zbqX53ap/index.m3u8"),
            new RealFixture("odyssey-02.m3u8", 3684, 3684, "https://b2.bdzybf22.com/videos/202505/27/6822707861f13823c825ad5a/011edg/index.m3u8"),
            new RealFixture("odyssey-03.m3u8", 1395, 1395, "https://hd.ijycnd.com/play/zbqX53ap/index.m3u8"),
            new RealFixture("odyssey-04.m3u8", 1395, 1395, "https://hn.bfvvs.com/play/penVX4e7/index.m3u8"),
            new RealFixture("odyssey-05.m3u8", 1395, 1395, "https://play.hhuus.com/play/QeZX3q5b/index.m3u8"),
            new RealFixture("odyssey-06.m3u8", 1395, 1395, "https://play.subokk.com/play/mep6K1bM/index.m3u8"),
            new RealFixture("odyssey-07.m3u8", 6649, 6631, "https://s1.fengbao9.com/video/aodesai/27f955720965/index.m3u8"),
            new RealFixture("odyssey-08.m3u8", 2495, 2495, "https://svip.ryiplay18.com/20260720/8194_82dad43b/2000k/hls/index.m3u8"),
            new RealFixture("odyssey-09.m3u8", 1395, 1395, "https://v.gsuus.com/play/xbo9Kzeg/index.m3u8"),
            new RealFixture("odyssey-10.m3u8", 2498, 2498, "https://v.lfthirtytwo.com/20260720/8777_6fbd0ae8/2000k/hls/mixed.m3u8"),
            new RealFixture("odyssey-11.m3u8", 3684, 3684, "https://v1.ppqrrs.com/wjv1/202308/19/KNhCf0iibU2/video/1000k_720/hls/index.m3u8"),
            new RealFixture("odyssey-12.m3u8", 3696, 3696, "https://v1.zuidazym3u8.com/yyv1/202308/19/KNhCf0iibU2/video/2000k_1080/hls/index.m3u8"),
            new RealFixture("odyssey-13.m3u8", 1857, 1857, "https://vip.ffzy-play7.com/20221105/1676_5181746d/2000k/hls/mixed.m3u8"),
            new RealFixture("qunti-01.m3u8", 1848, 1838),
            new RealFixture("qunti-02.m3u8", 1852, 1852),
            new RealFixture("qunti-03.m3u8", 7140, 7122),
            new RealFixture("qunti-04.m3u8", 2491, 2449),
            new RealFixture("qunti-07.m3u8", 3674, 3674),
            new RealFixture("qunti-08.m3u8", 3562, 3562),
            new RealFixture("qunti-09.m3u8", 3583, 3583),
            new RealFixture("qunti-10.m3u8", 1478, 1478),
            new RealFixture("qunti-11.m3u8", 3717, 3693),
            new RealFixture("qunti-16.m3u8", 1798, 1798),
            new RealFixture("qunti-17.m3u8", 3711, 3669),
            new RealFixture("spirited-away-01.m3u8", 1877, 1877),
            new RealFixture("spirited-away-02.m3u8", 1876, 1876),
            new RealFixture("station-360.m3u8", 3220, 3200),
            new RealFixture("station-fengbao.m3u8", 6407, 6389),
            new RealFixture("station-zuidai.m3u8", 3222, 3222),
            new RealFixture("xuan-an-current.m3u8", 2955, 2937),
            new RealFixture("xuan-an-episode-01.m3u8", 2735, 2717),
    };

    @Test
    public void filtersCapturedRealMediaPlaylistsWithoutRegressions() throws IOException {
        for (RealFixture fixture : REAL_FIXTURES) {
            String original = readRealFixture(fixture.name);
            String filtered = new String(M3u8AdFilter.filterMinorHost(
                    original.getBytes(StandardCharsets.UTF_8),
                    fixture.baseUrl), StandardCharsets.UTF_8);

            assertEquals(fixture.name + " input changed", fixture.before, M3u8AdFilter.countSegmentsFromString(original));
            assertEquals(fixture.name + " filtering changed", fixture.after, M3u8AdFilter.countSegmentsFromString(filtered));
            assertTrue(fixture.name, filtered.startsWith("#EXTM3U"));
            assertTrue(fixture.name, fixture.after > 0);
            if (original.contains("#EXT-X-ENDLIST")) assertTrue(fixture.name, filtered.contains("#EXT-X-ENDLIST"));
        }
    }

    @Test
    public void removesFrameVerifiedAdjumpPodsFromCapturedOdysseyPlaylist() throws IOException {
        String original = readRealFixture("odyssey-07.m3u8");
        String filtered = filter(original, "https://s1.fengbao9.com/video/aodesai/27f955720965/index.m3u8");

        assertFalse(filtered.contains("/video/adjump/"));
        assertTrue(filtered.contains("0000312.ts"));
        assertTrue(filtered.contains("0000313.ts"));
        assertEquals(6631, M3u8AdFilter.countSegmentsFromString(filtered));
    }

    @Test
    public void removesFrameVerifiedRepeatedCasinoPodsFromCapturedModuPlaylist() throws IOException {
        String original = readRealFixture("chimi-18.m3u8");
        String filtered = filter(original, "https://bf.modujx11.com/20260630/JbD1RgRr/index.m3u8");

        assertFalse(filtered.contains("/20260727/wRbpF6Qd/"));
        assertTrue(filtered.contains("b1x48sws.ts"));
        assertTrue(filtered.contains("fD37xao5.ts"));
        assertEquals(3265, M3u8AdFilter.countSegmentsFromString(filtered));
    }

    @Test
    public void repeatedDurationsNeedStrongAdSignal() {
        assertFalse(M3u8AdFilter.shouldRemoveRepeatedPod(2, false));
        assertTrue(M3u8AdFilter.shouldRemoveRepeatedPod(2, true));
        assertFalse(M3u8AdFilter.shouldRemoveRepeatedPod(1, true));
        assertTrue(M3u8AdFilter.shouldRemoveIdenticalMediaPod(2));
        assertFalse(M3u8AdFilter.shouldRemoveIdenticalMediaPod(1));
    }

    @Test
    public void capsCueOutOnlyWhenDurationIsMissing() {
        assertFalse(M3u8AdFilter.shouldEndCue(0, 19));
        assertTrue(M3u8AdFilter.shouldEndCue(0, 20));
        assertFalse(M3u8AdFilter.shouldEndCue(30, 20));
        assertTrue(M3u8AdFilter.shouldEndCue(30, 240));
        assertFalse(M3u8AdFilter.shouldEndCue(0, 20, true));
        assertTrue(M3u8AdFilter.shouldEndCue(0, 240, true));
    }

    @Test
    public void removesCompleteLongCueOutIntervalWhenCueInExists() {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        playlist.append(segment(5.0, "main-before.ts"));
        playlist.append("#EXT-X-CUE-OUT\n");
        for (int index = 0; index < 25; index++) playlist.append(segment(2.0, "break-" + index + ".ts"));
        playlist.append("#EXT-X-CUE-IN\n");
        playlist.append(segment(5.0, "main-after.ts"));
        playlist.append("#EXT-X-ENDLIST\n");

        String filtered = filter(playlist.toString());

        assertFalse(filtered.contains("break-0.ts"));
        assertFalse(filtered.contains("break-24.ts"));
        assertTrue(filtered.contains("main-before.ts"));
        assertTrue(filtered, filtered.contains("main-after.ts"));
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
    public void preservesVariantWithRandomVastSubstringInFilename() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1000000\n"
                + "video/hnMl3VAST7.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=2000000\n"
                + "video/main.m3u8\n";

        String filtered = M3u8AdFilter.filterAdVariantPlaylists(playlist, "https://video.example/master.m3u8");

        assertTrue(filtered.contains("hnMl3VAST7.m3u8"));
        assertTrue(filtered.contains("video/main.m3u8"));
    }

    @Test
    public void preservesVariantWhoseAudioDescriptionGroupIsNamedAd() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"ad\",NAME=\"Audio Description\",URI=\"audio/description.m3u8\"\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1000000,AUDIO=\"ad\"\n"
                + "video/main.m3u8\n";

        String filtered = M3u8AdFilter.filterAdVariantPlaylists(playlist, "https://video.example/master.m3u8");

        assertEquals(playlist, filtered);
    }

    @Test
    public void preservesNormalDateRangeWhoseIdContainsRescue() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-DATERANGE:ID=\"rescue-scene\",CLASS=\"chapter\",DURATION=30\n"
                + segment(5.0, "main-1.ts")
                + segment(5.0, "main-2.ts")
                + segment(5.0, "main-3.ts")
                + segment(5.0, "main-4.ts")
                + "#EXT-X-ENDLIST\n";

        assertEquals(4, M3u8AdFilter.countSegmentsFromString(filter(playlist)));
    }

    @Test
    public void removesStandardInterstitialDateRange() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "main-before.ts")
                + "#EXT-X-DATERANGE:ID=\"break-1\",CLASS=\"com.apple.hls.interstitial\",DURATION=10\n"
                + segment(5.0, "insert-1.ts")
                + segment(5.0, "insert-2.ts")
                + segment(5.0, "main-after.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertFalse(filtered.contains("insert-1.ts"));
        assertFalse(filtered.contains("insert-2.ts"));
        assertTrue(filtered.contains("main-before.ts"));
        assertTrue(filtered, filtered.contains("main-after.ts"));
    }

    @Test
    public void ignoresAdWordsInUnknownMetadataTags() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-CUSTOM-METADATA:TITLE=\"Sponsored advertisement\"\n"
                + "#EXT-X-DISCONTINUITY\n"
                + segment(5.0, "main-1.ts")
                + segment(5.0, "main-2.ts")
                + segment(5.0, "main-3.ts")
                + segment(5.0, "main-4.ts")
                + "#EXT-X-ENDLIST\n";

        assertEquals(4, M3u8AdFilter.countSegmentsFromString(filter(playlist)));
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

    @Test
    public void preservesUnconfirmedMinorHostLowLatencyPart() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-PART:DURATION=0.5,URI=\"https://main.example/part-1.m4s\"\n"
                + "#EXT-X-PART:DURATION=0.5,URI=\"https://main.example/part-2.m4s\"\n"
                + "#EXT-X-PART:DURATION=0.5,URI=\"https://minor.example/part-3.m4s\"\n"
                + "#EXT-X-PART:DURATION=0.5,URI=\"https://main.example/part-4.m4s\"\n"
                + "#EXT-X-PART:DURATION=0.5,URI=\"https://main.example/part-5.m4s\"\n"
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertTrue(filtered.contains("minor.example"));
        assertTrue(filtered.contains("URI=\"https://main.example/part-5.m4s\""));
        assertFalse(filtered.contains("\nhttps://main.example/part-5.m4s\n"));
        assertTrue(filtered.contains("#EXT-X-ENDLIST"));
    }

    @Test
    public void preservesUnconfirmedMinorHostIntervalBoundedByDiscontinuities() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "https://main.example/main-1.ts")
                + segment(5.0, "https://main.example/main-2.ts")
                + "#EXT-X-DISCONTINUITY\n"
                + segment(5.0, "https://insert.example/clip-1.ts")
                + segment(5.0, "https://insert.example/clip-2.ts")
                + "#EXT-X-DISCONTINUITY\n"
                + segment(5.0, "https://main.example/main-3.ts")
                + segment(5.0, "https://main.example/main-4.ts")
                + segment(5.0, "https://main.example/main-5.ts")
                + segment(5.0, "https://main.example/main-6.ts")
                + segment(5.0, "https://main.example/main-7.ts")
                + segment(5.0, "https://main.example/main-8.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertTrue(filtered.contains("insert.example/clip-1.ts"));
        assertTrue(filtered.contains("insert.example/clip-2.ts"));
        assertTrue(filtered.contains("main-8.ts"));
    }

    @Test
    public void preservesRandomFilenameContainingVastSubstring() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "https://cdn.example/main-1.ts")
                + segment(5.0, "https://cdn.example/hnMl3VAST7.ts")
                + segment(5.0, "https://cdn.example/main-2.ts")
                + "#EXT-X-ENDLIST\n";

        assertTrue(filter(playlist).contains("hnMl3VAST7.ts"));
    }

    @Test
    public void preservesMediaOnHostWhoseLabelMatchesShortAdToken() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "https://ima.example/video/main-1.ts")
                + segment(5.0, "https://ima.example/video/main-2.ts")
                + segment(5.0, "https://ima.example/video/main-3.ts")
                + "#EXT-X-ENDLIST\n";

        assertEquals(3, M3u8AdFilter.countSegmentsFromString(filter(playlist)));
    }

    @Test
    public void preservesRepeatedPathsWhenQueriesIdentifyDifferentMedia() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "main-before.ts")
                + "#EXT-X-DISCONTINUITY\n"
                + segment(3.0, "media.ts?range=0")
                + segment(3.0, "media.ts?range=1")
                + segment(3.0, "media.ts?range=2")
                + "#EXT-X-DISCONTINUITY\n"
                + segment(5.0, "main-middle.ts")
                + "#EXT-X-DISCONTINUITY\n"
                + segment(3.0, "media.ts?range=3")
                + segment(3.0, "media.ts?range=4")
                + segment(3.0, "media.ts?range=5")
                + "#EXT-X-DISCONTINUITY\n"
                + segment(5.0, "main-after.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertEquals(M3u8AdFilter.countSegmentsFromString(playlist), M3u8AdFilter.countSegmentsFromString(filtered));
        assertTrue(filtered.contains("range=0"));
        assertTrue(filtered.contains("range=5"));
    }

    @Test
    public void preservesRealLoneAdNumberFilenameCollisions() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "https://cdn.example/202604/15/main001.ts")
                + segment(5.0, "https://cdn.example/202604/15/AD1veWOf.ts")
                + segment(5.0, "https://cdn.example/202604/15/main002.ts")
                + segment(5.0, "https://cdn.example/202604/15/aD5b9wgLiG.ts")
                + segment(5.0, "https://cdn.example/202604/15/main003.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertTrue(filtered.contains("AD1veWOf.ts"));
        assertTrue(filtered.contains("aD5b9wgLiG.ts"));
        assertTrue(filtered.contains("main003.ts"));
    }

    @Test
    public void preservesAdNumberCollisionImmediatelyBeforeDiscontinuity() {
        String playlist = "#EXTM3U\n"
                + segment(4.0, "2f3dda1fb14e0264ff377a3b514892dc.ts")
                + segment(3.96, "ad0921c3cb348474f61ab43785c0edaf.ts")
                + "#EXT-X-DISCONTINUITY\n"
                + segment(4.0, "0c3634d2453f6e93f56e9c16b1fefa8e.ts")
                + segment(4.0, "3dbb5470d571a2f14d235120e6dd39d7.ts")
                + segment(4.0, "5426a43bd6667044c5fe00bc3784dc36.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertTrue(filtered.contains("ad0921c3cb348474f61ab43785c0edaf.ts"));
        assertTrue(filtered.contains("#EXT-X-DISCONTINUITY"));
        assertTrue(filtered.contains("#EXT-X-ENDLIST"));
    }

    @Test
    public void removesAdNumberFilenameWhenDiscontinuityConfirmsIt() {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        for (int index = 0; index < 20; index++) playlist.append(segment(5.0, "https://cdn.example/before" + index + ".ts"));
        playlist.append("#EXT-X-DISCONTINUITY\n");
        playlist.append(segment(4.0, "https://cdn.example/AD1veWOf.ts"));
        playlist.append("#EXT-X-DISCONTINUITY\n");
        for (int index = 0; index < 20; index++) playlist.append(segment(5.0, "https://cdn.example/after" + index + ".ts"));
        playlist.append("#EXT-X-ENDLIST\n");

        String filtered = filter(playlist.toString());

        assertFalse(filtered.contains("AD1veWOf.ts"));
        assertTrue(filtered.contains("after19.ts"));
        assertTrue(filtered.contains("#EXT-X-ENDLIST"));
    }

    @Test
    public void preservesAdNumberAtCommonDiscontinuityBoundary() {
        String playlist = "#EXTM3U\n"
                + segment(3.833333, "67a338adc71308e36fc9e4412276e689.ts")
                + "#EXT-X-DISCONTINUITY\n"
                + segment(6.083333, "ad2b67f39fa51f00b99448372bd9538f.ts")
                + segment(4.166667, "3acd09c2e53cd4508978a3af428f72eb.ts")
                + segment(5.0, "83788d672f283450a828e082bb4d3602.ts")
                + segment(4.166667, "4e48004d9ca6b622b397b3b96fd29b7c.ts")
                + segment(4.166667, "545e1c16223c61422b77d0e51a96477c.ts")
                + "#EXT-X-DISCONTINUITY\n"
                + segment(4.166667, "026ca5f42136db32736402e71ffa39a5.ts")
                + segment(3.375, "23687836f80aebfb30f460e0f3281bcc.ts")
                + segment(0.916667, "66b734854aa59b93e9eb5397c27a8d21.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertTrue(filtered.contains("ad2b67f39fa51f00b99448372bd9538f.ts"));
        assertEquals(M3u8AdFilter.countSegmentsFromString(playlist), M3u8AdFilter.countSegmentsFromString(filtered));
    }

    @Test
    public void removesContinuousAdNumberCluster() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "https://cdn.example/main001.ts")
                + segment(5.0, "https://cdn.example/main002.ts")
                + segment(4.0, "https://cdn.example/AD1first.ts")
                + segment(4.0, "https://cdn.example/ad2second.ts")
                + segment(5.0, "https://cdn.example/main003.ts")
                + segment(5.0, "https://cdn.example/main004.ts")
                + segment(5.0, "https://cdn.example/main005.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertFalse(filtered.contains("AD1first.ts"));
        assertFalse(filtered.contains("ad2second.ts"));
        assertTrue(filtered.contains("main005.ts"));
    }

    @Test
    public void removesRealRepeatedNineSegmentAdjumpPods() {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        playlist.append(segment(5.0, "https://video.example/main001.ts"));
        playlist.append(segment(5.0, "https://video.example/main002.ts"));
        appendAdjumpPod(playlist, "17766952867670000000");
        playlist.append("#EXT-X-DISCONTINUITY\n");
        playlist.append(segment(5.0, "https://video.example/main003.ts"));
        playlist.append(segment(5.0, "https://video.example/main004.ts"));
        appendAdjumpPod(playlist, "17766952867670000001");
        playlist.append("#EXT-X-DISCONTINUITY\n");
        playlist.append(segment(5.0, "https://video.example/main005.ts"));
        playlist.append(segment(5.0, "https://video.example/main006.ts"));
        playlist.append("#EXT-X-ENDLIST\n");

        String filtered = filter(playlist.toString());

        assertFalse(filtered.contains("/video/adjump/"));
        assertTrue(filtered.contains("main001.ts"));
        assertTrue(filtered.contains("main006.ts"));
        assertTrue(filtered.contains("#EXT-X-ENDLIST"));
    }

    @Test
    public void removesRepeatedUnlabelledMediaPodsFromRealIkunStructure() {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        playlist.append(segment(3.0, "https://kkzycdn.com/20260630/movie/hls/main001.ts"));
        appendUnlabelledPod(playlist, "/20260727/FxLgovfH/10128kb/hls/");
        playlist.append("#EXT-X-DISCONTINUITY\n");
        playlist.append(segment(3.0, "https://kkzycdn.com/20260630/movie/hls/main002.ts"));
        appendUnlabelledPod(playlist, "/20260727/FxLgovfH/10128kb/hls/");
        playlist.append("#EXT-X-DISCONTINUITY\n");
        playlist.append(segment(3.0, "https://kkzycdn.com/20260630/movie/hls/main003.ts"));
        playlist.append("#EXT-X-ENDLIST\n");

        String filtered = filter(playlist.toString());

        assertFalse(filtered.contains("FxLgovfH"));
        assertTrue(filtered.contains("main001.ts"));
        assertTrue(filtered.contains("main003.ts"));
    }

    private static void appendUnlabelledPod(StringBuilder playlist, String path) {
        playlist.append("#EXT-X-DISCONTINUITY\n");
        playlist.append("#EXT-X-KEY:METHOD=NONE\n");
        playlist.append(segment(3.0, path + "5k2wQ0ah.ts"));
        playlist.append(segment(3.0, path + "0LdxUmQT.ts"));
        playlist.append(segment(5.0, path + "IbA69VFL.ts"));
        playlist.append(segment(3.0, path + "cVvxQWp2.ts"));
        playlist.append(segment(3.0, path + "1INnmLQO.ts"));
        playlist.append(segment(0.64, path + "Mbz1HIak.ts"));
    }

    private static void appendAdjumpPod(StringBuilder playlist, String timestamp) {
        playlist.append("#EXT-X-DISCONTINUITY\n");
        for (int index = 0; index < 9; index++) {
            playlist.append(segment(2.889, "https://bf-cdn.example/video/adjump/time/" + timestamp + index + ".ts"));
        }
    }

    private static String segment(double duration, String uri) {
        return "#EXTINF:" + duration + ",\n" + uri + "\n";
    }

    private static String filter(String playlist) {
        return filter(playlist, "https://video.example/live.m3u8");
    }

    private static String filter(String playlist, String baseUrl) {
        byte[] filtered = M3u8AdFilter.filterMinorHost(playlist.getBytes(StandardCharsets.UTF_8), baseUrl);
        return new String(filtered, StandardCharsets.UTF_8);
    }

    private static String readRealFixture(String name) throws IOException {
        String path = "/m3u8/real/" + name;
        try (InputStream input = M3u8AdFilterTest.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing test resource: " + path);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static final class RealFixture {

        private final String name;
        private final int before;
        private final int after;
        private final String baseUrl;

        private RealFixture(String name, int before, int after) {
            this(name, before, after, "https://fixture.invalid/" + name);
        }

        private RealFixture(String name, int before, int after, String baseUrl) {
            this.name = name;
            this.before = before;
            this.after = after;
            this.baseUrl = baseUrl;
        }
    }
}
