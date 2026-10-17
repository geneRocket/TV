package com.fongmi.android.tv.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ImgUtilTest {
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
