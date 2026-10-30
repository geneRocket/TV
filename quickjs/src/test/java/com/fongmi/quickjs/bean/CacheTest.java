package com.fongmi.quickjs.bean;

import org.jsoup.nodes.Document;
import org.junit.Test;

import static org.junit.Assert.*;

public class CacheTest {
    @Test public void arrayAndFieldExtractionShareDocument() {
        Cache cache = new Cache();
        String html = "<a href='/one'>One</a>";
        Document document = cache.getPdfa(html);
        assertSame(document, cache.getPdfh(new String(html)));
        assertEquals("One", document.select("a").text());
    }

    @Test public void fieldAndArrayExtractionShareDocument() {
        Cache cache = new Cache();
        String html = "<p>One</p>";
        assertSame(cache.getPdfh(html), cache.getPdfa(new String(html)));
    }

    @Test public void extractingFragmentsRetainsFullPageCache() {
        Cache cache = new Cache();
        String html = "<main><a>One</a><a>Two</a></main>";
        Document page = cache.getPdfa(html);
        cache.getPdfh(html);
        cache.getPdfh("<a>One</a>");
        cache.getPdfh("<a>Two</a>");
        assertSame(page, cache.getPdfa(html));
        assertSame(page, cache.getPdfh(html));
        assertEquals(2, page.select("a").size());
    }

    @Test public void changedHtmlInvalidatesTheMatchingSlot() {
        Cache cache = new Cache();
        Document old = cache.getPdfh("<p>Old</p>");
        Document updated = cache.getPdfh("<p>New</p>");
        assertNotSame(old, updated);
        assertEquals("New", updated.text());
        assertSame(updated, cache.getPdfa("<p>New</p>"));
    }
}
