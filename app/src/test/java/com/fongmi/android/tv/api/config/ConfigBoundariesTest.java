package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.Config;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigBoundariesTest {
    @Test public void immutableDocumentOwnsPayloadAndSourceIdentity() {
        JsonObject object=json("{\"sites\":[{\"key\":\"original\"}]}"); List<String> urls=new ArrayList<>(Arrays.asList("one"));
        ConfigDocument document=new ConfigDocument(object,urls); object.add("sites",new JsonArray()); urls.clear();
        document.object().getAsJsonArray("sites").get(0).getAsJsonObject().addProperty("key","draft");
        assertEquals("original",document.object().getAsJsonArray("sites").get(0).getAsJsonObject().get("key").getAsString());
        assertEquals(Collections.singletonList("one"),document.urls());
        try { document.urls().clear(); fail(); } catch(UnsupportedOperationException expected) { }
    }
    @Test public void inputConfigurationsAreCapturedBeforeBackgroundWorkStarts() {
        Config config=config(7,"one"); config.setJson("{\"sites\":[]}"); List<Config> requested=new ArrayList<>(Arrays.asList(config));
        List<Config> captured=ConfigRequests.capture(requested); config.setUrl("two"); config.setJson("invalid"); requested.clear();
        assertEquals("one",captured.get(0).getUrl()); assertEquals("{\"sites\":[]}",captured.get(0).getJson());
    }
    @Test public void failedRemoteLoadFallsBackToValidatedCacheWithoutPublishingDraft() {
        VodConfig previous=VodConfig.get();
        Config config=config(7,"one"); config.setJson("{\"sites\":[]}");
        VodConfigLoader loader=new VodConfigLoader(url->{ throw new IllegalStateException("offline"); });
        VodConfigLoader.Result result=loader.loadResult(config);
        assertNull(result.error); assertFalse(result.cacheable); assertTrue(result.object.has("sites"));
        assertSame(previous,VodConfig.get());
    }
    @Test public void invalidRemoteAndCachePayloadCannotBecomeAReadyRuntime() {
        Config config=config(7,"one"); config.setJson("{\"sites\":{}}");
        VodConfigLoader loader=new VodConfigLoader(url->"{\"msg\":\"denied\"}");
        VodConfig previous=VodConfig.get(); VodConfigLoader.Result result=loader.loadResult(config);
        assertNotNull(result.error); assertNull(result.object); assertSame(previous,VodConfig.get());
    }
    @Test public void depotTriesNextSourceAndPreservesSuccessfulDocument() throws Throwable {
        List<String> fetched=new ArrayList<>();
        VodConfigLoader loader=new VodConfigLoader(url->{
            fetched.add(url); if(url.equals("root"))return "{\"urls\":[{\"url\":\"bad\"},{\"url\":\"good\"}]}";
            if(url.equals("bad"))throw new IllegalStateException("unavailable"); return "{\"sites\":[],\"flags\":[\"working\"]}";
        });
        assertEquals("working",loader.loadObject("root",0).getAsJsonArray("flags").get(0).getAsString());
        assertEquals(Arrays.asList("root","bad","good"),fetched);
    }

    @Test public void depotRegistersAllNamedSourcesBeforeReadingTheFirst() throws Throwable {
        List<String> registered = new ArrayList<>();
        VodConfigLoader loader = new VodConfigLoader(url -> {
            if ("root".equals(url)) {
                return "{\"urls\":[{\"url\":\"first\",\"name\":\"First\"},{\"url\":\"second\",\"name\":\"Second\"}]}";
            }
            assertEquals("first", url);
            assertEquals(List.of("First:first", "Second:second"), registered);
            return "{\"sites\":[]}";
        }, entry -> registered.add(entry.getName() + ":" + entry.getUrl()));

        assertTrue(loader.loadObject("root", 0).has("sites"));
    }

    @Test public void liveDepotAcceptsATextPlaylistAtTheChildUrl() throws Throwable {
        String child = "https://example.test/lists/live.m3u";
        String text = "#EXTM3U\n#EXTINF:-1,Channel\nhttps://example.test/stream.m3u8";
        List<String> validated = new ArrayList<>();
        LiveConfigLoader loader = new LiveConfigLoader(url -> {
            assertEquals(child, url);
            return text;
        }, entry -> { }, (url, playlist) -> validated.add(url));

        LiveConfigLoader.Payload payload = loader.resolvePayload("https://example.test/config.json",
                "{\"urls\":[{\"url\":\"" + child + "\"}]}", 0);

        assertEquals(child, payload.url);
        assertEquals(text, payload.text);
        assertNull(payload.object);
        assertEquals(List.of(child), validated);
    }
    @Test public void cancelledGenerationCannotPublishEvenWhenWorkerIgnoresInterrupts() {
        ConfigLoadTask.Session session=new ConfigLoadTask.Session(); AtomicInteger publication=new AtomicInteger();
        session.run(()->{ session.cancel(); Thread.interrupted(); ConfigLoadTask.commit(publication::incrementAndGet); });
        assertEquals(0,publication.get());
    }
    @Test public void buildingAnotherRuntimeDoesNotMutatePublishedConfigurationOrInput() {
        VodConfig previous=VodConfig.get(); Config input=config(7,"original");
        VodConfig candidate=new VodConfig().config(input); input.setUrl("changed");
        assertEquals("original",candidate.getConfig().getUrl()); assertSame(previous,VodConfig.get());
    }
    @Test public void mergingScopesKeepsSiteAndJarOwnershipAndNeverEditsInputs() {
        JsonObject source=json("{\"spider\":\"one.jar\",\"sites\":[{\"key\":\"site\",\"api\":\"csp_Test\"}]}");
        JsonObject original=source.deepCopy(), merged=new JsonObject();
        ConfigMerger.vod(merged,source,config(7,"one")); ConfigMerger.vod(merged,source,config(8,"two"));
        assertEquals(original,source); JsonArray sites=merged.getAsJsonArray("sites");
        assertEquals("7@site",sites.get(0).getAsJsonObject().get("key").getAsString());
        assertEquals("8@site",sites.get(1).getAsJsonObject().get("key").getAsString());
        assertEquals("one.jar",sites.get(1).getAsJsonObject().get("jar").getAsString());
    }
    @Test public void editingUrlKeepsTheOriginalIdentityAndCachedDocumentUntilCommit() {
        Config original = config(7, "old");
        original.json("{\"sites\":[]}");
        Config edit = original.editUrl("new", "replacement").copy();
        assertEquals("old", original.getUrl());
        assertEquals("{\"sites\":[]}", original.getJson());
        assertEquals(7, edit.getId());
        assertEquals("new", edit.getUrl());
        assertTrue(edit.getJson().isEmpty());
        assertTrue(edit.isUrlEdit());
    }

    @Test public void legacyInheritanceRequiresMatchingSourcesAndNoExplicitMarker() {
        List<String> original = Arrays.asList("one", "two");
        assertTrue(ConfigApplicationService.isInheritedSelection("one\ntwo", null, original));
        assertFalse(ConfigApplicationService.isInheritedSelection("one\ntwo", "", original));
        assertFalse(ConfigApplicationService.isInheritedSelection("manual", null, original));
        assertTrue(ConfigApplicationService.isInheritedSelection("one\ntwo", "one\ntwo", List.of("new")));
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static Config config(int id,String url) { Config c=new Config(); c.setId(id);c.setUrl(url);return c; }
}
