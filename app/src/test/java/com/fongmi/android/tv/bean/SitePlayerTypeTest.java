package com.fongmi.android.tv.bean;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class SitePlayerTypeTest {
    private final Gson gson = new Gson();

    @Test public void apiPlayerTypeIsIgnoredAndNotSerialized() {
        for (int player : new int[]{0, 1, 2, 10}) {
            Site site = gson.fromJson("{\"playerType\":" + player + "}", Site.class);
            com.google.gson.JsonObject stored = gson.toJsonTree(site).getAsJsonObject();
            assertFalse(stored.has("playerType"));
        }
    }

    @Test public void malformedIgnoredPlayerTypeDoesNotBreakOtherSiteFields() {
        for (String value : new String[]{"null", "\"invalid\"", "{}", "[]"}) {
            Site site = gson.fromJson("{\"key\":\"site\",\"playerType\":" + value + "}", Site.class);
            com.google.gson.JsonObject stored = gson.toJsonTree(site).getAsJsonObject();
            assertEquals("site", stored.get("key").getAsString());
            assertFalse(stored.has("playerType"));
        }
    }
}
