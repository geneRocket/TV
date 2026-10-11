package com.fongmi.android.tv.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteContentValidatorTest {
    @Test public void detectsHtmlErrorPagesDespiteWhitespaceBomAndCase() {
        assertTrue(RemoteContentValidator.isHtml(" \uFEFF \n<!DOCTYPE HTML><html>Unavailable</html>"));
        assertTrue(RemoteContentValidator.isHtml("<HTML lang=\"en\">challenge</HTML>"));
        assertTrue(RemoteContentValidator.isHtml("\n<body>Forbidden</body>"));
    }

    @Test public void preservesScriptsAndResourcesThatContainHtmlAsData() {
        assertFalse(RemoteContentValidator.isHtml("const page = '<html>example</html>';"));
        assertFalse(RemoteContentValidator.isHtml("{\"template\":\"<html>example</html>\"}"));
        assertFalse(RemoteContentValidator.isHtml("#EXTM3U\n#EXTINF:-1,TV\nhttps://example.com/live.m3u8"));
        assertFalse(RemoteContentValidator.isHtml(null));
    }
}
