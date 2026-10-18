package com.fongmi.android.tv.player;

import com.fongmi.android.tv.bean.Channel;
import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import okhttp3.Headers;
import static org.junit.Assert.assertEquals;

public class PlaybackInlineHeadersTest {
    @Test public void inlineAuthorizationOverridesPlaybackHeadersIgnoringCase() {
        Map<String, String> defaults = defaults();
        Headers headers = Headers.of(PlaybackHeaders.mergeInlineHeaders(defaults,
                "https://example.com/video@Headers={\"authorization\":\"Bearer inline\",\"x-token\":\"inline\"}"));
        assertEquals(1, headers.values("Authorization").size());
        assertEquals("Bearer inline", headers.get("Authorization"));
        assertEquals(1, headers.values("X-Token").size());
        assertEquals("inline", headers.get("X-Token"));
        assertEquals("Bearer default", defaults.get("Authorization"));
    }

    @Test public void channelInlineAuthorizationOverridesChannelDefaultsIgnoringCase() {
        Channel channel = new Channel() {
            @Override public String getUa() { return ""; }
            @Override public String getOrigin() { return ""; }
            @Override public String getReferer() { return ""; }
            @Override public String getUrl() { return ""; }
            @Override public String getCurrent() {
                return "https://example.com/live@Headers={\"authorization\":\"Bearer inline\",\"x-token\":\"inline\"}";
            }
        };
        channel.setHeader(defaults());
        Headers headers = Headers.of(channel.getHeaders());
        assertEquals(1, headers.values("Authorization").size());
        assertEquals("Bearer inline", headers.get("Authorization"));
        assertEquals(1, headers.values("X-Token").size());
        assertEquals("inline", headers.get("X-Token"));
        assertEquals("Bearer default", channel.getHeader().get("Authorization"));
    }

    @Test public void absentInlineMetadataKeepsDefaultsAndAcceptsNullDefaults() {
        Headers headers = Headers.of(PlaybackHeaders.mergeInlineHeaders(defaults(), "https://example.com/video"));
        assertEquals("Bearer default", headers.get("Authorization"));
        assertEquals("default", headers.get("X-Token"));
        assertEquals(0, PlaybackHeaders.mergeInlineHeaders(null, "https://example.com/video").size());
    }

    private static Map<String, String> defaults() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer default");
        headers.put("X-Token", "default");
        return headers;
    }
}
