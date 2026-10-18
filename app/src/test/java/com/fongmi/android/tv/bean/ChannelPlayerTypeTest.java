package com.fongmi.android.tv.bean;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class ChannelPlayerTypeTest {
    private final Gson gson = new Gson();

    @Test public void apiPlayerTypeIsIgnoredOnLiveSourcesAndChannels() {
        for (String value : new String[]{"0", "1", "2", "10", "null", "\"invalid\"", "{}", "[]"}) {
            String json = "{\"playerType\":" + value + "}";
            assertFalse(gson.toJsonTree(gson.fromJson(json, Live.class)).getAsJsonObject().has("playerType"));
            assertFalse(gson.toJsonTree(gson.fromJson(json, Channel.class)).getAsJsonObject().has("playerType"));
        }
    }

    @Test public void sourceBindingCannotReintroduceApiPlayerType() {
        Live live = gson.fromJson("{\"playerType\":1}", EmptyMetadataLive.class);
        Channel inherited = new Channel();
        inherited.live(live);
        assertFalse(gson.toJsonTree(inherited).getAsJsonObject().has("playerType"));

        Channel overridden = gson.fromJson("{\"playerType\":2}", Channel.class);
        overridden.live(live);
        assertFalse(gson.toJsonTree(overridden).getAsJsonObject().has("playerType"));
    }

    private static class EmptyMetadataLive extends Live {
        @Override public String getUa() { return ""; }
        @Override public String getClick() { return ""; }
        @Override public String getOrigin() { return ""; }
        @Override public String getReferer() { return ""; }
        @Override public String getEpg() { return ""; }
        @Override public String getLogo() { return ""; }
        @Override public Catchup getCatchup() {
            return new Catchup() { @Override public boolean isEmpty() { return true; } };
        }
    }
}
