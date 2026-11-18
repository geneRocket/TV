package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.google.gson.Gson;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.*;

public class EpgStreamingTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void stringApiKeepsRootDateOrderAndSkipsInvalidTimestamps() throws Exception {
        Epg result = EpgParser.getEpg("<tv date='20261020120000 +0000'>"
                + programme("one", "20261020120000 +0000", "20261020130000 +0000", "News &amp; Weather")
                + programme("two", "bad", "20261020130000 +0000", "Invalid")
                + programme("two", "20261020130000 +0000", "20261020140000 +0000", "<![CDATA[Film <HD>]]>")
                + "</tv>", "requested");
        assertEquals(2, result.getList().size());
        assertEquals(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
                new Date(java.time.Instant.parse("2026-10-20T12:00:00Z").toEpochMilli())), json(result, "date"));
        assertEquals("requested", json(result, "key"));
        assertEquals("News & Weather", json(result.getList().get(0), "title"));
        assertEquals("Film <HD>", json(result.getList().get(1), "title"));
    }

    @Test public void fileApiMatchesLateAliasesKeepsDuplicateIdLogosAndFiltersDates() throws Exception {
        TestChannel alias = new TestChannel("provider", "Guide Name");
        TestChannel exact = new TestChannel("Guide Name", "Exact");
        String xml = "<tv>"
                + programme("xml", today(-1, 23), today(0, 1), "Midnight")
                + programme("xml", today(0, 12), today(0, 13), "Today")
                + programme("xml", today(2, 12), today(2, 13), "Future")
                + programme("unknown", today(0, 12), today(0, 13), "Unknown")
                + programme("xml", "bad", today(0, 13), "Invalid")
                + programme("Guide Name", today(0, 12), today(0, 13), "Exact ID")
                + "<channel id='xml'><display-name>Guide Name</display-name></channel>"
                + "<channel id='xml'><icon src='https://logo/news.png'/></channel></tv>";
        read(xml, alias, exact);
        // Explicit IDs have priority over aliases even when XML metadata comes last.
        assertTrue(alias.data.getList().isEmpty());
        assertEquals(3, exact.data.getList().size());
        assertEquals("Midnight", json(exact.data.getList().get(0), "title"));
        assertEquals("https://logo/news.png", exact.logo);

        TestChannel onlyAlias = new TestChannel("provider", "Guide Name");
        read(xml, onlyAlias);
        assertEquals(3, onlyAlias.data.getList().size());
        assertEquals("https://logo/news.png", onlyAlias.logo);
    }

    @Test public void malformedFeedDoesNotPublishSchedulesOrLogos() throws Exception {
        TestChannel channel = new TestChannel("news", "News");
        Epg original = channel.data;
        try {
            read("<tv><channel id='news'><icon src='https://logo/new.png'/></channel>"
                    + programme("news", today(0, 12), today(0, 13), "Partial") + "<broken>", channel);
            fail("Malformed feed accepted");
        } catch (InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof org.xmlpull.v1.XmlPullParserException);
        }
        assertSame(original, channel.data);
        assertEquals("", channel.logo);
    }

    @Test public void unresolvedSharedNameSuppliesGuideToBothPlaylistChannels() throws Exception {
        TestChannel hd = new TestChannel("news-hd", "News");
        TestChannel sd = new TestChannel("news-sd", "News");
        String xml = "<tv><channel id='provider-news'><display-name>News</display-name></channel>"
                + programme("provider-news", today(0, 12), today(0, 13), "Today") + "</tv>";
        read(xml, hd, sd);
        assertEquals("Today", json(hd.data.getList().get(0), "title"));
        assertEquals(1, sd.data.getList().size());
        assertEquals("Today", json(sd.data.getList().get(0), "title"));
    }

    @Test public void historicalGuideMatchesEitherChannelUsingTheSharedName() throws Exception {
        TestChannel hd = new TestChannel("news-hd", "News");
        TestChannel sd = new TestChannel("news-sd", "News");
        String xml = "<tv><channel id='provider-news'><display-name>News</display-name></channel>"
                + programme("provider-news", "20261016120000 +0000", "20261016130000 +0000", "News") + "</tv>";
        Epg result = EpgParser.readDay(xml, live(hd, sd), sd, "2026-10-16", TimeZone.getTimeZone("UTC"));
        assertEquals(1, result.getList().size());
        assertEquals("News", json(result.getList().get(0), "title"));
    }

    private void read(String xml, TestChannel... channels) throws Exception {
        File file = temporary.newFile();
        Files.write(file.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        Method read = EpgParser.class.getDeclaredMethod("readXml", Live.class, File.class);
        read.setAccessible(true);
        read.invoke(null, live(channels), file);
    }

    private static Live live(TestChannel... channels) {
        Group group = new Group("test");
        group.setChannel(Arrays.asList(channels));
        return new Live() { @Override public List<Group> getGroups() { return Collections.singletonList(group); } };
    }

    private static String today(int offset, int hour) {
        Calendar date = Calendar.getInstance();
        date.add(Calendar.DAY_OF_YEAR, offset);
        date.set(Calendar.HOUR_OF_DAY, hour);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        return new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).format(date.getTime());
    }

    private static String programme(String channel, String start, String stop, String title) {
        return "<programme channel='" + channel + "' start='" + start + "' stop='" + stop + "'><title>" + title + "</title></programme>";
    }

    private static String json(Object object, String field) {
        return new Gson().toJsonTree(object).getAsJsonObject().get(field).getAsString();
    }

    private static class TestChannel extends Channel {
        private final String id;
        private final String name;
        Epg data = Epg.create("", "");
        String logo = "";
        TestChannel(String id, String name) { this.id = id; this.name = name; }
        @Override public boolean hasTvgId() { return true; }
        @Override public String getTvgId() { return id; }
        @Override public String getTvgName() { return name; }
        @Override public String getName() { return name; }
        @Override public Epg getData() { return data; }
        @Override public void setData(Epg data) { this.data = data; }
        @Override public String getLogo() { return logo; }
        @Override public void setLogo(String logo) { this.logo = logo; }
    }
}
