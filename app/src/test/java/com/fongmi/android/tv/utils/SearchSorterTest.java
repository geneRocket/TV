package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchSorterTest {

    @Test
    public void matchesTitle_allowsOnlyMetadataDifferences() {
        assertTrue(SearchSorter.matchesTitle("群体", "群体"));
        assertTrue(SearchSorter.matchesTitle("群体 (2026)【1080P】", "群体"));
        assertTrue(SearchSorter.matchesTitle("流浪地球.2019.2160p.UHD", "流浪地球"));
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
}
