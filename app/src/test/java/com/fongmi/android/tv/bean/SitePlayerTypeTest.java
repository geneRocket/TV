package com.fongmi.android.tv.bean;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class SitePlayerTypeTest {
    private final Gson gson = new Gson();

    @Test public void legacySourcePlayerTypeSurvivesDeserializationAndSerialization() {
        for (int player = 0; player <= 2; player++) {
            Site site = gson.fromJson("{\"playerType\":" + player + "}", Site.class);
            com.google.gson.JsonObject stored = gson.toJsonTree(site).getAsJsonObject();
            assertTrue("Source playerType was discarded", stored.has("playerType"));
            assertEquals(player, stored.get("playerType").getAsInt());
        }
    }

    @Test public void unsetSourceDoesNotForceAPlayerAndLegacyLimitIsPreserved() throws Exception {
        java.lang.reflect.Method getter = Site.class.getMethod("getPlayerType");
        assertEquals(-1, getter.invoke(new Site()));
        assertEquals(2, getter.invoke(gson.fromJson("{\"playerType\":10}", Site.class)));
    }
}
