package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.Config;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class VodConfigMergeTest {
    @Test public void configWithoutLiveSectionDoesNotRequestLiveReplacement() throws Exception {
        JsonObject target = new JsonObject();
        merge(target, json("{\"spider\":\"vod.jar\",\"sites\":[]}"));
        assertFalse(target.has("lives"));
    }

    @Test public void eachLiveSpiderKeepsItsOwningConfigurationJar() throws Exception {
        JsonObject target = new JsonObject();
        merge(target, json("{\"spider\":\"first.jar\",\"sites\":[],\"lives\":[{\"name\":\"First\",\"api\":\"csp_First\"}]}"));
        merge(target, json("{\"spider\":\"second.jar\",\"sites\":[],\"lives\":[{\"name\":\"Second\",\"api\":\"csp_Second\"}]}"));
        JsonArray lives = target.getAsJsonArray("lives");
        assertEquals("first.jar", lives.get(0).getAsJsonObject().get("jar").getAsString());
        assertEquals("second.jar", lives.get(1).getAsJsonObject().get("jar").getAsString());
    }

    @Test public void explicitJarIsPreservedAndMergeDoesNotModifyCachedPayload() throws Exception {
        JsonObject source = json("{\"spider\":\"default.jar\",\"sites\":[],\"lives\":["
                + "{\"name\":\"Explicit\",\"api\":\"csp_One\",\"jar\":\"override.jar\"},"
                + "{\"name\":\"Inherited\",\"api\":\"csp_Two\"}]}" );
        JsonObject before = source.deepCopy();
        JsonObject target = new JsonObject();
        merge(target, source);
        assertEquals("override.jar", target.getAsJsonArray("lives").get(0).getAsJsonObject().get("jar").getAsString());
        assertEquals("default.jar", target.getAsJsonArray("lives").get(1).getAsJsonObject().get("jar").getAsString());
        assertEquals(before, source);
    }

    @Test public void wrappedVideoAndOuterLiveSourcesUseTheirOwnDefaults() throws Exception {
        JsonObject target = new JsonObject();
        merge(target, json("{\"spider\":\"outer.jar\",\"lives\":[{\"name\":\"Outer\",\"api\":\"csp_Outer\"}],"
                + "\"video\":{\"spider\":\"inner.jar\",\"sites\":[],\"lives\":[{\"name\":\"Inner\",\"api\":\"csp_Inner\"}]}}"));
        JsonArray lives = target.getAsJsonArray("lives");
        assertEquals("outer.jar", lives.get(0).getAsJsonObject().get("jar").getAsString());
        assertEquals("inner.jar", lives.get(1).getAsJsonObject().get("jar").getAsString());
    }

    private static void merge(JsonObject target, JsonObject source) throws Exception {
        Method method = VodConfig.class.getDeclaredMethod("mergeConfig", JsonObject.class, JsonObject.class, Config.class);
        method.setAccessible(true);
        method.invoke(new VodConfig(), target, source, new Config());
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
