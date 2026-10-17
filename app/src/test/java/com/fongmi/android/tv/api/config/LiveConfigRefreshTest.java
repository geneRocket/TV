package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class LiveConfigRefreshTest {
    @Test public void changedSpiderExtensionInvalidatesLoadedChannels() {
        PureLive old = loaded("old extension", "spider.jar");
        LiveConfig.updateLive(old, source("new extension", "spider.jar"));
        assertTrue(old.getGroups().isEmpty());
        assertEquals("new extension", old.getExt());
    }

    @Test public void changedSpiderJarInvalidatesLoadedChannels() {
        PureLive old = loaded("extension", "old.jar");
        LiveConfig.updateLive(old, source("extension", "new.jar"));
        assertTrue(old.getGroups().isEmpty());
        assertEquals("new.jar", old.getJar());
    }

    @Test public void unchangedSourceKeepsItsLoadedChannels() {
        PureLive old = loaded("extension", "spider.jar");
        Group group = old.getGroups().get(0);
        LiveConfig.updateLive(old, source("extension", "spider.jar"));
        assertEquals(1, old.getGroups().size());
        assertSame(group, old.getGroups().get(0));
    }

    @Test public void refreshedHeadersReplacePreviouslyCachedHeadersAndChannels() throws Exception {
        PureLive old = loaded("extension", "spider.jar");
        field(old, "header", JsonParser.parseString("{\"Cookie\":\"old-token\"}"));
        assertEquals("old-token", old.getHeaders().get("Cookie"));
        PureLive incoming = source("extension", "spider.jar");
        field(incoming, "header", JsonParser.parseString("{\"Cookie\":\"new-token\"}"));
        LiveConfig.updateLive(old, incoming);
        assertEquals("new-token", old.getHeaders().get("Cookie"));
        assertTrue(old.getGroups().isEmpty());
    }

    @Test public void refreshedPlaybackAndEpgSettingsReplaceOldSourceSettings() throws Exception {
        PureLive old = loaded("extension", "spider.jar");
        old.setBoot(true);
        old.setPass(true);
        PureLive incoming = source("extension", "spider.jar");
        field(incoming, "playerType", 1);
        field(incoming, "epg", "https://example.com/new-epg.xml");
        LiveConfig.updateLive(old, incoming);
        assertEquals(1, old.getPlayerType());
        assertEquals("https://example.com/new-epg.xml", new Gson().toJsonTree(old, Live.class).getAsJsonObject().get("epg").getAsString());
        assertTrue(old.isBoot());
        assertTrue(old.isPass());
        assertTrue(old.getGroups().isEmpty());
    }

    @Test public void refreshedInlineGroupsReplacePreviousChannels() {
        PureLive old = loaded("extension", "spider.jar");
        PureLive incoming = source("extension", "spider.jar");
        Group group = new Group("New inline channels");
        incoming.getGroups().add(group);
        LiveConfig.updateLive(old, incoming);
        assertEquals(1, old.getGroups().size());
        assertSame(group, old.getGroups().get(0));
    }

    private static void field(Live live, String name, Object value) throws Exception {
        java.lang.reflect.Field field = Live.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(live, value);
    }

    private static PureLive loaded(String ext, String jar) {
        PureLive live = source(ext, jar);
        live.getGroups().add(new Group("Loaded channels"));
        return live;
    }

    private static PureLive source(String ext, String jar) {
        PureLive live = new PureLive();
        live.setUrl("https://example.com/live");
        live.setApi("csp_Live");
        live.setExt(ext);
        live.setJar(jar);
        return live;
    }

    private static class PureLive extends Live {
        private String url, api, ext, jar;
        @Override public String getUrl() { return url; }
        @Override public String getApi() { return api; }
        @Override public String getExt() { return ext; }
        @Override public String getJar() { return jar; }
        @Override public void setUrl(String value) { url = value; }
        @Override public void setApi(String value) { api = value; }
        @Override public void setExt(String value) { ext = value; }
        @Override public void setJar(String value) { jar = value; }
        @Override public String getUa() { return ""; }
        @Override public String getOrigin() { return ""; }
        @Override public String getReferer() { return ""; }
    }
}
