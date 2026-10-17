package com.fongmi.android.tv.viewmodel;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SubtitleRedirectTest {
    private static final String REQUEST = "https://assrt.net/download/123/file?old=1";

    @Test public void relativeFileUsesTheCurrentDownloadDirectory() {
        assertEquals("https://assrt.net/download/files/movie.srt",
                SubtitleViewModel.normalizeAssrtLocation(REQUEST, "../files/movie.srt"));
    }

    @Test public void queryRedirectRetainsTheDownloadPath() {
        assertEquals("https://assrt.net/download/123/file?token=abc",
                SubtitleViewModel.normalizeAssrtLocation(REQUEST, "?token=abc"));
    }

    @Test public void rootRedirectRetainsTheActualServer() {
        assertEquals("https://assrt.net/files/movie.srt",
                SubtitleViewModel.normalizeAssrtLocation(REQUEST, "/files/movie.srt"));
    }

    @Test public void absoluteAndNetworkPathTargetsKeepWorking() {
        assertEquals("https://cdn.example/movie.srt",
                SubtitleViewModel.normalizeAssrtLocation(REQUEST, "https://cdn.example/movie.srt"));
        assertEquals("https://cdn.example/movie.srt",
                SubtitleViewModel.normalizeAssrtLocation(REQUEST, "//cdn.example/movie.srt"));
    }

    @Test public void missingOrUnsupportedLocationsAreIgnored() {
        assertEquals("", SubtitleViewModel.normalizeAssrtLocation(REQUEST, null));
        assertEquals("", SubtitleViewModel.normalizeAssrtLocation(REQUEST, "   "));
        assertEquals("", SubtitleViewModel.normalizeAssrtLocation(REQUEST, "javascript:invalid"));
    }
}
