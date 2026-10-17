package com.fongmi.android.tv.player;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ParseJobTest {
    @Test
    public void inlineHeadersOverrideParserDefaultsWithoutMutatingThem() {
        Map<String, String> configured = new LinkedHashMap<>();
        configured.put("referer", "https://parser.example/");
        configured.put("User-Agent", "Parser/1.0");
        Map<String, String> source = new LinkedHashMap<>();
        source.put("Referer", "https://source.example/");
        source.put("cookie", "token=abc");

        Map<String, String> merged = ParseJob.mergeHeaders(configured, source);

        assertEquals(3, merged.size());
        assertEquals("https://source.example/", merged.get("Referer"));
        assertEquals("token=abc", merged.get("Cookie"));
        assertEquals("Parser/1.0", merged.get("User-Agent"));
        assertEquals("https://parser.example/", configured.get("referer"));
    }

}
