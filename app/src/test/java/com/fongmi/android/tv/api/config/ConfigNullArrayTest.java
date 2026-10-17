package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.Config;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class ConfigNullArrayTest {
    @Test public void nullableStringListsReadAsAbsent() {
        JsonObject source = json("{\"sites\":[],\"flags\":null,\"ads\":null,\"hosts\":null}");
        ConfigPayloadValidator.vod(source);
        for (String key : new String[]{"flags", "ads", "hosts"}) assertTrue(Json.safeListString(source, key).isEmpty());
    }

    @Test public void nullableBeanListsReadAsAbsent() {
        JsonObject source = json("{\"sites\":null,\"lives\":null,\"parses\":null,\"rules\":[]}");
        ConfigPayloadValidator.vod(source);
        for (String key : new String[]{"sites", "lives", "parses"}) assertTrue(Json.safeListElement(source, key).isEmpty());
    }

    @Test public void vodMergeKeepsExistingSitesWhenOptionalArraysAreNull() throws Exception {
        JsonObject source = json("{\"sites\":[],\"lives\":null,"
                + "\"parses\":null,\"rules\":null,\"flags\":null,\"ads\":null,\"headers\":null,\"proxy\":null,\"hosts\":null,\"doh\":null}");
        JsonObject before = source.deepCopy();
        ConfigPayloadValidator.vod(source);
        JsonObject target = json("{\"sites\":[{\"key\":\"one\",\"api\":\"csp_One\"}]}");
        mergeVod(target, source);
        assertEquals(1, target.getAsJsonArray("sites").size());
        assertEquals("csp_One", target.getAsJsonArray("sites").get(0).getAsJsonObject().get("api").getAsString());
        assertFalse("Null lives must not request clearing the live configuration", target.has("lives"));
        assertEquals(before, source);
    }

    @Test public void liveMergeKeepsEarlierEntriesWhenLaterArraysAreNull() throws Exception {
        Method merge = LiveConfig.class.getDeclaredMethod("mergeConfig", JsonObject.class, JsonObject.class);
        merge.setAccessible(true);
        LiveConfig owner = new LiveConfig();
        JsonObject target = new JsonObject();
        merge.invoke(owner, target, json("{\"lives\":[{\"name\":\"TV\"}],\"rules\":[{\"name\":\"Rule\"}],\"ads\":[\"ad.example\"]}"));
        JsonObject expected = target.deepCopy();
        JsonObject source = json("{\"lives\":null,\"rules\":null,\"ads\":null,\"hosts\":null,\"headers\":null,\"proxy\":null}");
        merge.invoke(owner, target, source);
        assertEquals(expected, target);
    }

    @Test public void nullSiteListDoesNotDiscardValidLiveSources() throws Exception {
        JsonObject source = json("{\"sites\":null,\"lives\":[{\"name\":\"TV\",\"url\":\"tv.m3u\"}]}");
        ConfigPayloadValidator.vod(source);
        JsonObject target = new JsonObject();
        mergeVod(target, source);
        assertTrue(target.getAsJsonArray("sites").isEmpty());
        assertEquals(1, target.getAsJsonArray("lives").size());
    }

    @Test public void explicitEmptyLiveListStillRequestsLiveReplacement() {
        JsonObject target = new JsonObject();
        LiveConfig.mergeLives(target, json("{\"lives\":[]}"));
        assertTrue(target.has("lives"));
        assertTrue(target.getAsJsonArray("lives").isEmpty());
    }

    private void mergeVod(JsonObject target, JsonObject source) throws Exception {
        Method merge = VodConfig.class.getDeclaredMethod("mergeConfig", JsonObject.class, JsonObject.class, Config.class);
        merge.setAccessible(true);
        merge.invoke(new VodConfig(), target, source, new Config());
    }

    private JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
