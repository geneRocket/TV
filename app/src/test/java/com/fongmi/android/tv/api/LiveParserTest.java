package com.fongmi.android.tv.api;

import com.github.catvod.utils.Json;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Live;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.Map;
import java.util.List;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LiveParserTest {

    @Test public void txtHeaderUpdatesDoNotRewriteEarlierChannels() throws Exception {
        List<Channel> channels = parseTxtChannels("News,#genre#\n"
                + "#KODIPROP:inputstream.adaptive.stream_headers=Cookie=first&X-Common=shared\n"
                + "First,https://example.com/first\n"
                + "#KODIPROP:inputstream.adaptive.stream_headers=Cookie=second&X-Next=next\n"
                + "Second,https://example.com/second\n");
        assertEquals(2, channels.size());
        assertEquals("first", channels.get(0).getHeader().get("Cookie"));
        assertEquals("shared", channels.get(0).getHeader().get("X-Common"));
        assertNull(channels.get(0).getHeader().get("X-Next"));
        assertEquals("second", channels.get(1).getHeader().get("Cookie"));
        assertEquals("shared", channels.get(1).getHeader().get("X-Common"));
        assertEquals("next", channels.get(1).getHeader().get("X-Next"));
    }

    @Test public void txtKodiOverridesDoNotMutateEarlierJsonHeaders() throws Exception {
        List<Channel> channels = parseTxtChannels("News,#genre#\n"
                + "header={\"Authorization\":\"Bearer first\",\"Cookie\":\"session=a=b\"}\n"
                + "First,https://example.com/first\n"
                + "#KODIPROP:inputstream.adaptive.stream_headers=Authorization=Bearer second\n"
                + "Second,https://example.com/second\n");
        assertEquals("Bearer first", channels.get(0).getHeader().get("Authorization"));
        assertEquals("Bearer second", channels.get(1).getHeader().get("Authorization"));
        assertEquals("session=a=b", channels.get(0).getHeader().get("Cookie"));
        assertEquals("session=a=b", channels.get(1).getHeader().get("Cookie"));
    }

    // Exercise parsing without the Android resource and live-default enrichment in text().
    private static List<Channel> parseTxtChannels(String text) throws Exception {
        Live live = new Live();
        Method parse = LiveParser.class.getDeclaredMethod("txt", Live.class, String.class);
        parse.setAccessible(true);
        parse.invoke(null, live, text);
        return live.getGroups().get(0).getChannel();
    }

    @Test public void channelMetadataPreservesProviderIdSeparatelyFromDisplayName() {
        Channel channel = new Channel();
        LiveParser.channelMetadata(channel, "#EXTINF:-1 tvg-id=\"provider.101\" tvg-name=\"News HD\" tvg-logo=\"https://example.com/logo.png\",News");
        JsonObject metadata = new Gson().toJsonTree(channel).getAsJsonObject();
        assertEquals("provider.101", metadata.get("tvgId").getAsString());
        assertEquals("News HD", metadata.get("tvgName").getAsString());
        assertEquals("https://example.com/logo.png", metadata.get("logo").getAsString());

        LiveParser.channelMetadata(channel, "#EXTINF:-1 tvg-name=\"Other\",Other");
        assertEquals("", new Gson().toJsonTree(channel).getAsJsonObject().get("tvgId").getAsString());
    }

    @Test
    public void channelNamesIgnoreCommasInsideMetadata() {
        assertEquals("Channel, HD", LiveParser.channelName(
                "#EXTINF:-1 group-title=\"News, International\" tvg-name=\"Channel, One\",Channel, HD"));
        assertEquals("CCTV 1", LiveParser.channelName("#EXTINF:-1 tvg-name=\"CCTV 1\", CCTV 1"));
        assertEquals("", LiveParser.channelName("#EXTINF:-1 group-title=\"News, International\""));
    }

    @Test
    public void jsonHeadersKeepQuotesAndCookieDelimiters() {
        String json = "{\"Cookie\":\"session=a=b; token=c\",\"Referer\":\"https://example.com/live?id=1\",\"User-Agent\":\"Player/1.0 (TV)\"}";
        for (String prefix : new String[]{"header=", "#EXTHTTP:"}) {
            Map<String, String> headers = Json.toMap(Json.parse(LiveParser.extractValue(prefix + json, prefix)));
            assertEquals("session=a=b; token=c", headers.get("Cookie"));
            assertEquals("https://example.com/live?id=1", headers.get("Referer"));
            assertEquals("Player/1.0 (TV)", headers.get("User-Agent"));
        }
    }

    @Test
    public void scalarValuesRemoveOnlySurroundingQuotes() {
        assertEquals("Player/1.0", LiveParser.extractValue("ua=\"Player/1.0\"", "ua="));
        assertEquals("session=\"signed\"", LiveParser.extractValue("ua=session=\"signed\"", "ua="));
        assertEquals("https://example.com/?a=b=c", LiveParser.extractValue("referer=https://example.com/?a=b=c", "referer="));
    }
}
