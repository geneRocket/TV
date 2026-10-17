package com.fongmi.android.tv.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class LiveConfigPrecedenceTest {
    @Test public void duplicateNamesKeepTheFirstCompleteSource() {
        JsonObject config = json("{\"lives\":["
                + "{\"name\":\"TV\",\"url\":\"first.m3u\",\"ua\":\"first-agent\"},"
                + "{\"name\":\"TV\",\"url\":\"second.m3u\",\"ua\":\"second-agent\"}]}" );
        List<JsonElement> entries = LiveConfig.liveEntries(config);
        assertEquals(1, entries.size());
        assertSame(config.getAsJsonArray("lives").get(0), entries.get(0));
    }

    @Test public void delayedHigherPriorityConfigKeepsItsPositionWhenMergedAgain() {
        JsonObject merged = json("{\"lives\":["
                + "{\"name\":\"Shared\",\"url\":\"priority.m3u\"},"
                + "{\"name\":\"Priority only\",\"url\":\"only.m3u\"},"
                + "{\"name\":\"Shared\",\"url\":\"cached.m3u\"},"
                + "{\"name\":\"Cached only\",\"url\":\"cached-only.m3u\"}]}" );
        List<JsonElement> entries = LiveConfig.liveEntries(merged);
        assertEquals(3, entries.size());
        assertEquals("priority.m3u", entries.get(0).getAsJsonObject().get("url").getAsString());
        assertEquals("Priority only", entries.get(1).getAsJsonObject().get("name").getAsString());
        assertEquals("Cached only", entries.get(2).getAsJsonObject().get("name").getAsString());
    }

    @Test public void differentNamesCanShareTheSamePlaylist() {
        JsonObject config = json("{\"lives\":[{\"name\":\"A\",\"url\":\"tv.m3u\"},"
                + "{\"name\":\"B\",\"url\":\"tv.m3u\"}]}" );
        assertEquals(2, LiveConfig.liveEntries(config).size());
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
