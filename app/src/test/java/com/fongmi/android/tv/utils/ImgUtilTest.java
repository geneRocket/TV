package com.fongmi.android.tv.utils;

import org.junit.Test;
import java.util.Map;
import okhttp3.Headers;

import static org.junit.Assert.assertEquals;

public class ImgUtilTest {
    @Test public void coordinateCommasBeforeAProxyTargetAreNotImageSeparators() {
        String image = "https://images.example.com/resize?size=300,450&url=https://cdn.example.com/poster.jpg";
        assertEquals(image, ImgUtil.firstPic(image));
    }

    @Test public void coordinateCommasInAProxyPathAreNotImageSeparators() {
        String image = "https://images.example.com/resize/300,450/https://cdn.example.com/poster.jpg";
        assertEquals(image, ImgUtil.firstPic(image));
    }

    @Test public void imageListsPreserveCommasInsideTheirFirstUrl() {
        String image = "https://images.example.com/resize?size=300,450&url=https://cdn.example.com/poster.jpg";
        assertEquals(image, ImgUtil.firstPic(image + ", https://cdn.example.com/second.jpg"));
    }

    @Test public void imageSpecificCookieReplacesTheSiteCookie() {
        Headers site = new Headers.Builder().add("Cookie", "site=old").add("X-Site", "retained").build();
        Map<String, String> headers = ImgUtil.imageHeaders("https://example.com/poster.jpg@Cookie=image=new", site);
        assertEquals("image=new", headers.get("Cookie"));
        assertEquals("retained", headers.get("X-Site"));
        assertEquals("site=old", site.get("Cookie"));
    }

    @Test public void individualTagsOverrideJsonWhichOverridesSiteHeadersIgnoringCase() {
        Headers site = new Headers.Builder().add("cookie", "site-cookie").add("referer", "site-referrer")
                .add("user-agent", "SiteAgent").add("Authorization", "Bearer site").build();
        Map<String, String> headers = ImgUtil.imageHeaders("https://example.com/poster.jpg"
                + "@Headers={\"COOKIE\":\"json-cookie\",\"REFERER\":\"json-referrer\",\"USER-AGENT\":\"JsonAgent\",\"authorization\":\"Bearer image\"}"
                + "@Cookie=final-cookie@Referer=https://example.com/detail@User-Agent=ImageAgent", site);
        assertEquals(4, headers.size());
        assertEquals("final-cookie", headers.get("Cookie"));
        assertEquals("https://example.com/detail", headers.get("Referer"));
        assertEquals("ImageAgent", headers.get("User-Agent"));
        assertEquals("Bearer image", headers.get("Authorization"));
    }

    @Test public void plainImageRetainsAllSiteHeaders() {
        Headers site = new Headers.Builder().add("User-Agent", "SiteAgent").add("Cookie", "site-cookie").build();
        Map<String, String> headers = ImgUtil.imageHeaders("https://example.com/poster.jpg", site);
        assertEquals(2, headers.size());
        assertEquals("SiteAgent", headers.get("User-Agent"));
        assertEquals("site-cookie", headers.get("Cookie"));
        assertEquals(0, ImgUtil.imageHeaders("https://example.com/poster.jpg", null).size());
    }

    @Test public void malformedJsonHeadersDoNotDiscardTheOtherHeaderSources() {
        Headers site = new Headers.Builder().add("X-Site", "retained").build();
        Map<String, String> headers = ImgUtil.imageHeaders("https://example.com/poster.jpg@Headers={broken@Cookie=image-cookie", site);
        assertEquals("retained", headers.get("X-Site"));
        assertEquals("image-cookie", headers.get("Cookie"));
    }
    @Test
    public void headerMetadataIsNotMistakenForAnImageList() {
        String image = "https://example.com/poster.jpg@Headers={\"Cookie\":\"token=a&&b\",\"Referer\":\"https://example.com/detail\"}";
        assertEquals(image, ImgUtil.firstPic(image));
        String cookie = "https://example.com/poster.jpg@Cookie=token=a&&b";
        assertEquals(cookie, ImgUtil.firstPic(cookie));
        String referer = "https://example.com/poster.jpg?crop=1,2@Referer=https://example.com/detail";
        assertEquals(referer, ImgUtil.firstPic(referer));
        assertEquals(image, ImgUtil.firstPic(image + "$$$https://example.com/second.jpg"));
    }

    @Test
    public void imageListsStillSelectTheFirstImage() {
        String first = "https://example.com/first.jpg";
        for (String separator : new String[]{"$$$", "&&", "\n", "\r", "\t", ","}) {
            assertEquals(first, ImgUtil.firstPic(first + separator + "https://example.com/second.jpg"));
        }
    }
}
