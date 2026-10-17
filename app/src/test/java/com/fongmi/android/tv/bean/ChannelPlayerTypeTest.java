package com.fongmi.android.tv.bean;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChannelPlayerTypeTest {
    private final Gson gson = new Gson();

    @Test public void readsLegacyPlayerTypeAndKeepsDefaultUnset() {
        assertEquals(-1, new Live().getPlayerType());
        assertEquals(-1, new Channel().getPlayerType().intValue());
        for (int type = 0; type <= 2; type++) {
            String json = "{\"playerType\":" + type + "}";
            assertEquals(type, gson.fromJson(json, Live.class).getPlayerType());
            Channel channel = gson.fromJson(json, Channel.class);
            assertEquals(type, channel.getPlayerType().intValue());
            assertEquals(type, gson.fromJson(gson.toJson(channel), Channel.class).getPlayerType().intValue());
        }
        assertEquals(2, gson.fromJson("{\"playerType\":10}", Live.class).getPlayerType());
        assertEquals(2, gson.fromJson("{\"playerType\":10}", Channel.class).getPlayerType().intValue());
    }

    @Test public void sourcePlayerIsInheritedUnlessChannelOverridesIt() {
        Live live = gson.fromJson("{\"playerType\":1}", EmptyMetadataLive.class);
        Channel inherited = new Channel();
        inherited.live(live);
        assertEquals(1, inherited.getPlayerType().intValue());

        Channel overridden = gson.fromJson("{\"playerType\":2}", Channel.class);
        overridden.live(live);
        assertEquals(2, overridden.getPlayerType().intValue());
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
