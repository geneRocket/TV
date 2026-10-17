package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Rule;
import com.google.gson.Gson;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class SnifferTest {
    private static Rule rule(String json) { return new Gson().fromJson(json, Rule.class); }

    @Test public void compiledPatternsDoNotLeakIntoSerializedConfiguration() {
        Rule rule = rule("{\"regex\":[\"video\"],\"exclude\":[\"advert\"]}");
        rule.getRegexPatterns();
        rule.getExcludePatterns();
        String json = new Gson().toJson(rule);
        assertFalse(json.contains("regexPatterns"));
        assertFalse(json.contains("excludePatterns"));
        Rule restored = rule(json);
        assertEquals("video", restored.getRegexPatterns().get(0).pattern());
        assertEquals("advert", restored.getExcludePatterns().get(0).pattern());
    }

    @Test public void exclusionsTakePriorityOverVideoExtensionsAndCustomMatches() {
        Rule rule = rule("{\"exclude\":[\"/advert/\",\"[?]preview=1\"],\"regex\":[\".mp4\"]}");
        assertFalse(Sniffer.isVideoFormat("https://cdn.example/advert/video.mp4", rule));
        assertFalse(Sniffer.isVideoFormat("https://cdn.example/video.mp4?preview=1", rule));
        assertTrue(Sniffer.isVideoFormat("https://cdn.example/video.mp4", rule));
    }

    @Test public void embeddedMediaUrlsDoNotMakeAParserPagePlayable() {
        assertFalse(Sniffer.isVideoFormat("https://parser.example/?url=https://cdn.example/video.mp4", Rule.empty()));
        assertTrue(Sniffer.isVideoFormat("https://cdn.example/video.MP4", Rule.empty()));
    }

    @Test public void hostRulesSupportSubdomainsAndRegexWithoutAsterisk() {
        Rule domain = rule("{\"hosts\":[\"example.com\"]}");
        assertSame(domain, Sniffer.findRule(Collections.singletonList(domain), "cdn.example.com", ""));
        Rule regex = rule("{\"hosts\":[\"video[0-9]+[.]example[.]com\"]}");
        assertSame(regex, Sniffer.findRule(Collections.singletonList(regex), "video12.example.com", ""));
    }

    @Test public void firstMatchingRuleKeepsConfigurationPriority() {
        Rule first = rule("{\"hosts\":[\"example.com\"]}");
        Rule second = rule("{\"hosts\":[\"cdn.example.com\"]}");
        assertSame(first, Sniffer.findRule(Arrays.asList(first, second), "cdn.example.com", ""));
        assertSame(first, Sniffer.findRule(Arrays.asList(first, second), "parser.local", "cdn.example.com"));
    }
}
