package com.fongmi.android.tv.model;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class LiveProgrammeGuideTest {
    private static final String DATE = "2026-10-16";
    private static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Shanghai");
    private static final String CHANNEL_ENDPOINT = "https://example.com/news?date={date}";

    @Test public void currentGuideUsesTheRequestedChannelAndProviderDay() throws Exception {
        Channel news = channel("news-hd", "News");
        Channel movies = channel("movies", "Movies");
        Live source = source("https://example.com/guide.xml", news, movies);
        String body = "<tv date='20261015173000 +0000'>"
                + "<channel id='guide-news'><display-name>News</display-name></channel>"
                + "<programme channel='movies' start='20261015173000 +0000' stop='20261015183000 +0000'><title>Wrong channel</title></programme>"
                + "<programme channel='guide-news' start='20261015173000 +0000' stop='20261015183000 +0000'><title>News</title></programme>"
                + "<programme channel='guide-news' start='20261016173000 +0000' stop='20261016183000 +0000'><title>Wrong day</title></programme></tv>";

        assertNews(LiveViewModel.parseProgrammeGuide(body, source, news, DATE, ZONE));
        assertNews(LiveViewModel.parseProgrammeGuide(body, null, news, DATE, ZONE));
    }

    @Test public void channelEndpointAcceptsItsSoleOpaqueProviderId() throws Exception {
        Channel news = channel("news-hd", "News");
        String body = "<tv>" + programme("opaque-42", "News") + "</tv>";
        assertNews(LiveViewModel.parseProgrammeGuide(body, source("", news), news, DATE, ZONE));
    }

    @Test public void channelEndpointRejectsAmbiguousOrKnownUnrelatedProgrammes() throws Exception {
        Channel news = channel("news-hd", "News");
        Live source = source("", news, channel("movies", "Movies"));
        String ambiguous = "<tv>" + programme("opaque-42", "News") + programme("opaque-99", "Other") + "</tv>";
        String unrelated = "<tv>" + programme("movies", "Other") + "</tv>";
        assertEquals(0, LiveViewModel.parseProgrammeGuide(ambiguous, source, news, DATE, ZONE).getList().size());
        assertEquals(0, LiveViewModel.parseProgrammeGuide(unrelated, source, news, DATE, ZONE).getList().size());
    }

    @Test public void sharedEndpointNeverAssumesAnUnknownProviderIdIsTheSelectedChannel() throws Exception {
        Channel news = channel("news-hd", "News");
        String body = "<tv>" + programme("opaque-42", "News") + "</tv>";
        assertEquals(0, LiveViewModel.parseProgrammeGuide(body, source(CHANNEL_ENDPOINT, news), news, DATE, ZONE).getList().size());
    }

    private static void assertNews(Epg guide) {
        assertEquals(1, guide.getList().size());
        Gson gson = new Gson();
        assertEquals(DATE, gson.toJsonTree(guide).getAsJsonObject().get("date").getAsString());
        assertEquals("News", gson.toJsonTree(guide.getList().get(0)).getAsJsonObject().get("title").getAsString());
        assertEquals("01:30", gson.toJsonTree(guide.getList().get(0)).getAsJsonObject().get("start").getAsString());
    }

    private static String programme(String id, String title) {
        return "<programme channel='" + id + "' start='20261015173000 +0000' stop='20261015183000 +0000'><title>"
                + title + "</title></programme>";
    }

    private static Live source(String endpoint, Channel... channels) {
        Group group = new Group("Example");
        group.setChannel(Arrays.asList(channels));
        return new Live() {
            @Override public List<Group> getGroups() { return Collections.singletonList(group); }
            @Override public String getEpg() { return endpoint; }
        };
    }

    private static Channel channel(String id, String name) {
        return new Channel() {
            @Override public boolean hasTvgId() { return true; }
            @Override public String getTvgId() { return id; }
            @Override public String getTvgName() { return name; }
            @Override public String getName() { return name; }
            @Override public String getEpg() { return CHANNEL_ENDPOINT; }
        };
    }
}
