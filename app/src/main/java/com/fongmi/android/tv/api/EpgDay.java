package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.EpgData;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Calendar days in the guide provider's time zone. */
public final class EpgDay {

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 30;

    private final long start;
    private final long end;

    public EpgDay(String date, TimeZone zone) throws ParseException {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTime(format(zone).parse(date));
        start = calendar.getTimeInMillis();
        calendar.add(Calendar.DATE, 1);
        end = calendar.getTimeInMillis();
    }

    public boolean contains(EpgData programme) {
        return programme.getStartTime() > 0 && programme.getEndTime() > programme.getStartTime()
                && programme.getStartTime() < end && programme.getEndTime() > start;
    }

    public static List<String> previousDates(TimeZone zone, long now, String days) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(now);
        SimpleDateFormat format = format(zone);
        List<String> dates = new ArrayList<>();
        int count = days(days);
        for (int i = 0; i < count; i++) {
            calendar.add(Calendar.DATE, -1);
            dates.add(format.format(calendar.getTime()));
        }
        return dates;
    }

    public static int days(String value) {
        if (value == null) return DEFAULT_DAYS;
        try {
            return Math.max(0, Math.min(MAX_DAYS, Integer.parseInt(value.trim())));
        } catch (NumberFormatException ignored) {
            return DEFAULT_DAYS;
        }
    }

    private static SimpleDateFormat format(TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setLenient(false);
        format.setTimeZone(zone);
        return format;
    }
}
