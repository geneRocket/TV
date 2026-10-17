package com.fongmi.android.tv.utils;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class UrlUtilTest {
    @Test public void malformedJsonDoesNotDiscardIndependentHeaderTags() {
        Map<String, String> headers = UrlUtil.getTagHeaders("https://example.com/video.m3u8"
                + "@Headers={\"broken\":@Cookie=token=a=b@Referer=https://example.com/watch@User-Agent=Player/1.0");
        assertEquals("token=a=b", headers.get("Cookie"));
        assertEquals("https://example.com/watch", headers.get("Referer"));
        assertEquals("Player/1.0", headers.get("User-Agent"));
    }

    @Test public void explicitTagsOverrideJsonHeadersAndKeepUnrelatedValues() {
        Map<String, String> headers = UrlUtil.getTagHeaders("https://example.com/video.m3u8"
                + "@Headers={\"cookie\":\"old\",\"X-Token\":\"abc\"}@Cookie=new");
        assertEquals("new", headers.get("Cookie"));
        assertEquals("abc", headers.get("X-Token"));
        assertEquals(2, headers.size());
        assertTrue(UrlUtil.getTagHeaders(null).isEmpty());
    }
}
