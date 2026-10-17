package com.fongmi.android.tv.utils;

import org.junit.Test;
import com.fongmi.android.tv.bean.Vod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchSorterTest {

    @Test
    public void matchesTitle_allowsOnlyMetadataDifferences() {
        assertTrue(SearchSorter.matchesTitle("群体", "群体"));
        assertTrue(SearchSorter.matchesTitle("群体 (2026)【1080P】", "群体"));
        assertTrue(SearchSorter.matchesTitle("流浪地球.(2019).2160p.UHD", "流浪地球"));
        assertTrue(SearchSorter.matchesTitle("流浪地球 4K", "流浪地球"));
    }

    @Test
    public void matchesTitle_rejectsDifferentWorksAndSequels() {
        assertFalse(SearchSorter.matchesTitle("群体恶行", "群体"));
        assertFalse(SearchSorter.matchesTitle("少年骇客：异形群体", "群体"));
        assertFalse(SearchSorter.matchesTitle("流浪地球2", "流浪地球"));
        assertFalse(SearchSorter.matchesTitle("流浪地球 II", "流浪地球"));
        assertFalse(SearchSorter.matchesTitle("Blade Runner", "Blade Runner 2049"));
        assertFalse(SearchSorter.matchesTitle("", "群体"));
        assertFalse(SearchSorter.matchesTitle(null, "群体"));
    }

    @Test
    public void key_usesSiteAndIdOrNormalizedName() {
        assertEquals("site@123", SearchSorter.key("site", "123", "影片名"));
        assertEquals("site@123", SearchSorter.key(" site ", " 123 ", "影片名"));
        assertEquals("site@影 片 名", SearchSorter.key("site", "", " 影 片 名 "));
        assertEquals("", SearchSorter.key("", "123", "影片名"));
        assertEquals("", SearchSorter.key("site", "   ", ""));
    }

    @Test public void automaticSourceSelectionRejectsSequels() {
        assertFalse(SearchSorter.isSourceCandidate(vod("流浪地球2"), "流浪地球", true, .5));
        assertFalse(SearchSorter.isSourceCandidate(vod("Blade Runner 2049"), "Blade Runner", true, .5));
    }

    @Test public void automaticSourceSelectionRejectsOtherWorksWithSimilarNames() {
        assertFalse(SearchSorter.isSourceCandidate(vod("群体恶行"), "群体", true, .5));
    }

    @Test public void automaticSourceSelectionAllowsQualityAndYearMetadata() {
        assertTrue(SearchSorter.isSourceCandidate(vod("群体 (2026)【1080P】"), "群体", true, .5));
    }

    @Test public void manualSourceSearchKeepsFuzzySuggestions() {
        assertTrue(SearchSorter.isSourceCandidate(vod("流浪地球2"), "流浪地球", false, .5));
        assertTrue(SearchSorter.isSourceCandidate(vod("群体恶行"), "群体", false, .5));
        assertFalse(SearchSorter.isSourceCandidate(vod("另一部电影"), "群体", false, .5));
    }

    @Test public void automaticSourceSelectionKeepsExactTitles() {
        assertTrue(SearchSorter.isSourceCandidate(vod("群体"), "群体", true, .5));
        assertFalse(SearchSorter.isSourceCandidate(null, "群体", true, .5));
    }

    @Test public void automaticSourceSelectionKeepsNumbersAndSeasonNamesAfterQualityLabels() {
        assertFalse(SearchSorter.isSourceCandidate(vod("Blade Runner 2049 4K"), "Blade Runner", true, .5));
        assertFalse(SearchSorter.isSourceCandidate(vod("Wonder Woman 1984 1080P"), "Wonder Woman", true, .5));
        assertFalse(SearchSorter.isSourceCandidate(vod("群体 4K 第二季"), "群体", true, .5));
        assertFalse(SearchSorter.isSourceCandidate(vod("流浪地球 1080P 2"), "流浪地球", true, .5));
    }

    @Test public void ambiguousBareYearsAreNotAssumedToBeMetadata() {
        assertFalse(SearchSorter.matchesTitle("流浪地球.2019.2160p.UHD", "流浪地球"));
        assertTrue(SearchSorter.matchesTitle("群体 (2026)【1080P HDR】", "群体"));
        assertTrue(SearchSorter.matchesTitle("群体 4K (2026)", "群体"));
    }

    private Vod vod(String title) {
        return new Vod() {
            @Override public String getVodName() { return title; }
        };
    }
}
