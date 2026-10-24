package com.fongmi.android.tv.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteContentValidatorTest {
    @Test public void commentedServerErrorPagesCannotBecomeWorkingConfigOrScriptCaches() {
        for (String prefix : new String[]{"<!-- maintenance -->\n", "<!-- cdn -->\n<!-- trace --> ",
                "\uFEFF <!-- notice -->\n", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"}) {
            for (String html : new String[]{"<!DOCTYPE html><html>Unavailable</html>", "<HTML>Forbidden</HTML>"}) {
                assertTrue(prefix, RemoteContentValidator.isHtml(prefix + html));
            }
        }
    }

    @Test public void leadingCommentsDoNotTurnRealResourcesIntoHtmlErrors() {
        for (String text : new String[]{"<!-- notice -->\nconst value = '<html>text</html>';",
                "<?xml version=\"1.0\"?><tv><channel id=\"one\"/></tv>",
                "<!-- incomplete comment containing <html>", "// <html>sample</html>\nconst api = {};"}) {
            assertFalse(text, RemoteContentValidator.isHtml(text));
        }
    }
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
