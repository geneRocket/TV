package com.fongmi.android.tv.utils;

import org.junit.Test;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

public class AdBlockerTest {
    @Test public void normalPlaybackParametersAreNotAdResources() {
        assertFalse(AdBlocker.hasDefaultAdSignal("https://cdn.example/movie.m3u8?ad=0"));
        assertFalse(AdBlocker.hasDefaultAdSignal("https://cdn.example/movie.m3u8?ads=false"));
        assertFalse(AdBlocker.hasDefaultAdSignal("https://cdn.example/movie.m3u8?token=opaque-ad-signature"));
        assertFalse(AdBlocker.hasDefaultAdSignal("https://cdn.example/movie.m3u8?title=advertisement"));
        assertFalse(AdBlocker.hasDefaultAdSignal("https://cdn.example/movie.m3u8#label=ad"));
        assertFalse(AdBlocker.hasDefaultAdSignal("https://cdn.example/movie.m3u8#?ad=1"));
    }

    @Test public void explicitAndEncodedAdResourcesAreStillBlocked() {
        assertTrue(AdBlocker.hasDefaultAdSignal("https://cdn.example/ads/clip.m3u8"));
        assertTrue(AdBlocker.hasDefaultAdSignal("https://cdn.example/%61ds/clip.m3u8"));
        assertTrue(AdBlocker.hasDefaultAdSignal("https://cdn.example/clip.m3u8?ad=1"));
        assertTrue(AdBlocker.hasDefaultAdSignal("https://cdn.example/clip.m3u8?placement=pre%72oll"));
    }

    @Test public void nagasawaChangzeAdResourcesAreBlocked() {
        assertTrue(AdBlocker.hasDefaultAdSignal("https://cdn.example/changze/clip.m3u8"));
        assertTrue(AdBlocker.hasDefaultAdSignal("https://cdn.example/czzy/clip.m3u8"));
        assertTrue(AdBlocker.hasDefaultAdSignal("https://cdn.example/czad/clip.m3u8"));
        assertTrue(AdBlocker.hasDefaultAdSignal("https://cdn.example/clip.m3u8?czad=1"));
    }

    @Test public void configuredHostRegexRetainsUppercaseCharacterEscapes() throws Exception {
        Pattern pattern = configured("regex:^cdn\\D+\\.example\\.com$", true);
        assertTrue(pattern.matcher("cdnads.example.com").matches());
        assertFalse(pattern.matcher("cdn123.example.com").matches());
    }

    @Test public void configuredUrlRegexRetainsNonWhitespaceEscape() throws Exception {
        Pattern pattern = configured("regex:^https://cdn\\.example/movie\\?token=\\S+$", false);
        assertTrue(pattern.matcher("https://cdn.example/movie?token=abc123").matches());
        assertFalse(pattern.matcher("https://cdn.example/movie?token= ").matches());
    }

    @Test public void configuredRegexRetainsUnicodePropertyNames() throws Exception {
        Pattern pattern = configured("regex:^\\p{Lu}+\\.example\\.com$", true);
        assertTrue(pattern.matcher("ABC.example.com").matches());
    }

    @Test public void regexPrefixAndLiteralMatchingRemainCaseInsensitive() throws Exception {
        Pattern pattern = configured("  ReGeX:^Ads\\d+\\.Example\\.Com$  ", true);
        assertTrue(pattern.matcher("ads123.example.com").matches());
        assertFalse(pattern.matcher("adsabc.example.com").matches());
    }

    @Test public void malformedConfiguredRegexKeepsItsLiteralFallback() throws Exception {
        assertTrue(configured("regex:[", true).matcher("[").matches());
    }

    @SuppressWarnings("unchecked")
    private Pattern configured(String rule, boolean hostOnly) throws Exception {
        Method method = AdBlocker.class.getDeclaredMethod("compilePatterns", List.class, boolean.class);
        method.setAccessible(true);
        List<Pattern> patterns = (List<Pattern>) method.invoke(null, Arrays.asList(null, "", rule), hostOnly);
        assertEquals(1, patterns.size());
        return patterns.get(0);
    }
}
