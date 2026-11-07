package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;

import org.junit.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

public class EpgParserTest {
    @Test public void historicalDatesUseProviderCalendarAndDeclaredDays() {
        java.util.TimeZone zone = java.util.TimeZone.getTimeZone("Asia/Shanghai");
        long now = Instant.parse("2026-10-16T17:30:00Z").toEpochMilli();
        assertEquals(Arrays.asList("2026-10-16", "2026-10-15"), EpgDay.previousDates(zone, now, "2"));
        assertTrue(EpgDay.previousDates(zone, now, "0").isEmpty());
    }

    @Test public void historicalDayEndsAtNextMidnightOnDstChange() throws Exception {
        EpgDay day = new EpgDay("2026-03-08", java.util.TimeZone.getTimeZone("America/New_York"));
        EpgData crossing = new EpgData();
        crossing.setStartTime(Instant.parse("2026-03-09T03:45:00Z").toEpochMilli());
        crossing.setEndTime(Instant.parse("2026-03-09T04:15:00Z").toEpochMilli());
        assertTrue(day.contains(crossing));
        crossing.setStartTime(Instant.parse("2026-03-09T04:00:00Z").toEpochMilli());
        assertFalse(day.contains(crossing));
    }

    @Test public void historicalXmlIncludesMidnightOverlapAndUsesChannelAndDate() throws Exception {
        Channel news = namedChannel("news", "News", "News");
        Channel other = namedChannel("other", "Other", "Other");
        Group group = new Group("Test");
        group.setChannel(Arrays.asList(news, other));
        Live live = new Live() { @Override public List<Group> getGroups() { return Arrays.asList(group); } };
        java.nio.file.Path xml = java.nio.file.Files.createTempFile("historical-guide", ".xml");
        try {
            String payload = "<tv>"
                    + "<programme channel='provider-news' start='20261015233000 +0800' stop='20261016003000 +0800'><title>Late</title></programme>"
                    + "<programme channel='other' start='20261016100000 +0800' stop='20261016110000 +0800'><title>Other</title></programme>"
                    + "<programme channel='provider-news' start='20261016230000 +0800' stop='20261017000000 +0800'><title>Evening</title></programme>"
                    + "<programme channel='provider-news' start='20261017000000 +0800' stop='20261017010000 +0800'><title>Tomorrow</title></programme>"
                    + "<channel id='provider-news'><display-name>News</display-name></channel></tv>";
            java.nio.file.Files.write(xml, payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.util.TimeZone zone = java.util.TimeZone.getTimeZone("Asia/Shanghai");
            Epg result = EpgParser.readHistory(xml.toFile(), live, news, "2026-10-16", zone);
            Epg response = EpgParser.readHistory(payload, live, news, "2026-10-16", zone);
            assertEquals(2, response.getList().size());
            assertEquals("23:30", new com.google.gson.Gson().toJsonTree(response.getList().get(0)).getAsJsonObject().get("start").getAsString());
            assertEquals(2, result.getList().size());
            assertEquals(Instant.parse("2026-10-15T15:30:00Z").toEpochMilli(), result.getList().get(0).getStartTime());
            assertEquals(Instant.parse("2026-10-16T15:00:00Z").toEpochMilli(), result.getList().get(1).getStartTime());
        } finally {
            java.nio.file.Files.deleteIfExists(xml);
        }
    }

    @Test public void emptyProviderIdRemainsAnAliasFallback() {
        Channel channel = new Channel();
        assertFalse(channel.hasTvgId());
        channel.setTvgId("");
        assertFalse(channel.hasTvgId());
        channel.setTvgId("provider.101");
        assertTrue(channel.hasTvgId());
        assertEquals("provider.101", channel.getTvgId());
    }

    @Test public void cacheFilesAreIsolatedByFullSourceUrlAndHeaders() {
        String first = EpgParser.cacheName("https://one.example/epg.xml");
        assertNotEquals(first, EpgParser.cacheName("https://two.example/epg.xml"));
        assertNotEquals(first, EpgParser.cacheName("https://one.example/epg.xml?region=2"));
        assertNotEquals(EpgParser.cacheName("https://one.example/epg.xml@Cookie=account=1"),
                EpgParser.cacheName("https://one.example/epg.xml@Cookie=account=2"));
        assertEquals(first, EpgParser.cacheName("  https://one.example/epg.xml  "));
        assertTrue(first.matches("[a-f0-9]{64}\\.epg"));
    }

    @Test public void exactChannelIdTakesPriorityOverAnotherChannelsName() throws Exception {
        Channel alias = namedChannel("news-hd", "News", "News");
        Channel exact = namedChannel("News", "Other", "Other");
        assertSame(exact, channelIndex(alias, exact).get("News"));
        assertSame(exact, channelIndex(exact, alias).get("News"));
        assertSame(alias, channelIndex(alias, exact).get("news-hd"));
    }

    @Test public void namesRemainAvailableWhenNoExactIdMatches() throws Exception {
        Channel channel = namedChannel("provider.101", "Guide Name", "Display Name");
        Map<String, Channel> index = channelIndex(channel);
        assertSame(channel, index.get("provider.101"));
        assertSame(channel, index.get("Guide Name"));
        assertSame(channel, index.get("Display Name"));
    }

    @Test public void implicitIdFromNameCannotShadowAnExplicitProviderId() throws Exception {
        Channel fallback = namedChannel("News", "News", "News", false);
        Channel explicit = namedChannel("News", "Other", "Other");
        assertSame(explicit, channelIndex(fallback, explicit).get("News"));
        assertSame(explicit, channelIndex(explicit, fallback).get("News"));
        assertSame(fallback, channelIndex(fallback).get("News"));
    }

    private static Channel namedChannel(String id, String guideName, String name) {
        return namedChannel(id, guideName, name, true);
    }

    private static Channel namedChannel(String id, String guideName, String name, boolean explicitId) {
        return new Channel() {
            @Override public boolean hasTvgId() { return explicitId; }
            @Override public String getTvgId() { return id; }
            @Override public String getTvgName() { return guideName; }
            @Override public String getName() { return name; }
            @Override public String toString() { return id; }
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Channel> channelIndex(Channel... channels) throws Exception {
        Group group = new Group("Test");
        group.setChannel(Arrays.asList(channels));
        Live live = new Live() { @Override public List<Group> getGroups() { return Arrays.asList(group); } };
        Method prepare = EpgParser.class.getDeclaredMethod("prepareLiveChannels", Live.class);
        prepare.setAccessible(true);
        return (Map<String, Channel>) prepare.invoke(null, live);
    }

    @Test public void nextDayRefreshReplacesPreviousSchedule() throws Exception {
        Epg yesterday = epg("2026-10-16", programme("Yesterday", "10:00", "11:00"));
        Epg today = epg("2026-10-17", programme("Today", "10:00", "11:00"));
        TestChannel channel = new TestChannel(yesterday);

        merge(channel, today);

        assertSame(today, channel.getData());
        assertEquals(1, channel.getData().getList().size());
        assertEquals("Today", channel.getData().getList().get(0).getTitle());
    }

    @Test public void sameDaySourcesStillMergeWithoutDuplicates() throws Exception {
        Epg original = epg("2026-10-17", programme("News", "10:00", "11:00"));
        Epg additional = epg("2026-10-17", programme("News", "10:00", "11:00"), programme("Film", "11:00", "12:00"));
        TestChannel channel = new TestChannel(original);

        merge(channel, additional);

        assertEquals(2, channel.getData().getList().size());
        assertEquals(1, original.getList().size());
        assertEquals(2, additional.getList().size());
    }

    @Test public void concurrentDateParsingKeepsEachRequestsTimestamp() throws Exception {
        Method parse = EpgParser.class.getDeclaredMethod("parse", String.class);
        parse.setAccessible(true);
        String[] values = {"20261017234500 +0800", "20250101010203 -0500"};
        long[] expected = {Instant.parse("2026-10-17T15:45:00Z").toEpochMilli(), Instant.parse("2025-01-01T06:02:03Z").toEpochMilli()};
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                final int index = worker % 2;
                futures.add(workers.submit(() -> {
                    try {
                        for (int attempt = 0; attempt < 1000; attempt++) {
                            Date result = (Date) parse.invoke(null, values[index]);
                            assertNotNull(result);
                            assertEquals(expected[index], result.getTime());
                        }
                    } catch (ReflectiveOperationException error) {
                        throw new AssertionError(error);
                    }
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            workers.shutdownNow();
        }
    }

    private static void merge(Channel channel, Epg epg) throws Exception {
        Method merge = EpgParser.class.getDeclaredMethod("merge", Channel.class, Epg.class, String.class);
        merge.setAccessible(true);
        merge.invoke(null, channel, epg, null);
    }

    private static Epg epg(String date, EpgData... programmes) {
        Epg epg = new Epg() { @Override public String getDate() { return date; } };
        epg.setList(new ArrayList<>(Arrays.asList(programmes)));
        return epg;
    }

    private static EpgData programme(String title, String start, String end) {
        return new EpgData() {
            @Override public String getTitle() { return title; }
            @Override public String getStart() { return start; }
            @Override public String getEnd() { return end; }
        };
    }

    private static class TestChannel extends Channel {
        private Epg data;
        TestChannel(Epg data) { this.data = data; }
        @Override public Epg getData() { return data; }
        @Override public void setData(Epg data) { this.data = data; }
        @Override public String getTvgId() { return "news"; }
    }
}
