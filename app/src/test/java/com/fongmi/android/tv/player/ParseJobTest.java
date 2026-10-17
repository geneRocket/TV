package com.fongmi.android.tv.player;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonParser;
import okhttp3.Headers;

import static org.junit.Assert.assertEquals;

public class ParseJobTest {
    @Test public void parsedAuthorizationReplacesDefaultsWithDifferentCaseOnTheWire() {
        Map<String, String> configured = new LinkedHashMap<>();
        configured.put("Authorization", "Bearer default");
        configured.put("X-Token", "default-token");
        Map<String, String> source = new LinkedHashMap<>();
        source.put("authorization", "Bearer resolved");
        source.put("x-token", "resolved-token");
        Headers headers = Headers.of(ParseJob.mergeHeaders(configured, source));
        assertEquals(1, headers.values("Authorization").size());
        assertEquals("Bearer resolved", headers.get("Authorization"));
        assertEquals(1, headers.values("X-Token").size());
        assertEquals("resolved-token", headers.get("X-Token"));
        assertEquals("Bearer default", configured.get("Authorization"));
    }

    @Test public void nestedResponseHeadersOverrideRootAndConfiguredHeadersIgnoringCase() {
        Map<String, String> configured = new LinkedHashMap<>();
        configured.put("Authorization", "Bearer default");
        configured.put("X-Retained", "default-value");
        Headers headers = Headers.of(ParseJob.responseHeaders(JsonParser.parseString(
                "{\"header\":{\"authorization\":\"Bearer root\"},"
                + "\"data\":{\"header\":{\"AUTHORIZATION\":\"Bearer nested\"}}}").getAsJsonObject(), configured));
        assertEquals(1, headers.values("Authorization").size());
        assertEquals("Bearer nested", headers.get("Authorization"));
        assertEquals("default-value", headers.get("X-Retained"));
    }

    @Test public void topLevelUserAgentStillOverridesNestedHeaderMetadata() {
        Map<String, String> configured = new LinkedHashMap<>();
        configured.put("User-Agent", "DefaultAgent");
        Headers headers = Headers.of(ParseJob.responseHeaders(JsonParser.parseString(
                "{\"header\":{\"user-agent\":\"RootAgent\"},"
                + "\"data\":{\"header\":{\"User-Agent\":\"NestedAgent\"}},"
                + "\"USER-AGENT\":\"FinalAgent\"}").getAsJsonObject(), configured));
        assertEquals(1, headers.values("User-Agent").size());
        assertEquals("FinalAgent", headers.get("User-Agent"));
    }

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
