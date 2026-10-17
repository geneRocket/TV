package com.fongmi.android.tv.api;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.junit.Assert.*;

public class EpgFormatContextTest {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Date instant = Date.from(Instant.parse("2026-10-17T20:00:00Z"));
    private Locale previousLocale;
    private TimeZone previousZone;

    @Before public void captureDefaults() {
        previousLocale = Locale.getDefault(); previousZone = TimeZone.getDefault();
        Locale.setDefault(Locale.US); TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @After public void restoreDefaults() {
        Locale.setDefault(previousLocale); TimeZone.setDefault(previousZone); worker.shutdownNow();
    }

    @Test public void reusedWorkerUsesTheNewDeviceTimeZone() throws Exception {
        assertEquals("20:00", format("FORMAT_TIME"));
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+08:00"));
        assertEquals("04:00", format("FORMAT_TIME"));
    }

    @Test public void programmeDateTracksTheSameNewTimeZone() throws Exception {
        assertEquals("2026-10-17", format("FORMAT_DATE"));
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+08:00"));
        assertEquals("2026-10-18", format("FORMAT_DATE"));
    }

    @Test public void reusedWorkerUsesTheNewFormattingLocale() throws Exception {
        String old = format("FORMAT_TIME");
        Locale locale = Locale.forLanguageTag("ar-EG-u-nu-arab");
        Locale.setDefault(locale);
        SimpleDateFormat expected = new SimpleDateFormat("HH:mm", locale);
        expected.setTimeZone(TimeZone.getTimeZone("UTC"));
        assertNotEquals(old, expected.format(instant));
        assertEquals(expected.format(instant), format("FORMAT_TIME"));
    }

    @Test public void refreshReplacesRatherThanMutatesAFormatsInUse() throws Exception {
        SimpleDateFormat first = worker.submit(() -> {
            EpgParser.refreshFormats(); return formatter("FORMAT_TIME");
        }).get();
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+08:00"));
        SimpleDateFormat second = worker.submit(() -> {
            EpgParser.refreshFormats(); return formatter("FORMAT_TIME");
        }).get();
        assertNotSame(first, second);
        assertEquals("20:00", first.format(instant));
        assertEquals("04:00", second.format(instant));
    }

    private String format(String field) throws Exception {
        return worker.submit(() -> {
            EpgParser.refreshFormats(); return formatter(field).format(instant);
        }).get();
    }

    @SuppressWarnings("unchecked")
    private SimpleDateFormat formatter(String name) throws Exception {
        Field field = EpgParser.class.getDeclaredField(name); field.setAccessible(true);
        return ((ThreadLocal<SimpleDateFormat>) field.get(null)).get();
    }
}
