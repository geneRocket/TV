package com.fongmi.android.tv.api.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.fail;

public class ConfigPayloadValidatorTest {
    private JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test public void cachedDepotRemainsUsable() {
        ConfigPayloadValidator.cachedVod(json("{\"urls\":[{\"url\":\"https://example.com/config\"}]}"));
        ConfigPayloadValidator.cachedLive(json("{\"urls\":[{\"url\":\"file:///playlist\"}]}"));
    }

    @Test public void invalidCachedPayloadsCannotBlockNetworkFallback() {
        for (String text : new String[]{"{}", "{\"urls\":[]}", "{\"urls\":[{}]}", "{\"urls\":[{\"url\":false}]}"}) {
            try {
                ConfigPayloadValidator.cachedVod(json(text));
                fail("Invalid cache accepted");
            } catch (IllegalStateException expected) { }
            try {
                ConfigPayloadValidator.cachedLive(json(text));
                fail("Invalid cache accepted");
            } catch (IllegalStateException expected) { }
        }
    }

    @Test public void acceptsSourcesAndExplicitEmptyLists() {
        ConfigPayloadValidator.vod(json("{\"sites\":[{\"key\":\"a\",\"api\":\"a.js\"}],\"ads\":null}"));
        ConfigPayloadValidator.vod(json("{\"video\":{\"sites\":[]},\"lives\":[]}"));
        ConfigPayloadValidator.live(json("{\"lives\":[]}"));
        ConfigPayloadValidator.live(json("{\"lives\":[{\"name\":\"TV\",\"url\":\"tv.m3u\"}]}"));
    }

    @Test public void rejectsErrorResponsesAndMalformedSourceLists() {
        String[] invalid = {"{}", "{\"code\":503,\"error\":\"unavailable\"}",
                "{\"msg\":\"expired\",\"sites\":[]}", "{\"sites\":\"error\"}",
                "{\"sites\":[\"error\"]}", "{\"video\":\"error\"}", "{\"sites\":null}"};
        for (String text : invalid) {
            try {
                ConfigPayloadValidator.vod(json(text));
                fail("Accepted invalid payload: " + text);
            } catch (IllegalStateException expected) { }
        }
        try {
            ConfigPayloadValidator.live(json("{\"lives\":[\"error\"]}"));
            fail("Accepted malformed live sources");
        } catch (IllegalStateException expected) { }
    }

    @Test public void rejectsMalformedRuleEntriesBeforeCaching() {
        for (String entry : new String[]{"false", "null", "\"server error\""}) {
            rejectVod("{\"sites\":[],\"rules\":[" + entry + "]}");
            rejectLive("{\"lives\":[],\"rules\":[" + entry + "]}");
        }
    }

    @Test public void rejectsMalformedDnsEntriesBeforeCaching() {
        for (String entry : new String[]{"false", "null", "\"server error\""}) {
            rejectVod("{\"sites\":[],\"doh\":[" + entry + "]}");
        }
    }

    @Test public void rejectsMalformedNetworkPolicyEntriesBeforeCaching() {
        for (String key : new String[]{"headers", "proxy"}) {
            for (String entry : new String[]{"false", "null", "\"server error\""}) {
                rejectVod("{\"sites\":[],\"" + key + "\":[" + entry + "]}");
                rejectLive("{\"lives\":[],\"" + key + "\":[" + entry + "]}");
            }
        }
    }

    @Test public void rejectsNonScalarStringListEntriesBeforeCaching() {
        for (String key : new String[]{"flags", "ads", "hosts"}) {
            for (String entry : new String[]{"{}", "[]", "null"}) {
                rejectVod("{\"sites\":[],\"" + key + "\":[" + entry + "]}");
                if (!key.equals("flags")) rejectLive("{\"lives\":[],\"" + key + "\":[" + entry + "]}");
            }
        }
    }

    @Test public void wrappedVideoDoesNotBypassOuterLiveValidation() {
        rejectVod("{\"video\":{\"sites\":[]},\"lives\":\"error\"}");
        rejectVod("{\"video\":{\"sites\":[]},\"lives\":[null]}");
    }

    @Test public void validPoliciesAndScalarListsRemainUsable() {
        String shared = "\"rules\":[{\"name\":\"proxy\",\"hosts\":[\"example.com\"]}],"
                + "\"headers\":[{\"host\":\"example.com\",\"header\":{\"User-Agent\":\"TV\"}}],"
                + "\"proxy\":[{\"name\":\"Direct\",\"hosts\":[\"example.com\"],\"urls\":[]}],"
                + "\"hosts\":[\"example.com\"],\"ads\":[\"ads.example.com\"]";
        ConfigPayloadValidator.vod(json("{\"sites\":[],\"flags\":[1,true,\"vip\"],\"doh\":[{\"name\":\"DNS\",\"url\":\"https://example.com/dns-query\"}]," + shared + "}"));
        ConfigPayloadValidator.live(json("{\"lives\":[]," + shared + "}"));
    }

    private void rejectVod(String text) {
        try {
            ConfigPayloadValidator.vod(json(text));
            fail("Accepted invalid payload: " + text);
        } catch (IllegalStateException expected) { }
    }

    private void rejectLive(String text) {
        try {
            ConfigPayloadValidator.live(json(text));
            fail("Accepted invalid payload: " + text);
        } catch (IllegalStateException expected) { }
    }
}
