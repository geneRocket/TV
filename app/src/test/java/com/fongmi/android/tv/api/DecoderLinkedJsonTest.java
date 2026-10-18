package com.fongmi.android.tv.api;

import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class DecoderLinkedJsonTest {
    private static final String JSON = "{\"spider\":\"./spider.jar\"}";

    @Test public void relativeLinkUsesPageDirectory() {
        Decoder.LinkedJson result = Decoder.getJsonFromHtml("https://page.example/start/index.html",
                "<a href=\"../configs/main.json?v=1\">config</a>",
                url -> "https://page.example/configs/main.json?v=1".equals(url) ? JSON : "");
        assertNotNull(result);
        assertEquals("https://page.example/configs/spider.jar", URI.create(result.url).resolve("./spider.jar").toString());
        assertEquals(JSON, result.data);
    }

    @Test public void absoluteLinkKeepsJsonOriginForRelativeContent() {
        Decoder.LinkedJson result = Decoder.getJsonFromHtml("https://page.example/index.html",
                "<a href=\"https://cdn.example/nested/main.json\">config</a>", url -> JSON);
        assertNotNull(result);
        assertEquals("https://cdn.example/nested/main.json", result.url);
        assertEquals("https://cdn.example/nested/spider.jar", URI.create(result.url).resolve("./spider.jar").toString());
    }

    @Test public void malformedCandidateDoesNotHideLaterObject() {
        List<String> requested = new ArrayList<>();
        Decoder.LinkedJson result = Decoder.getJsonFromHtml("https://page.example/index.html",
                "<a href=\"https://cdn.example/invalid.json\"></a><a href=\"https://cdn.example/main.json\"></a>", url -> {
                    requested.add(url);
                    return url.endsWith("invalid.json") ? "not json" : JSON;
                });
        assertNotNull(result);
        assertEquals(2, requested.size());
        assertEquals(JSON, result.data);
    }

    @Test public void pageWithoutLinksDoesNotFetch() {
        assertNull(Decoder.getJsonFromHtml("https://page.example/index.html", "<html>none</html>", url -> {
            fail("No linked JSON should be fetched");
            return "";
        }));
    }

    @Test public void rootAndProtocolRelativeLinksResolveAgainstPage() {
        for (String link : new String[]{"/nested/main.json", "//cdn.example/nested/main.json"}) {
            Decoder.LinkedJson result = Decoder.getJsonFromHtml("https://page.example/start/index.html",
                    "<a href=\"" + link + "\"></a>", url -> JSON);
            assertNotNull(result);
            String expectedHost = link.startsWith("//") ? "cdn.example" : "page.example";
            assertEquals("https://" + expectedHost + "/nested/main.json", result.url);
        }
    }
}
