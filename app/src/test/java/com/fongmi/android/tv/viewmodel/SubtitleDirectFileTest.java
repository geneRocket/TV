package com.fongmi.android.tv.viewmodel;

import com.fongmi.android.tv.bean.Subtitle;
import org.jsoup.nodes.Document;
import org.junit.Test;
import static org.junit.Assert.*;

public class SubtitleDirectFileTest {
    private static final String PAGE = "https://assrt.net/sub/detail/123";

    @Test public void signedSubtitleUrlRetainsItsDownloadParameters() throws Exception {
        Subtitle subtitle = parse("https://cdn.example/movie.SRT?token=abc#download");
        assertNotNull(subtitle); assertEquals("movie.SRT", subtitle.getName());
        assertEquals("https://cdn.example/movie.SRT?token=abc#download", subtitle.getUrl());
    }

    @Test public void additionalPlayerSupportedFormatsAreRecognized() throws Exception {
        for (String extension : new String[]{"vtt", "ssa", "xml", "dfxp"}) {
            assertNotNull(extension, parse("https://cdn.example/movie." + extension));
        }
    }

    @Test public void relativeDownloadUsesTheResponseDirectory() throws Exception {
        Subtitle subtitle = parse("../files/movie.srt");
        assertNotNull(subtitle);
        assertEquals("https://assrt.net/sub/files/movie.srt", subtitle.getUrl());
    }

    @Test public void literalPlusInAFileNameDoesNotBecomeASpace() throws Exception {
        Subtitle subtitle = parse("https://cdn.example/C++.srt");
        assertNotNull(subtitle); assertEquals("C++.srt", subtitle.getName());
    }

    @Test public void escapedUnicodeFileNameRemainsReadable() throws Exception {
        Subtitle subtitle = parse("https://cdn.example/%E4%B8%AD%E6%96%87.ass");
        assertNotNull(subtitle); assertEquals("中文.ass", subtitle.getName());
    }

    @Test public void legacyQueryBasedDownloadEndpointsRemainAccepted() throws Exception {
        Subtitle subtitle = parse("https://cdn.example/download?format=srt");
        assertNotNull(subtitle); assertEquals("https://cdn.example/download?format=srt", subtitle.getUrl());
    }

    @Test public void archivesAndMissingDownloadButtonsAreNotDirectSubtitles() throws Exception {
        assertNull(parse("https://cdn.example/movie.zip"));
        assertNull(SubtitleViewModel.parseDirectSubtitle(Document.createShell(""), PAGE));
    }

    private Subtitle parse(String href) throws Exception {
        Document document = Document.createShell("");
        document.body().appendElement("div").addClass("download").appendElement("a")
                .attr("id", "btn_download").attr("href", href);
        return SubtitleViewModel.parseDirectSubtitle(document, PAGE);
    }
}
