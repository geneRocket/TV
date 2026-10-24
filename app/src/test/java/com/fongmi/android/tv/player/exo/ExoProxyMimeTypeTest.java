package com.fongmi.android.tv.player.exo;

import androidx.media3.common.MimeTypes;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ExoProxyMimeTypeTest {
    @Test public void localProxyDeclarationSelectsHlsBeforeProgressiveExtractors() {
        assertEquals(MimeTypes.APPLICATION_M3U8, ExoUtil.resolveMimeType(null,
                "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fvideo.example%2Findex.m3u8"));
    }

    @Test public void localProxySupportsAlternatePortAndEncodedParameter() {
        assertEquals(MimeTypes.APPLICATION_M3U8, ExoUtil.resolveMimeType("",
                "http://localhost:9979/proxy?url=opaque&%64o=m3u8"));
        assertEquals(MimeTypes.APPLICATION_M3U8, ExoUtil.resolveMimeType(null,
                "http://[::1]:9980/proxy?do=m3u8"));
    }

    @Test public void explicitFormatTakesPriorityOverProxyDeclaration() {
        assertEquals(MimeTypes.VIDEO_MP4, ExoUtil.resolveMimeType(MimeTypes.VIDEO_MP4,
                "http://127.0.0.1:9978/proxy?do=m3u8"));
    }

    @Test public void unrelatedUrlsDoNotAcquireHlsFromQueryText() {
        for (String url : new String[]{
                "https://cdn.example/proxy?do=m3u8",
                "http://127.0.0.1:9978/video?do=m3u8",
                "http://127.0.0.1:9978/proxy?do=mp4",
                "http://127.0.0.1:9978/proxy?do=py&url=do%3Dm3u8",
                "http://127.0.0.1:9978/proxy?url=movie.m3u8",
                "not a valid URI"}) {
            assertNull(url, ExoUtil.resolveMimeType(null, url));
        }
    }
}
