package com.fongmi.android.tv.bean;

import com.google.gson.Gson;

import org.junit.Test;
import java.time.Instant;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class CatchupTest {

    @Test public void pltvReplayKeepsProgrammeWallTimeAcrossDeviceZones() {
        EpgData data = new EpgData();
        data.setStartTime(Instant.parse("2026-11-07T04:00:00Z").toEpochMilli());
        data.setEndTime(Instant.parse("2026-11-07T04:35:00Z").toEpochMilli());
        String url = "https://example.com/PLTV/live.m3u8?token=signed";
        String expected = "https://example.com/TVOD/live.m3u8?token=signed&playseek=20261107120000-20261107123500";
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zone : new String[]{"Asia/Shanghai", "UTC"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                assertEquals(expected, Catchup.PLTV().format(url, data));
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test public void expandsUtcAndTimestampTemplates() {
        Catchup catchup = new Gson().fromJson("{\"type\":\"default\",\"source\":"
                + "\"https://example.com/replay?start={utc:yyyyMMddHHmmss}&stop=${utcend:timestamp}&from=${(b)timestamp}\"}", Catchup.class);
        for (String zone : new String[]{"Asia/Shanghai", "UTC"}) {
            assertEquals("https://example.com/replay?start=1700000000&stop=1700003600&from=1700000000",
                    catchup.format("https://example.com/live", programme(), TimeZone.getTimeZone(zone)));
        }
    }

    @Test public void configuredGuideZoneOverridesPltvDefault() {
        EpgData data = new EpgData();
        data.setStartTime(Instant.parse("2026-11-07T04:00:00Z").toEpochMilli());
        data.setEndTime(Instant.parse("2026-11-07T04:35:00Z").toEpochMilli());
        assertEquals("https://example.com/TVOD/live.m3u8?playseek=20261107130000-20261107133500",
                Catchup.PLTV().format("https://example.com/PLTV/live.m3u8", data, TimeZone.getTimeZone("Asia/Tokyo")));
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
