package com.fongmi.android.tv.bean;

import com.google.gson.Gson;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CatchupTest {

    @Test public void expandsUtcAndTimestampTemplates() {
        Catchup catchup = new Gson().fromJson("{\"type\":\"default\",\"source\":"
                + "\"https://example.com/replay?start={utc:yyyyMMddHHmmss}&stop=${utcend:timestamp}&from=${(b)timestamp}\"}", Catchup.class);
        assertEquals("https://example.com/replay?start=1700000000&stop=1700003600&from=1700000000",
                catchup.format("https://example.com/live", programme()));
    }

    @Test public void rewritesReplayPathAndKeepsExistingQuery() {
        Catchup catchup = new Gson().fromJson("{\"type\":\"append\",\"replace\":\"/live/(\\\\d+)/,/replay/$1/\","
                + "\"source\":\"?start={utc:timestamp}\"}", Catchup.class);
        assertEquals("https://example.com/replay/42/index.m3u8?token=signed&start=1700000000",
                catchup.format("https://example.com/live/42/index.m3u8?token=signed", programme()));
    }

    @Test public void ignoresInvalidRewriteAndKeepsLegacyPltvFallback() {
        Catchup catchup = new Gson().fromJson("{\"replace\":\"[,/replay/\",\"source\":\"?start={utc:timestamp}\"}", Catchup.class);
        assertEquals("https://example.com/TVOD/index.m3u8?start=1700000000",
                catchup.format("https://example.com/PLTV/index.m3u8", programme()));
    }

    private static EpgData programme() {
        EpgData data = new EpgData();
        data.setStartTime(1700000000000L);
        data.setEndTime(1700003600000L);
        return data;
    }
}
