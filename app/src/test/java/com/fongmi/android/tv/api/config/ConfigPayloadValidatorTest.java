package com.fongmi.android.tv.api.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.fail;

public class ConfigPayloadValidatorTest {
    private JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
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
}
