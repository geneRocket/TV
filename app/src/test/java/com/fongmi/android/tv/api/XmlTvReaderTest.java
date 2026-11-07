package com.fongmi.android.tv.api;

import com.github.catvod.net.HttpCallScope;

import org.junit.Test;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.InterruptedIOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class XmlTvReaderTest {
    @Test public void usesFirstNonBlankProgrammeTitle() throws Exception {
        List<XmlTvReader.Programme> programmes = new ArrayList<>();
        String xml = "<tv><programme><title/><title>  </title>"
                + "<title lang='zh'>新闻</title><title lang='en'>News</title></programme></tv>";
        XmlTvReader.read(new StringReader(xml), null, programmes::add);
        assertEquals("新闻", programmes.get(0).title);
    }

    @Test public void readsAliasesIconsUnicodeCdataAndEntitiesIgnoringUnknownSubtrees() throws Exception {
        List<XmlTvReader.Channel> channels = new ArrayList<>();
        List<XmlTvReader.Programme> programmes = new ArrayList<>();
        String date = XmlTvReader.read(new StringReader("<?xml version='1.0'?><tv date='20261020120000 +0800'>"
                + "<metadata><channel id='fake'/><programme/></metadata>"
                + "<channel id='news'><display-name lang='zh'> 新闻 &amp; 综合 </display-name>"
                + "<display-name><![CDATA[News <HD>]]></display-name><icon src='https://logo/a.png'/></channel>"
                + "<programme channel='news' start='20261020120000 +0800' stop='20261020130000 +0800'>"
                + "<desc><title>Hidden</title></desc><title> 新闻<![CDATA[ <直播>]]> &amp; Weather </title>"
                + "<credits><actor>Somebody</actor></credits></programme></tv>"), channels::add, programmes::add);
        assertEquals("20261020120000 +0800", date);
        assertEquals(1, channels.size());
        assertEquals("news", channels.get(0).id);
        assertEquals("新闻 & 综合", channels.get(0).names.get(0));
        assertEquals("News <HD>", channels.get(0).names.get(1));
        assertEquals("https://logo/a.png", channels.get(0).logo);
        assertEquals(1, programmes.size());
        assertEquals("news", programmes.get(0).channel);
        assertEquals("20261020120000 +0800", programmes.get(0).start);
        assertEquals("20261020130000 +0800", programmes.get(0).stop);
        assertEquals("新闻 <直播> & Weather", programmes.get(0).title);
    }

    @Test public void detectsDeclaredEncodingAndDoesNotFetchExternalDtd() throws Exception {
        String xml = "<?xml version='1.0' encoding='UTF-16'?><!DOCTYPE tv SYSTEM 'file:///missing/xmltv.dtd'>"
                + "<tv><programme><title>中文</title></programme></tv>";
        List<XmlTvReader.Programme> programmes = new ArrayList<>();
        XmlTvReader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_16)), null, programmes::add);
        assertEquals("中文", programmes.get(0).title);
        assertEquals("", programmes.get(0).start);
        assertEquals("", programmes.get(0).stop);
    }

    @Test public void deliversEntriesBeforeReadingTheWholeFeed() throws Exception {
        StringBuilder xml = new StringBuilder("<tv>");
        for (int i = 0; i < 20000; i++) xml.append("<programme channel='news'><title>News</title></programme>");
        xml.append("</tv>");
        int[] consumed = {0};
        int[] delivered = {0};
        StringReader input = new StringReader(xml.toString()) {
            @Override public int read(char[] buffer, int offset, int length) throws java.io.IOException {
                int count = super.read(buffer, offset, Math.min(length, 128));
                if (count > 0) consumed[0] += count;
                return count;
            }
        };
        XmlTvReader.read(input, null, programme -> {
            if (delivered[0]++ == 0) assertTrue(consumed[0] < xml.length() / 2);
        });
        assertEquals(20000, delivered[0]);
    }

    @Test public void cancellationStopsBetweenProgrammeCallbacks() throws Exception {
        HttpCallScope scope = new HttpCallScope();
        int[] delivered = {0};
        try {
            scope.call(() -> XmlTvReader.read(new StringReader("<tv><programme/><programme/></tv>"), null, programme -> {
                delivered[0]++;
                scope.close();
            }));
            fail("Cancellation ignored");
        } catch (InterruptedIOException expected) {
            assertEquals(1, delivered[0]);
        }
    }

    @Test public void cancellationAlsoStopsWhileSkippingUnwantedContent() throws Exception {
        HttpCallScope scope = new HttpCallScope();
        StringReader input = new StringReader("<tv><ignored><nested/></ignored></tv>") {
            @Override public int read(char[] buffer, int offset, int length) throws java.io.IOException {
                int count = super.read(buffer, offset, Math.min(length, 4));
                scope.close();
                return count;
            }
        };
        try {
            scope.call(() -> XmlTvReader.read(input, null, null));
            fail("Cancellation ignored");
        } catch (InterruptedIOException expected) {
            assertFalse(Thread.currentThread().isInterrupted());
        }
    }

    @Test public void rejectsTruncatedWrongRootAndExtraRootDocuments() throws Exception {
        for (String xml : new String[]{"<tv><programme><title>Broken", "<html/>", "<tv/><tv/>", "<tv><ignored>"}) {
            try {
                XmlTvReader.read(new StringReader(xml), channel -> {}, programme -> {});
                fail("Accepted " + xml);
            } catch (XmlPullParserException expected) {
                // A malformed response must not be treated as a complete schedule.
            }
        }
    }
}
