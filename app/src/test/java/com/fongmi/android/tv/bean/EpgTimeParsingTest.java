package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
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

    @Test public void overnightEndUsesProviderZoneAcrossSpringDstChange() throws Exception {
        EpgData programme = programme("2026-03-07", "23:30", "03:30", "America/New_York");
        assertEquals(Instant.parse("2026-03-08T04:30:00Z").toEpochMilli(), programme.getStartTime());
        assertEquals(Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(), programme.getEndTime());
    }

    @Test public void overnightEndUsesProviderZoneAcrossAutumnDstChange() throws Exception {
        EpgData programme = programme("2026-10-31", "23:30", "02:30", "America/New_York");
        assertEquals(Instant.parse("2026-11-01T03:30:00Z").toEpochMilli(), programme.getStartTime());
        assertEquals(Instant.parse("2026-11-01T07:30:00Z").toEpochMilli(), programme.getEndTime());
    }

    @Test public void overnightEndStillWorksWithoutDst() throws Exception {
        EpgData programme = programme("2026-10-18", "23:30:45", "01:30:12", "GMT+08:00");
        assertEquals(Instant.parse("2026-10-18T15:30:45Z").toEpochMilli(), programme.getStartTime());
        assertEquals(Instant.parse("2026-10-18T17:30:12Z").toEpochMilli(), programme.getEndTime());
    }

    @Test public void invalidEndTimeIsNotTurnedIntoAnEpochDay() throws Exception {
        EpgData programme = programme("2026-10-18", "23:30", "invalid", "GMT+08:00");
        assertEquals(0, programme.getEndTime());
    }

    private static EpgData programme(String date, String start, String end, String zone) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Epg epg = new Epg() { @Override public String getDate() { return date; } };
            EpgData item = new EpgData() {
                @Override public String getTitle() { return "Programme"; }
                @Override public String getStart() { return start; }
                @Override public String getEnd() { return end; }
                @Override public void trans() {} // Android preference-backed title translation.
            };
            epg.setList(Collections.singletonList(item));
            SimpleDateFormat minutes = new SimpleDateFormat("yyyy-MM-ddHH:mm", Locale.ROOT);
            SimpleDateFormat seconds = new SimpleDateFormat("yyyy-MM-ddHH:mm:ss", Locale.ROOT);
            minutes.setTimeZone(TimeZone.getTimeZone(zone));
            seconds.setTimeZone(TimeZone.getTimeZone(zone));
            Method setTime = Epg.class.getDeclaredMethod("setTime", List.class);
            setTime.setAccessible(true);
            setTime.invoke(epg, Arrays.asList(minutes, seconds));
            return item;
        } finally {
            TimeZone.setDefault(previous);
        }
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
