package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class M3u8AdFilterTest {

    private static final String[] LIVE_FIXTURES = {
            "odyssey-01-live.m3u8",
            "odyssey-03-live.m3u8",
            "odyssey-04-live.m3u8",
            "odyssey-05-live.m3u8",
            "odyssey-06-live.m3u8",
            "odyssey-07-live.m3u8",
            "odyssey-08-live.m3u8",
            "odyssey-09-live.m3u8",
    };

    private static final String[] TVBOX_SITE_FIXTURES = {
            "tvbox-360--360.m3u8",
            "tvbox-baidu.m3u8",
            "tvbox-baidu-2.m3u8",
            "tvbox-dytt.m3u8",
            "tvbox-feifan.m3u8",
            "tvbox-feifan-2.m3u8",
            "tvbox-guangsu.m3u8",
            "tvbox-hongniu.m3u8",
            "tvbox-huya.m3u8",
            "tvbox-ikun.m3u8",
            "tvbox-jisu.m3u8",
            "tvbox-liangzi.m3u8",
            "tvbox-taopian.m3u8",
            "tvbox-uku.m3u8",
            "tvbox-uku-2.m3u8",
            "tvbox-xinlang.m3u8",
            "tvbox-yinghua.m3u8",
            "tvbox-zuidai.m3u8",
    };

    private static final RealFixture[] REAL_FIXTURES = {
            new RealFixture("chimi-01.m3u8", null),
            new RealFixture("chimi-02.m3u8", null),
            new RealFixture("chimi-03.m3u8", null, "/video/adjump/"),
            new RealFixture("chimi-04.m3u8", null, "/20260727/FxLgovfH/"),
            new RealFixture("chimi-05.m3u8", null),
            new RealFixture("chimi-06.m3u8", null),
            new RealFixture("chimi-07.m3u8", null),
            new RealFixture("chimi-08.m3u8", null),
            new RealFixture("chimi-09.m3u8", null),
            new RealFixture("chimi-10.m3u8", null),
            new RealFixture("chimi-11.m3u8", null),
            new RealFixture("chimi-12.m3u8", null, "/20260523/fgtjx0Du/"),
            new RealFixture("chimi-13.m3u8", null),
            new RealFixture("chimi-14.m3u8", null),
            new RealFixture("chimi-15.m3u8", null),
            new RealFixture("chimi-16.m3u8", null),
            new RealFixture("chimi-17.m3u8", null),
            new RealFixture("chimi-18.m3u8", null, "/20260727/wRbpF6Qd/"),
            new RealFixture("chimi-19.m3u8", null),
            new RealFixture("odyssey-01.m3u8", "https://1080p.huyall.com/play/zbqX53ap/index.m3u8"),
            new RealFixture("odyssey-02.m3u8", "https://b2.bdzybf22.com/videos/202505/27/6822707861f13823c825ad5a/011edg/index.m3u8"),
            new RealFixture("odyssey-03.m3u8", "https://hd.ijycnd.com/play/zbqX53ap/index.m3u8"),
            new RealFixture("odyssey-04.m3u8", "https://hn.bfvvs.com/play/penVX4e7/index.m3u8"),
            new RealFixture("odyssey-05.m3u8", "https://play.hhuus.com/play/QeZX3q5b/index.m3u8"),
            new RealFixture("odyssey-06.m3u8", "https://play.subokk.com/play/mep6K1bM/index.m3u8"),
            new RealFixture("odyssey-07.m3u8", "https://s1.fengbao9.com/video/aodesai/27f955720965/index.m3u8", "/video/adjump/"),
            new RealFixture("odyssey-08.m3u8", "https://svip.ryiplay18.com/20260720/8194_82dad43b/2000k/hls/index.m3u8"),
            new RealFixture("odyssey-09.m3u8", "https://v.gsuus.com/play/xbo9Kzeg/index.m3u8"),
            new RealFixture("odyssey-10.m3u8", "https://v.lfthirtytwo.com/20260720/8777_6fbd0ae8/2000k/hls/mixed.m3u8"),
            new RealFixture("odyssey-11.m3u8", "https://v1.ppqrrs.com/wjv1/202308/19/KNhCf0iibU2/video/1000k_720/hls/index.m3u8"),
            new RealFixture("odyssey-12.m3u8", "https://v1.zuidazym3u8.com/yyv1/202308/19/KNhCf0iibU2/video/2000k_1080/hls/index.m3u8"),
            new RealFixture("odyssey-13.m3u8", "https://vip.ffzy-play7.com/20221105/1676_5181746d/2000k/hls/mixed.m3u8"),
            new RealFixture("qunti-01.m3u8", null, "511bbce97206bf92ec35dc252f066430.ts", "b737810c3a808938877100d2a1319b6e.ts", "d70225e17c7aaebd79f810d4d5c90a98.ts", "431209dd72f8045900592416a82ad61f.ts", "c5e66da16786c7f70f2356f66a921597.ts"),
            new RealFixture("qunti-02.m3u8", null),
            new RealFixture("qunti-03.m3u8", null, "/video/adjump/"),
            new RealFixture("qunti-04.m3u8", null, "/20260727/FxLgovfH/"),
            new RealFixture("qunti-07.m3u8", null),
            new RealFixture("qunti-08.m3u8", null),
            new RealFixture("qunti-09.m3u8", null),
            new RealFixture("qunti-10.m3u8", null),
            new RealFixture("qunti-11.m3u8", null, "/20260523/fgtjx0Du/"),
            new RealFixture("qunti-16.m3u8", null),
            new RealFixture("qunti-17.m3u8", null, "/20260727/YfRHNor8/"),
            new RealFixture("spirited-away-01.m3u8", null),
            new RealFixture("spirited-away-02.m3u8", null),
            new RealFixture("station-360.m3u8", null, "/20260726/1AS9nSvi/"),
            new RealFixture("station-fengbao.m3u8", null, "/video/adjump/"),
            new RealFixture("station-zuidai.m3u8", null),
            new RealFixture("xuan-an-current.m3u8", null, "/video/adjump/"),
            new RealFixture("xuan-an-episode-01.m3u8", null, "/video/adjump/"),
    };

    @Test
    public void filtersCapturedRealMediaPlaylistsWithoutRegressions() throws IOException {
        for (RealFixture fixture : REAL_FIXTURES) {
            String original = readRealFixture(fixture.name);
            String filtered = new String(M3u8AdFilter.filterMinorHost(
                    original.getBytes(StandardCharsets.UTF_8),
                    fixture.baseUrl), StandardCharsets.UTF_8);

            assertOnlyAdsRemoved(fixture.name, original, filtered, fixture.adUris);
        }
    }

    @Test
    public void filtersCapturedMultiHostVodPlaylistsByLabelledContent() throws IOException {
        for (String name : LIVE_FIXTURES) {
            String original = readLiveFixture(name);
            String filtered = filter(original, "https://live-fixture.invalid/" + name);
            assertOnlyAdsRemoved(name, original, filtered,
                    name.equals("odyssey-07-live.m3u8") ? new String[]{"/video/adjump/"} : new String[0]);
        }
    }

    @Test
    public void preservesTvBoxMasterReferencesAndFiltersLabelledMedia() throws IOException {
        for (String name : TVBOX_SITE_FIXTURES) {
            String original = readTvBoxFixture(name);
            String filtered = filter(original, "https://tvbox-site-fixture.invalid/" + name);
            // Several captures are master playlists: check their child references,
            // not a fictional count of media segments in an unfetched child.
            assertOnlyAdsRemoved(name, original, filtered,
                    name.equals("tvbox-yinghua.m3u8") ? new String[]{"11466_IMADS.jpeg"} : new String[0]);
        }
    }

    @Test
    public void removesPlainAdResourceUriFromTvBoxYinghuaPlaylist() throws IOException {
        String original = readTvBoxFixture("tvbox-yinghua.m3u8");
        String filtered = filter(original, "https://vod1.wgslsw.com/20240413/YZJUyne9/index.m3u8");

        assertTrue(original.contains("IMADS.jpeg"));
        assertFalse(filtered.contains("IMADS.jpeg"));
        assertTrue(filtered.contains("11467_VpZN6.jpeg"));
    }

    @Test
    public void removesAdjumpFromCurrentLiveFengbaoPlaylist() throws IOException {
        String original = readLiveFixture("odyssey-07-live.m3u8");
        String filtered = filter(original, "https://s1.fengbao9.com/video/aodesai/27f955720965/index.m3u8");

        assertTrue(original.contains("/video/adjump/"));
        assertFalse(filtered.contains("/video/adjump/"));
        assertOnlyAdsRemoved("adjump", original, filtered, "/video/adjump/");
    }

    @Test
    public void removesFrameVerifiedAdjumpPodsFromCapturedOdysseyPlaylist() throws IOException {
        String original = readRealFixture("odyssey-07.m3u8");
        String filtered = filter(original, "https://s1.fengbao9.com/video/aodesai/27f955720965/index.m3u8");

        assertFalse(filtered.contains("/video/adjump/"));
        assertTrue(filtered.contains("0000312.ts"));
        assertTrue(filtered.contains("0000313.ts"));
        assertOnlyAdsRemoved("adjump", original, filtered, "/video/adjump/");
    }

    @Test
    public void removesFrameVerifiedRepeatedCasinoPodsFromCapturedModuPlaylist() throws IOException {
        String original = readRealFixture("chimi-18.m3u8");
        String filtered = filter(original, "https://bf.modujx11.com/20260630/JbD1RgRr/index.m3u8");

        assertFalse(filtered.contains("/20260727/wRbpF6Qd/"));
        assertTrue(filtered.contains("b1x48sws.ts"));
        assertTrue(filtered.contains("fD37xao5.ts"));
        assertOnlyAdsRemoved("casino pod", original, filtered, "/20260727/wRbpF6Qd/");
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
    public void removesScte35OutIntervalWithNeutralSegmentUris() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "main-before.ts")
                + "#EXT-X-SCTE35-OUT:DURATION=12,SCTE35=\"/DAvAAAAAAAAAP/wFAUAAAAEf+//yBysAAA==\"\n"
                + segment(4.0, "stitched/clip-1.ts")
                + "#EXT-X-SCTE35-OUT-CONT:ElapsedTime=4,Duration=12,SCTE35=\"/DAvAAAAAAAAAP/wFAUAAAAEf+//yBysAAA==\"\n"
                + segment(4.0, "stitched/clip-2.ts")
                + "#EXT-X-SCTE35-OUT-CONT:ElapsedTime=8,Duration=12,SCTE35=\"/DAvAAAAAAAAAP/wFAUAAAAEf+//yBysAAA==\"\n"
                + segment(4.0, "stitched/clip-3.ts")
                + "#EXT-X-SCTE35-IN\n"
                + segment(5.0, "main-after.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertFalse(filtered.contains("stitched/clip-1.ts"));
        assertFalse(filtered.contains("stitched/clip-3.ts"));
        assertTrue(filtered.contains("main-before.ts"));
        assertTrue(filtered.contains("main-after.ts"));
    }

    @Test
    public void removesCueOutContTagPlacedBetweenExtinfAndUri() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "main-before.ts")
                + "#EXTINF:4.0,\n"
                + "#EXT-X-CUE-OUT-CONT:ElapsedTime=4,Duration=12,SCTE35=\"/DAvAAAAAAAAAP/wFAUAAAAEf+//yBysAAA==\"\n"
                + "neutral-ad-1.ts\n"
                + "#EXTINF:4.0,\n"
                + "#EXT-X-CUE-OUT-CONT:ElapsedTime=8,Duration=12,SCTE35=\"/DAvAAAAAAAAAP/wFAUAAAAEf+//yBysAAA==\"\n"
                + "neutral-ad-2.ts\n"
                + "#EXT-X-CUE-IN\n"
                + segment(5.0, "main-after.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertFalse(filtered.contains("neutral-ad-1.ts"));
        assertFalse(filtered.contains("neutral-ad-2.ts"));
        assertTrue(filtered.contains("main-before.ts"));
        assertTrue(filtered.contains("main-after.ts"));
    }

    @Test
    public void preservesScte35MetadataWithoutOutDirection() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "main-before.ts")
                + "#EXT-X-SCTE35:SCTE35=\"/DAvAAAAAAAAAP/wFAUAAAAEf+//yBysAAA==\"\n"
                + segment(5.0, "main-middle.ts")
                + segment(5.0, "main-after.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertTrue(filtered.contains("main-before.ts"));
        assertTrue(filtered.contains("main-middle.ts"));
        assertTrue(filtered.contains("main-after.ts"));
        assertEquals(mediaEntries(playlist), mediaEntries(filtered));
    }

    @Test
    public void commonCdnQueryKeysAreNotAdsByThemselves() {
        assertFalse(M3u8AdFilter.containsAdQueryKey("segment.ts?vid=movie&output=hls&sz=720"));
        assertTrue(M3u8AdFilter.containsAdQueryKey("segment.ts?adid=123"));
        assertTrue(M3u8AdFilter.containsAdQueryKey("segment.ts?category=pre%72oll"));
        assertTrue(M3u8AdFilter.containsAdQueryKey("segment.ts?placement=midroll"));
        assertTrue(M3u8AdFilter.containsAdQueryKey("segment.ts?kind=preroll"));
        assertTrue(M3u8AdFilter.containsAdQueryKey("segment.ts?content_type=advertisement"));
        assertTrue(M3u8AdFilter.containsAdQueryKey("segment.ts?u=https%3A%2F%2Fads.example%2Fvast%2Fad-1.ts"));
        assertTrue(M3u8AdFilter.containsAdQueryKey("segment.ts?iu=%2F123%2Fmidroll"));
        assertTrue(M3u8AdFilter.containsAdQueryKey("segment.ts?cust_params=slot%3Dpreroll"));
        assertFalse(M3u8AdFilter.containsAdQueryKey("segment.ts?type=video#label=ad"));
        assertFalse(M3u8AdFilter.containsAdQueryKey("segment.ts?u=https%3A%2F%2Fcdn.example%2Fmovie%2Fmain-1.ts"));
    }

    @Test
    public void removesSegmentsWithAdResourceHiddenInGenericQueryValue() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "https://cdn.example/movie/main-1.ts")
                + segment(5.0, "https://cdn.example/proxy.ts?u=https%3A%2F%2Fpubads.g.doubleclick.net%2Fgampad%2Fad-1.ts")
                + segment(5.0, "https://cdn.example/proxy.ts?redirect=%2Fvast%2Fad-2.ts")
                + segment(5.0, "https://cdn.example/movie/main-2.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertFalse(filtered.contains("pubads.g.doubleclick"));
        assertFalse(filtered.contains("redirect=%2Fvast%2Fad-2.ts"));
        assertTrue(filtered.contains("main-1.ts"));
        assertTrue(filtered.contains("main-2.ts"));
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
    public void removesAdMediaRenditionMarkedOnlyByName() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Advertisement\",URI=\"audio/track-2.m3u8\"\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Main\",URI=\"audio/main.m3u8\"\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1000000,AUDIO=\"audio\"\n"
                + "video/main.m3u8\n";

        String filtered = M3u8AdFilter.filterAdVariantPlaylists(playlist, "https://video.example/master.m3u8");

        assertFalse(filtered.contains("track-2.m3u8"));
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

        assertEquals(mediaEntries(playlist), mediaEntries(filter(playlist)));
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
    public void removesAppleInterstitialDateRangeWithAssetUri() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "main-before.ts")
                + "#EXT-X-DATERANGE:ID=\"stitched-1\",CLASS=\"com.apple.hls.interstitial\",START-DATE=\"2026-08-15T00:00:00Z\",DURATION=12,X-ASSET-URI=\"https://ads.example/preroll/ad.m3u8\"\n"
                + segment(4.0, "https://ads.example/preroll/ad-1.ts")
                + segment(4.0, "https://ads.example/preroll/ad-2.ts")
                + segment(4.0, "https://ads.example/preroll/ad-3.ts")
                + segment(5.0, "main-after.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertFalse(filtered.contains("preroll/ad-1.ts"));
        assertFalse(filtered.contains("X-ASSET-URI"));
        assertTrue(filtered.contains("main-before.ts"));
        assertTrue(filtered.contains("main-after.ts"));
    }

    @Test
    public void removesInterstitialDateRangeMarkedByContentType() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "main-before.ts")
                + "#EXT-X-DATERANGE:ID=\"splice-1\",START-DATE=\"2026-08-15T00:00:00Z\",DURATION=10,X-CONTENT-TYPE=\"ADVERTISEMENT\",X-PLACEMENT=\"midroll\"\n"
                + segment(5.0, "neutral-insert-1.ts")
                + segment(5.0, "neutral-insert-2.ts")
                + segment(5.0, "main-after.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertFalse(filtered.contains("neutral-insert-1.ts"));
        assertFalse(filtered.contains("X-CONTENT-TYPE"));
        assertTrue(filtered.contains("main-before.ts"));
        assertTrue(filtered.contains("main-after.ts"));
    }

    @Test
    public void removesStrongAdDateRangeWithoutDurationUntilNextDiscontinuity() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "https://cdn.example/movie/main-before.ts")
                + "#EXT-X-DATERANGE:ID=\"stitched-1\",CLASS=\"com.apple.hls.interstitial\",START-DATE=\"2026-08-15T00:00:00Z\",X-ASSET-URI=\"https://ads.example/vmap/break.m3u8\"\n"
                + "#EXT-X-DISCONTINUITY\n"
                + segment(4.0, "https://stitched-cdn.example/neutral/clip-1.ts")
                + segment(4.0, "https://stitched-cdn.example/neutral/clip-2.ts")
                + "#EXT-X-DISCONTINUITY\n"
                + segment(5.0, "https://cdn.example/movie/main-after.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertFalse(filtered.contains("neutral/clip-1.ts"));
        assertFalse(filtered.contains("neutral/clip-2.ts"));
        assertTrue(filtered.contains("main-before.ts"));
        assertTrue(filtered.contains("main-after.ts"));
        assertTrue(filtered.contains("#EXT-X-ENDLIST"));
    }

    @Test
    public void preservesWeakDateRangeWithoutDuration() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "main-before.ts")
                + "#EXT-X-DATERANGE:ID=\"cue-1\",CLASS=\"chapter\",START-DATE=\"2026-08-15T00:00:00Z\"\n"
                + "#EXT-X-DISCONTINUITY\n"
                + segment(5.0, "chapter-clip-1.ts")
                + segment(5.0, "chapter-clip-2.ts")
                + "#EXT-X-DISCONTINUITY\n"
                + segment(5.0, "main-after.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertTrue(filtered.contains("chapter-clip-1.ts"));
        assertTrue(filtered.contains("chapter-clip-2.ts"));
        assertEquals(mediaEntries(playlist), mediaEntries(filtered));
    }

    @Test
    public void removesDateRangeMarkedByScteCmd() {
        String playlist = "#EXTM3U\n"
                + segment(6.0, "main-before.ts")
                + "#EXT-X-DATERANGE:ID=\"splice-1\",SCTE35-CMD=\"/DAvAAAAAAAAAP/wFAUAAAAEf+//yBysAAA==\",PLANNED-DURATION=12\n"
                + segment(6.0, "media-package-ad-1.ts?adid=splice")
                + segment(6.0, "media-package-ad-2.ts?adid=splice")
                + segment(6.0, "main-after.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertFalse(filtered.contains("media-package-ad-1.ts"));
        assertFalse(filtered.contains("SCTE35-CMD"));
        assertTrue(filtered.contains("main-after.ts"));
    }

    @Test
    public void removesAssetListTagAndFollowingAdSegments() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "main-before.ts")
                + "#EXT-X-ASSET-LIST:URI=\"https://ads.example/vast/assets.json\"\n"
                + segment(5.0, "https://ads.example/vast/asset-1.ts")
                + segment(5.0, "https://ads.example/vast/asset-2.ts")
                + segment(5.0, "main-after.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertFalse(filtered.contains("EXT-X-ASSET-LIST"));
        assertFalse(filtered.contains("vast/asset-1.ts"));
        assertTrue(filtered.contains("main-before.ts"));
        assertTrue(filtered.contains("main-after.ts"));
    }

    @Test
    public void removesGoogleImaQueryAdSegments() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "https://cdn.example/video/main-1.ts")
                + segment(5.0, "https://pubads.g.doubleclick.net/pagead/segment.ts?ad_type=video&iu=/123/preroll")
                + segment(5.0, "https://pubads.g.doubleclick.net/pagead/segment2.ts?ad_type=video&iu=/123/preroll")
                + segment(5.0, "https://cdn.example/video/main-2.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertFalse(filtered.contains("doubleclick"));
        assertTrue(filtered.contains("main-1.ts"));
        assertTrue(filtered.contains("main-2.ts"));
    }

    @Test
    public void preservesContentAssetListWithoutAdSignals() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-ASSET-LIST:URI=\"https://cdn.example/movie/assets.json\"\n"
                + segment(5.0, "https://cdn.example/movie/main-1.ts")
                + segment(5.0, "https://cdn.example/movie/main-2.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertTrue(filtered.contains("EXT-X-ASSET-LIST"));
        assertEquals(mediaEntries(playlist), mediaEntries(filtered));
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

        assertEquals(mediaEntries(playlist), mediaEntries(filter(playlist)));
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
    public void preservesShortLeadingPodWithoutAdEvidence() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "https://insert.example/opening/clip-1.ts")
                + segment(5.0, "https://insert.example/opening/clip-2.ts")
                + "#EXT-X-DISCONTINUITY\n"
                + segment(5.0, "https://main.example/movie/main-1.ts")
                + segment(5.0, "https://main.example/movie/main-2.ts")
                + segment(5.0, "https://main.example/movie/main-3.ts")
                + segment(5.0, "https://main.example/movie/main-4.ts")
                + segment(5.0, "https://main.example/movie/main-5.ts")
                + segment(5.0, "https://main.example/movie/main-6.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertTrue(filtered.contains("insert.example/opening"));
        assertTrue(filtered.contains("main.example/movie/main-1.ts"));
        assertTrue(filtered.contains("#EXT-X-ENDLIST"));
    }

    @Test
    public void preservesShortTrailingPodWithoutAdEvidence() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "https://main.example/movie/main-1.ts")
                + segment(5.0, "https://main.example/movie/main-2.ts")
                + segment(5.0, "https://main.example/movie/main-3.ts")
                + segment(5.0, "https://main.example/movie/main-4.ts")
                + segment(5.0, "https://main.example/movie/main-5.ts")
                + segment(5.0, "https://main.example/movie/main-6.ts")
                + "#EXT-X-DISCONTINUITY\n"
                + segment(5.0, "https://insert.example/closing/clip-1.ts")
                + segment(5.0, "https://insert.example/closing/clip-2.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertTrue(filtered.contains("insert.example/closing"));
        assertTrue(filtered.contains("main.example/movie/main-6.ts"));
        assertTrue(filtered.contains("#EXT-X-ENDLIST"));
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

        assertEquals(mediaEntries(playlist), mediaEntries(filter(playlist)));
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

        assertEquals(mediaEntries(playlist), mediaEntries(filtered));
        assertTrue(filtered.contains("range=0"));
        assertTrue(filtered.contains("range=5"));
    }

    @Test
    public void preservesRepeatedSegmentUrisWithoutAdEvidence() {
        String playlist = "#EXTM3U\n"
                + segment(5.0, "https://cdn.example/video/main-1.ts")
                + segment(5.0, "https://cdn.example/video/repeated.ts")
                + segment(5.0, "https://cdn.example/video/main-2.ts")
                + segment(5.0, "https://cdn.example/video/repeated.ts")
                + segment(5.0, "https://cdn.example/video/repeated.ts?range=1")
                + segment(5.0, "https://cdn.example/video/repeated.ts?range=2")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertEquals(mediaEntries(playlist), mediaEntries(filtered));
        assertTrue(filtered.contains("https://cdn.example/video/repeated.ts\n"));
        assertTrue(filtered.contains("repeated.ts?range=1"));
        assertTrue(filtered.contains("repeated.ts?range=2"));
        assertTrue(filtered.contains("main-1.ts"));
        assertTrue(filtered.contains("main-2.ts"));
    }

    @Test
    public void preservesRepeatedByteRangeSegmentsOnSameResource() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-VERSION:4\n"
                + "#EXT-X-BYTERANGE:100@0\n"
                + segment(5.0, "https://cdn.example/video/media.ts")
                + "#EXT-X-BYTERANGE:100@100\n"
                + segment(5.0, "https://cdn.example/video/media.ts")
                + "#EXT-X-BYTERANGE:100@200\n"
                + segment(5.0, "https://cdn.example/video/media.ts")
                + segment(5.0, "https://cdn.example/video/main-2.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertEquals(mediaEntries(playlist), mediaEntries(filtered));
        assertTrue(filtered.contains("#EXT-X-BYTERANGE:100@0"));
        assertTrue(filtered.contains("#EXT-X-BYTERANGE:100@200"));
    }

    @Test
    public void preservesImplicitRepeatedByteRangeSegmentsOnSameResource() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-VERSION:4\n"
                + "#EXT-X-BYTERANGE:100@0\n"
                + segment(5.0, "https://cdn.example/video/media.ts")
                + "#EXT-X-BYTERANGE:100\n"
                + segment(5.0, "https://cdn.example/video/media.ts")
                + "#EXT-X-BYTERANGE:100\n"
                + segment(5.0, "https://cdn.example/video/media.ts")
                + segment(5.0, "https://cdn.example/video/main-2.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertEquals(mediaEntries(playlist), mediaEntries(filtered));
        assertTrue(filtered.contains("https://cdn.example/video/media.ts\n"));
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
        assertEquals(mediaEntries(playlist), mediaEntries(filtered));
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

    @Test
    public void removesRepeatedUnlabelledPrerollWithoutOpeningDiscontinuity() {
        for (boolean postroll : new boolean[]{false, true}) {
            StringBuilder pod = new StringBuilder();
            appendUnlabelledPod(pod, "/20260727/FxLgovfH/10128kb/hls/");
            StringBuilder playlist = new StringBuilder("#EXTM3U\n");
            playlist.append(pod.substring("#EXT-X-DISCONTINUITY\n".length()));
            playlist.append("#EXT-X-DISCONTINUITY\n");
            for (int i = 0; i < 20; i++) playlist.append(segment(5, "movie/" + i + ".ts"));
            playlist.append(pod);
            if (!postroll) playlist.append("#EXT-X-DISCONTINUITY\n").append(segment(5, "movie/end.ts"));
            playlist.append("#EXT-X-ENDLIST\n");

            String filtered = filter(playlist.toString());
            assertFalse(filtered.contains("FxLgovfH"));
            assertOnlyAdsRemoved("repeated preroll/postroll", playlist.toString(), filtered, "/20260727/FxLgovfH/");
            for (int i = 0; i < 20; i++) assertTrue(filtered.contains("movie/" + i + ".ts"));
            assertTrue(filtered.startsWith("#EXTM3U\n"));
            assertTrue(filtered.contains("#EXT-X-ENDLIST"));
        }
    }

    @Test
    public void preservesDifferentOpeningAndEndingWithIdenticalDurations() {
        StringBuilder opening = new StringBuilder();
        appendUnlabelledPod(opening, "https://intro.example/opening/");
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        playlist.append(opening.substring("#EXT-X-DISCONTINUITY\n".length()));
        playlist.append("#EXT-X-DISCONTINUITY\n");
        for (int i = 0; i < 20; i++) playlist.append(segment(5, "movie/" + i + ".ts"));
        appendUnlabelledPod(playlist, "https://credits.example/ending/");
        playlist.append("#EXT-X-ENDLIST\n");
        assertEquals(playlist.toString(), filter(playlist.toString()));
    }

    @Test
    public void removesShortDiscontinuityPodWhenKeySwitchesAwayAndBack() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"main.key\"\n"
                + segment(5.0, "main-1.ts")
                + segment(5.0, "main-2.ts")
                + "#EXT-X-DISCONTINUITY\n"
                + "#EXT-X-KEY:METHOD=NONE\n"
                + segment(3.0, "insert-1.ts")
                + segment(3.0, "insert-2.ts")
                + segment(3.0, "insert-3.ts")
                + "#EXT-X-DISCONTINUITY\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"main.key\"\n"
                + segment(5.0, "main-3.ts")
                + segment(5.0, "main-4.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertFalse(filtered.contains("insert-1.ts"));
        assertFalse(filtered.contains("METHOD=NONE"));
        assertTrue(filtered.contains("main-1.ts"));
        assertTrue(filtered.contains("main-4.ts"));
    }

    @Test
    public void preservesSequenceDerivedEncryptedPlaylistWhenFilteringWouldShiftIv() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-VERSION:3\n"
                + "#EXT-X-MEDIA-SEQUENCE:100\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"main.key\"\n"
                + segment(5.0, "main-1.ts")
                + segment(5.0, "https://cdn.example/video/repeated.ts")
                + segment(5.0, "main-2.ts")
                + segment(5.0, "https://cdn.example/video/repeated.ts")
                + segment(5.0, "main-3.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertEquals(mediaEntries(playlist), mediaEntries(filtered));
        assertTrue(filtered.contains("https://cdn.example/video/repeated.ts\n"));
        assertTrue(filtered.contains("#EXT-X-MEDIA-SEQUENCE:100"));
    }

    @Test
    public void keyUriContainingIvTextDoesNotPretendToProvideExplicitIv() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-VERSION:3\n"
                + "#EXT-X-MEDIA-SEQUENCE:100\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"main.key?iv=value\"\n"
                + segment(5.0, "main-1.ts")
                + segment(5.0, "https://cdn.example/video/repeated.ts")
                + segment(5.0, "main-2.ts")
                + segment(5.0, "https://cdn.example/video/repeated.ts")
                + segment(5.0, "main-3.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertEquals(mediaEntries(playlist), mediaEntries(filtered));
    }

    @Test
    public void explicitIvStillAllowsRepeatedSegmentFiltering() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-VERSION:3\n"
                + "#EXT-X-MEDIA-SEQUENCE:100\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"main.key\",IV=0x1\n"
                + segment(5.0, "main-1.ts")
                + segment(5.0, "https://cdn.example/ads/repeated.ts")
                + segment(5.0, "main-2.ts")
                + segment(5.0, "https://cdn.example/ads/repeated.ts")
                + segment(5.0, "main-3.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertOnlyAdsRemoved("explicit IV", playlist, filtered, "/ads/repeated.ts");
    }

    @Test
    public void preservesDiscontinuityPodWhenKeySwitchDoesNotReturn() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"main-a.key\"\n"
                + segment(5.0, "main-1.ts")
                + segment(5.0, "main-2.ts")
                + "#EXT-X-DISCONTINUITY\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"main-b.key\"\n"
                + segment(5.0, "main-3.ts")
                + segment(5.0, "main-4.ts")
                + segment(5.0, "main-5.ts")
                + "#EXT-X-ENDLIST\n";

        String filtered = filter(playlist);

        assertTrue(filtered.contains("main-b.key"));
        assertTrue(filtered.contains("main-3.ts"));
        assertEquals(mediaEntries(playlist), mediaEntries(filtered));
    }

    @Test
    public void boundedCueDoesNotSpillPastExplicitDiscontinuityAd() {
        String playlist = "#EXTM3U\n" + segment(5, "main-before.ts")
                + "#EXT-X-CUE-OUT:8\n#EXT-X-DISCONTINUITY\n"
                + segment(4, "ads/clip-1.ts") + segment(4, "ads/clip-2.ts")
                + "#EXT-X-DISCONTINUITY\n" + segment(5, "main-after.ts")
                + segment(5, "main-last.ts") + "#EXT-X-ENDLIST\n";
        String filtered = filter(playlist);
        assertFalse(filtered.contains("ads/"));
        assertTrue(filtered.contains("main-before.ts"));
        assertTrue(filtered.contains("main-after.ts"));
        assertTrue(filtered.contains("main-last.ts"));
    }

    @Test
    public void continuationUsesRemainingDurationInSlidingWindow() {
        for (String value : new String[]{"ElapsedTime=8,Duration=12", "8/12"}) {
            String playlist = "#EXTM3U\n#EXT-X-CUE-OUT-CONT:" + value + "\n"
                    + segment(4, "clip.ts") + segment(4, "main-1.ts")
                    + segment(4, "main-2.ts") + "#EXT-X-ENDLIST\n";
            String filtered = filter(playlist);
            assertFalse(filtered.contains("clip.ts"));
            assertTrue(filtered.contains("main-1.ts"));
            assertTrue(filtered.contains("main-2.ts"));
        }
    }

    @Test
    public void continuationWithoutDurationDoesNotTreatElapsedAsRemaining() {
        String playlist = "#EXTM3U\n#EXT-X-CUE-OUT-CONT:ElapsedTime=4\n"
                + segment(4, "clip-1.ts") + segment(4, "clip-2.ts")
                + "#EXT-X-CUE-IN\n" + segment(5, "main.ts") + "#EXT-X-ENDLIST\n";
        String filtered = filter(playlist);
        assertFalse(filtered.contains("clip-1.ts"));
        assertFalse(filtered.contains("clip-2.ts"));
        assertTrue(filtered.contains("main.ts"));
    }

    @Test
    public void ignoresAdAttributesInsideQuotedMetadata() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-DATERANGE:ID=\"chapter\",X-NOTE=\"text,X-AD-ID=123,SCTE35-OUT=bad\",DURATION=8\n"
                + segment(4, "main-1.ts") + segment(4, "main-2.ts")
                + segment(4, "main-3.ts") + "#EXT-X-ENDLIST\n";
        assertEquals(playlist, filter(playlist));
    }

    @Test
    public void quotedFakeDurationDoesNotOverrideRealAdDuration() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-DATERANGE:ID=\"break\",CLASS=\"ad\",X-NOTE=\"text,DURATION=100\",DURATION=4\n"
                + segment(4, "clip.ts") + segment(4, "main-1.ts")
                + segment(4, "main-2.ts") + "#EXT-X-ENDLIST\n";
        String filtered = filter(playlist);
        assertFalse(filtered.contains("clip.ts"));
        assertTrue(filtered.contains("main-1.ts"));
    }

    @Test
    public void preservesThreeDifferentRotatingKeys() {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        for (int period = 0; period < 3; period++) {
            if (period > 0) playlist.append("#EXT-X-DISCONTINUITY\n");
            playlist.append("#EXT-X-KEY:METHOD=AES-128,URI=\"key-").append(period).append("\",IV=0x1\n");
            for (int segment = 0; segment < 4; segment++) {
                playlist.append(segment(4, "main-" + period + "-" + segment + ".ts"));
            }
        }
        playlist.append("#EXT-X-ENDLIST\n");
        assertEquals(playlist.toString(), filter(playlist.toString()));
    }

    @Test
    public void completedContinuationDoesNotOpenUnboundedBreak() {
        String playlist = "#EXTM3U\n#EXT-X-CUE-OUT-CONT:ElapsedTime=12,Duration=12\n"
                + segment(4, "main-1.ts") + segment(4, "main-2.ts") + "#EXT-X-ENDLIST\n";
        assertEquals(mediaEntries(playlist), mediaEntries(filter(playlist)));
    }

    @Test
    public void cueBetweenExtinfAndUriControlsWholeInterval() {
        String playlist = "#EXTM3U\n" + segment(4, "main-before.ts")
                + "#EXTINF:4,\n#EXT-X-CUE-OUT:8\nclip-1.ts\n"
                + segment(4, "clip-2.ts")
                + "#EXTINF:4,\n#EXT-X-CUE-IN\nmain-after.ts\n#EXT-X-ENDLIST\n";
        String filtered = filter(playlist);
        assertFalse(filtered.contains("clip-1.ts"));
        assertFalse(filtered.contains("clip-2.ts"));
        assertTrue(filtered.contains("main-before.ts"));
        assertTrue(filtered.contains("main-after.ts"));
    }

    @Test
    public void preservesByteRangesAcrossRepeatedDiscontinuityPods() {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        playlist.append(segment(4, "main.ts"));
        for (int period = 0; period < 2; period++) {
            playlist.append("#EXT-X-DISCONTINUITY\n");
            for (int index = 0; index < 3; index++) {
                playlist.append("#EXT-X-BYTERANGE:100@").append((period * 3 + index) * 100).append("\n");
                playlist.append(segment(4, "media.ts"));
            }
        }
        playlist.append("#EXT-X-ENDLIST\n");
        assertEquals(playlist.toString(), filter(playlist.toString()));
    }

    @Test
    public void preservesLowLatencyPartsWithDifferentByteRanges() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-PART:DURATION=1,URI=\"media.mp4\",BYTERANGE=\"100@0\"\n"
                + "#EXT-X-PART:DURATION=1,URI=\"media.mp4\",BYTERANGE=\"100@100\"\n"
                + "#EXT-X-PART:DURATION=1,URI=\"last.mp4\"\n";
        assertEquals(playlist, filter(playlist));
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

    @Test
    public void preservesDisabledAdFlagsAndOpaqueQueryValuesButRemovesEnabledAds() {
        String playlist = "#EXTM3U\n"
                + segment(5, "main-1.ts?ad=0")
                + segment(5, "main-2.ts?ads=false")
                + segment(5, "main-3.ts?token=opaque-ad-signature")
                + segment(5, "main-4.ts?title=advertisement")
                + segment(5, "break.ts?ad=1")
                + segment(5, "main-5.ts") + "#EXT-X-ENDLIST\n";
        String filtered = filter(playlist);
        for (int i = 1; i <= 5; i++) assertTrue(filtered, filtered.contains("main-" + i + ".ts"));
        assertFalse(filtered.contains("break.ts"));
        assertOnlyAdsRemoved("query flags", playlist, filtered, "break.ts?ad=1");
    }

    @Test
    public void preservesActualSubtitleSegmentsWithoutMasterPlaylist() {
        String playlist = "#EXTM3U\n" + segment(5, "subtitles/part-1.vtt")
                + segment(5, "subtitles/ads/part-2.vtt") + "#EXT-X-ENDLIST\n";
        assertEquals(playlist, filter(playlist));
    }

    @Test
    public void captionWordsInVideoPathsDoNotDisableAdFiltering() {
        String playlist = "#EXTM3U\n"
                + segment(5, "movie-with-subtitles/main-1.ts")
                + segment(5, "ads/break.ts")
                + segment(5, "captioned/main-2.ts") + "#EXT-X-ENDLIST\n";
        String filtered = filter(playlist);
        assertFalse(filtered.contains("break.ts"));
        assertTrue(filtered.contains("main-1.ts"));
        assertTrue(filtered.contains("main-2.ts"));
    }

    @Test
    public void subtitleWhitelistDoesNotCoverVideoAtSameEndpointWithDifferentQuery() {
        try {
            filter("#EXTM3U\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"s\",NAME=\"English\",URI=\"https://cdn.example/playlist?track=subtitles\"\n",
                    "https://cdn.example/master.m3u8");
            String playlist = "#EXTM3U\n" + segment(5, "main-1.ts") + segment(5, "ads/break.ts")
                    + segment(5, "main-2.ts") + "#EXT-X-ENDLIST\n";
            assertEquals(playlist, filter(playlist, "https://cdn.example/playlist?track=subtitles"));
            String filtered = filter(playlist, "https://cdn.example/playlist?track=video");
            assertFalse(filtered.contains("break.ts"));
            assertTrue(filtered.contains("main-1.ts"));
            assertTrue(filtered.contains("main-2.ts"));
        } finally {
            M3u8AdFilter.clearSubtitlePlaylistWhitelist();
        }
    }

    @Test
    public void preservesAudioDescriptionRenditionsAlongsideNormalAudio() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"English AD\",URI=\"audio/description.m3u8\"\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Described\",CHARACTERISTICS=\"public.accessibility.describes-video\",URI=\"audio/ad.m3u8\"\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Main\",URI=\"audio/main.m3u8\"\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1000000,AUDIO=\"audio\"\nvideo/main.m3u8\n";
        assertEquals(playlist, M3u8AdFilter.filterAdVariantPlaylists(playlist, "https://video.example/master.m3u8"));
    }

    @Test
    public void neverRemovesEveryRenditionFromReferencedAudioGroup() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Ad one\",URI=\"ads/one.m3u8\"\n"
                + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Ad two\",URI=\"ads/two.m3u8\"\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=1000000,AUDIO=\"audio\"\nvideo/main.m3u8\n";
        String filtered = M3u8AdFilter.filterAdVariantPlaylists(playlist, "https://video.example/master.m3u8");
        assertTrue(filtered.contains("#EXT-X-MEDIA:TYPE=AUDIO"));
        assertTrue(filtered.contains("video/main.m3u8"));
    }

    @Test
    public void removesPercentEncodedAdDirectoryWithoutMatchingRandomHashes() {
        String playlist = "#EXTM3U\n" + segment(5, "main-1.ts")
                + segment(5, "%61ds/break.ts") + segment(5, "a61dsb9.ts")
                + segment(5, "main-2.ts") + "#EXT-X-ENDLIST\n";
        String filtered = filter(playlist);
        assertFalse(filtered.contains("break.ts"));
        assertTrue(filtered.contains("a61dsb9.ts"));
        assertTrue(filtered.contains("main-1.ts"));
        assertTrue(filtered.contains("main-2.ts"));
    }

    @Test
    public void preservesNormalOpeningAndEndingOnSeparateResources() {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        playlist.append(segment(5, "https://intro.example/opening/1.ts"));
        playlist.append(segment(5, "https://intro.example/opening/2.ts"));
        playlist.append("#EXT-X-DISCONTINUITY\n");
        for (int i = 0; i < 20; i++) playlist.append(segment(5, "https://main.example/movie/" + i + ".ts"));
        playlist.append("#EXT-X-DISCONTINUITY\n");
        playlist.append(segment(5, "https://credits.example/ending/1.ts"));
        playlist.append(segment(5, "https://credits.example/ending/2.ts"));
        playlist.append("#EXT-X-ENDLIST\n");
        assertEquals(mediaEntries(playlist.toString()), mediaEntries(filter(playlist.toString())));
    }

    @Test
    public void removesRepeatedMediaAlreadyIdentifiedByClosedCue() {
        String playlist = "#EXTM3U\n" + segment(5, "main-1.ts")
                + "#EXT-X-CUE-OUT:5\n" + segment(5, "asset-123.ts")
                + "#EXT-X-CUE-IN\n" + segment(5, "main-2.ts")
                + segment(5, "asset-123.ts") + segment(5, "main-3.ts") + "#EXT-X-ENDLIST\n";
        String filtered = filter(playlist);
        assertFalse(filtered.contains("asset-123.ts"));
        assertOnlyAdsRemoved("closed cue", playlist, filtered, "asset-123.ts");
    }

    @Test
    public void stillRemovesLeadingAndTrailingClosedAdBreaks() {
        String playlist = "#EXTM3U\n#EXT-X-CUE-OUT:10\n"
                + segment(5, "https://insert.example/opening/1.ts")
                + segment(5, "https://insert.example/opening/2.ts")
                + "#EXT-X-CUE-IN\n#EXT-X-DISCONTINUITY\n"
                + segment(5, "main-1.ts") + segment(5, "main-2.ts")
                + "#EXT-X-CUE-OUT:10\n"
                + segment(5, "https://insert.example/closing/1.ts")
                + segment(5, "https://insert.example/closing/2.ts")
                + "#EXT-X-CUE-IN\n#EXT-X-ENDLIST\n";
        String filtered = filter(playlist);
        assertFalse(filtered.contains("insert.example"));
        assertTrue(filtered.contains("main-1.ts"));
        assertTrue(filtered.contains("main-2.ts"));
        assertOnlyAdsRemoved("closed edge breaks", playlist, filtered, "https://insert.example/");
    }

    @Test
    public void frameVerifiedCasinoFilterPreservesEveryOtherMediaUriInOrder() throws IOException {
        String original = readRealFixture("chimi-18.m3u8");
        String filtered = filter(original, "https://bf.modujx11.com/20260630/JbD1RgRr/index.m3u8");
        assertOnlyAdsRemoved("frame-verified casino", original, filtered, "/20260727/wRbpF6Qd/");
    }

    @Test
    public void removesFrameVerifiedCasinoPodsWithRotatingFilenames() throws IOException {
        String original = readFixture("/m3u8/research/ffzy-20260926.m3u8");
        String filtered = filter(original, "https://vip.ffzy-play7.com/20221105/1676_5181746d/2000k/hls/mixed.m3u8");
        assertOnlyAdsRemoved("frame-verified rotating names", original, filtered,
                readFixture("/m3u8/research/ffzy-20260926-ads.txt").trim().split("\n"));
    }

    @Test
    public void preservesCapturedDenseProviderPlaylist() throws IOException {
        String original = readFixture("/m3u8/research/ffzy-dense-20260926.m3u8");
        assertEquals(original.trim(), filter(original,
                "https://vip.ffzy-online5.com/20230422/10921_214e81bf/3000k/hls/mixed.m3u8").trim());
    }

    @Test
    public void casinoDurationProfileWorksAcrossSourcesButRequiresVod() throws IOException {
        String original = readFixture("/m3u8/research/ffzy-20260926.m3u8");
        String[] ads = readFixture("/m3u8/research/ffzy-20260926-ads.txt").trim().split("\n");
        for (String base : new String[]{"https://video.example/movie/index.m3u8",
                "https://another.example/unrelated/path/playlist.m3u8"}) {
            assertOnlyAdsRemoved(base, original, filter(original, base), ads);
        }
        String live = original.replace("#EXT-X-ENDLIST", "");
        assertEquals(live.trim(), filter(live,
                "https://vip.ffzy-play7.com/20221105/1676_5181746d/2000k/hls/mixed.m3u8").trim());
    }

    @Test
    public void casinoDurationProfileAllowsDifferentHostsDirectoriesAndFilenames() throws IOException {
        String original = readFixture("/m3u8/research/ffzy-20260926.m3u8");
        java.util.Set<String> ads = new java.util.HashSet<>(java.util.Arrays.asList(
                readFixture("/m3u8/research/ffzy-20260926-ads.txt").split("\n")));
        StringBuilder changed = new StringBuilder();
        List<String> renamedAds = new ArrayList<>();
        int index = 0;
        for (String line : original.split("\n")) {
            if (!line.isEmpty() && !line.startsWith("#")) {
                String uri = "https://cdn" + index % 3 + ".example/resource/" + index
                        + "/clip.jpeg?token=" + index++;
                if (ads.contains(line)) renamedAds.add(uri);
                line = uri;
            }
            changed.append(line).append('\n');
        }
        String filtered = filter(changed.toString());
        assertOnlyAdsRemoved("renamed cross-CDN media", changed.toString(), filtered, renamedAds.toArray(new String[0]));
    }

    @Test
    public void casinoDurationProfileRequiresWholeSequenceAndLongNeighbors() throws IOException {
        String original = readFixture("/m3u8/research/ffzy-20260926.m3u8");
        String base = "https://vip.ffzy-play7.com/20221105/1676_5181746d/2000k/hls/mixed.m3u8";
        String different = original.replace("#EXTINF:4.866667,", "#EXTINF:4.966667,")
                .replace("#EXTINF:6.960000,", "#EXTINF:6.860000,");
        assertEquals(different.trim(), filter(different, base).trim());
        String noRepeatedProfile = original.replaceFirst("#EXTINF:4\\.866667,", "#EXTINF:4.966667,")
                .replaceFirst("#EXTINF:6\\.960000,", "#EXTINF:6.860000,");
        assertEquals(noRepeatedProfile.trim(), filter(noRepeatedProfile, base).trim());
        String[] groups = original.split("#EXT-X-DISCONTINUITY\n");
        // Keep both matching signatures, but remove the long main-content intervals.
        String dense = "#EXTM3U\n#EXT-X-DISCONTINUITY\n" + groups[2]
                + "#EXT-X-DISCONTINUITY\n" + groups[4]
                + "#EXT-X-DISCONTINUITY\n" + groups[6]
                + "#EXT-X-DISCONTINUITY\n" + groups[8] + "#EXT-X-ENDLIST\n";
        assertEquals(dense.trim(), filter(dense, base).trim());
    }

    // Expected removals are independently labelled resource paths/URIs, never
    // inferred by calling the filter or copying its current output.
    private static void assertOnlyAdsRemoved(String label, String original, String filtered, String... adUris) {
        List<Map.Entry<String, String>> input = mediaEntries(original);
        assertTrue(label + " invalid input header", original.startsWith("#EXTM3U"));
        assertFalse(label + " empty input", input.isEmpty());
        for (String adUri : adUris) {
            assertTrue(label + " missing labelled ad: " + adUri,
                    input.stream().anyMatch(entry -> entry.getKey().contains(adUri)));
        }
        List<Map.Entry<String, String>> expected = new ArrayList<>();
        for (Map.Entry<String, String> entry : input) {
            boolean ad = false;
            for (String adUri : adUris) ad |= entry.getKey().contains(adUri);
            if (!ad) expected.add(entry);
        }
        assertFalse(label + " no retained content", expected.isEmpty());
        assertEquals(label + " retained media content/order", expected, mediaEntries(filtered));
        assertTrue(label, filtered.startsWith("#EXTM3U"));
        if (original.contains("#EXT-X-ENDLIST")) assertTrue(label, filtered.contains("#EXT-X-ENDLIST"));
    }

    // Include each media URI, duration/range, active KEY/MAP (or complete PART tag).
    // Comparing ordered entries also detects replacement, reordering and lost duplicates.
    private static List<Map.Entry<String, String>> mediaEntries(String playlist) {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        String key = "#EXT-X-KEY:METHOD=NONE";
        String map = "";
        for (String raw : playlist.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXT-X-KEY:")) {
                key = line;
            } else if (line.startsWith("#EXT-X-MAP:")) {
                map = line;
            } else if (line.startsWith("#EXTINF:") || line.startsWith("#EXT-X-BYTERANGE:")
                    || line.startsWith("#EXT-X-STREAM-INF:")) {
                pending.append(line).append('\n');
            } else if (line.startsWith("#EXT-X-PART:")) {
                java.util.regex.Matcher uri = java.util.regex.Pattern.compile("URI=\"([^\"]+)\"").matcher(line);
                assertTrue("PART without a URI: " + line, uri.find());
                entries.add(new AbstractMap.SimpleImmutableEntry<>(uri.group(1), key + '\n' + map + '\n' + line));
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                entries.add(new AbstractMap.SimpleImmutableEntry<>(line, key + '\n' + map + '\n' + pending + line));
                pending.setLength(0);
            }
        }
        assertEquals("Media declaration without a URI", "", pending.toString());
        return entries;
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
        return readFixture(path);
    }

    private static String readLiveFixture(String name) throws IOException {
        String path = "/m3u8/live/" + name;
        return readFixture(path);
    }

    private static String readTvBoxFixture(String name) throws IOException {
        String path = "/m3u8/tvbox/" + name;
        return readFixture(path);
    }

    private static String readFixture(String path) throws IOException {
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
        private final String baseUrl;
        private final String[] adUris;

        private RealFixture(String name, String baseUrl, String... adUris) {
            this.name = name;
            this.baseUrl = baseUrl == null ? "https://fixture.invalid/" + name : baseUrl;
            this.adUris = adUris;
        }
    }
}
