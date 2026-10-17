package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class EpgTimeParsingTest {

    @Test public void secondPrecisionIsNotTruncatedByTheMinuteFormat() throws Exception {
        assertEquals(Instant.parse("2026-10-18T01:05:47Z").toEpochMilli(), parse("2026-10-1809:05:47"));
    }

    @Test public void minutePrecisionStillUsesTheConfiguredTimeZone() throws Exception {
        assertEquals(Instant.parse("2026-10-18T01:05:00Z").toEpochMilli(), parse("2026-10-1809:05"));
    }

    @Test public void trailingGarbageIsNotAcceptedAsAValidProgrammeTime() throws Exception {
        assertEquals(0, parse("2026-10-1809:05broken"));
        assertEquals(0, parse("2026-10-1809:05:47broken"));
    }

    private static long parse(String value) throws Exception {
        SimpleDateFormat minutes = new SimpleDateFormat("yyyy-MM-ddHH:mm", Locale.ROOT);
        SimpleDateFormat seconds = new SimpleDateFormat("yyyy-MM-ddHH:mm:ss", Locale.ROOT);
        minutes.setTimeZone(TimeZone.getTimeZone("GMT+08:00"));
        seconds.setTimeZone(TimeZone.getTimeZone("GMT+08:00"));
        // The real LiveViewModel supplies the shorter format first.
        List<SimpleDateFormat> formats = Arrays.asList(minutes, seconds);
        Method parse = Epg.class.getDeclaredMethod("parse", List.class, String.class);
        parse.setAccessible(true);
        return (long) parse.invoke(new Epg(), formats, value);
    }
}
