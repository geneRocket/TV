package com.fongmi.android.tv.viewmodel;

import org.junit.Test;
import com.fongmi.android.tv.subtitle.AssrtSubtitleParser;
import static org.junit.Assert.assertEquals;

public class SubtitleRedirectTest {
    private static final String REQUEST = "https://assrt.net/download/123/file?old=1";

    @Test public void relativeFileUsesTheCurrentDownloadDirectory() {
        assertEquals("https://assrt.net/download/files/movie.srt",
                AssrtSubtitleParser.normalizeAssrtLocation(REQUEST, "../files/movie.srt"));
    }

    @Test public void queryRedirectRetainsTheDownloadPath() {
        assertEquals("https://assrt.net/download/123/file?token=abc",
                AssrtSubtitleParser.normalizeAssrtLocation(REQUEST, "?token=abc"));
    }

    @Test public void rootRedirectRetainsTheActualServer() {
        assertEquals("https://assrt.net/files/movie.srt",
                AssrtSubtitleParser.normalizeAssrtLocation(REQUEST, "/files/movie.srt"));
    }

    @Test public void absoluteAndNetworkPathTargetsKeepWorking() {
        assertEquals("https://cdn.example/movie.srt",
                AssrtSubtitleParser.normalizeAssrtLocation(REQUEST, "https://cdn.example/movie.srt"));
        assertEquals("https://cdn.example/movie.srt",
                AssrtSubtitleParser.normalizeAssrtLocation(REQUEST, "//cdn.example/movie.srt"));
    }

    @Test public void missingOrUnsupportedLocationsAreIgnored() {
        assertEquals("", AssrtSubtitleParser.normalizeAssrtLocation(REQUEST, null));
        assertEquals("", AssrtSubtitleParser.normalizeAssrtLocation(REQUEST, "   "));
        assertEquals("", AssrtSubtitleParser.normalizeAssrtLocation(REQUEST, "javascript:invalid"));
    }
}
