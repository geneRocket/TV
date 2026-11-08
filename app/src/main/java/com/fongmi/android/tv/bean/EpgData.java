package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Trans;
import com.google.gson.annotations.SerializedName;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

public class EpgData {

    @SerializedName("title")
    private String title;
    @SerializedName("start")
    private String start;
    @SerializedName("end")
    private String end;

    private boolean selected;
    private long startTime;
    private long endTime;

    public String getTitle() {
        return TextUtils.isEmpty(title) ? "" : title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStart() {
        return TextUtils.isEmpty(start) ? "" : start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return TextUtils.isEmpty(end) ? "" : end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setSelected(EpgData item) {
        this.selected = item.equals(this);
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public boolean isInRange() {
        return getStartTime() <= System.currentTimeMillis() && System.currentTimeMillis() <= getEndTime();
    }

    public boolean isFuture() {
        return getStartTime() > System.currentTimeMillis();
    }

    public String format(String group) {
        return format(group, TimeZone.getDefault());
    }

    String format(String group, TimeZone timeZone) {
        int open = group.indexOf('{');
        String token = group.substring(open + 1);
        if (token.startsWith("utc:")) return String.valueOf(getStartTime() / 1000);
        if (token.startsWith("utcend:")) return String.valueOf(getEndTime() / 1000);
        String pattern = extractPattern(group);
        if (pattern.isEmpty()) return "";
        if (token.startsWith("(b)")) return formatTime(getStartTime(), pattern, timeZone);
        if (token.startsWith("(e)")) return formatTime(getEndTime(), pattern, timeZone);
        return "";
    }

    private String formatTime(long time, String pattern, TimeZone timeZone) {
        if ("timestamp".equals(pattern)) return String.valueOf(time / 1000);
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.getDefault());
        format.setTimeZone(timeZone);
        return format.format(time);
    }

    private String extractPattern(String group) {
        int start = group.indexOf(')');
        int end = group.indexOf('}', start + 1);
        if (start < 0 || end < 0 || end <= start + 1) return "";
        return group.substring(start + 1, end);
    }

    public String format() {
        if (getTitle().isEmpty()) return "";
        if (getStart().isEmpty() && getEnd().isEmpty()) return ResUtil.getString(R.string.play_now, getTitle());
        return getStart() + " ~ " + getEnd() + "  " + getTitle();
    }

    public void checkDay() {
        checkDay(TimeZone.getDefault());
    }

    public void checkDay(TimeZone timeZone) {
        Calendar cal = Calendar.getInstance(timeZone);
        cal.setTimeInMillis(getEndTime());
        cal.add(Calendar.DAY_OF_MONTH, 1);
        setEndTime(cal.getTimeInMillis());
    }

    public void trans() {
        if (Trans.pass()) return;
        this.title = Trans.s2t(title);
    }

    public String getTime() {
        if (getStart().isEmpty() && getEnd().isEmpty()) return "";
        return getStart() + " ~ " + getEnd();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EpgData)) return false;
        EpgData it = (EpgData) obj;
        return Objects.equals(getTitle(), it.getTitle()) && Objects.equals(getEnd(), it.getEnd()) && Objects.equals(getStart(), it.getStart());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTitle(), getEnd(), getStart());
    }
}
