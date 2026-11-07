package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.utils.TextValues;

import com.google.gson.annotations.SerializedName;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Catchup {

    private static final Pattern TOKEN = Pattern.compile("\\$?\\{[^}]*\\}");

    @SerializedName("type")
    private String type;
    @SerializedName("days")
    private String days;
    @SerializedName("regex")
    private String regex;
    @SerializedName("source")
    private String source;
    @SerializedName("replace")
    private String replace;

    public static Catchup PLTV() {
        Catchup item = new Catchup();
        item.setDays("7");
        item.setType("append");
        item.setRegex("/PLTV/");
        item.setSource("?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}");
        return item;
    }

    public static Catchup create() {
        return new Catchup();
    }

    public static Catchup decide(Catchup major, Catchup minor) {
        Catchup selected = !major.isEmpty() ? major : minor;
        if (selected.isEmpty()) return null;
        String days = major.getDays().isEmpty() ? minor.getDays() : major.getDays();
        if (days.equals(selected.getDays())) return selected;
        Catchup result = new Catchup();
        result.type = selected.type;
        result.regex = selected.regex;
        result.source = selected.source;
        result.replace = selected.replace;
        result.days = days;
        return result;
    }

    public String getType() {
        return TextValues.isEmpty(type) ? "" : type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDays() {
        return TextValues.isEmpty(days) ? "" : days;
    }

    public void setDays(String days) {
        this.days = days;
    }

    public String getRegex() {
        return TextValues.isEmpty(regex) ? "" : regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public String getSource() {
        return TextValues.isEmpty(source) ? "" : source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setReplace(String replace) {
        this.replace = replace;
    }

    public boolean match(String url) {
        return url.contains(getRegex()) || Pattern.compile(getRegex()).matcher(url).find();
    }

    public boolean isEmpty() {
        return getSource().isEmpty();
    }

    private boolean isAppend() {
        return getType().equals("append");
    }

    private boolean isDefault() {
        return getType().equals("default");
    }

    private String format(String url, String result) {
        url = rewrite(url);
        int fragment = url.indexOf('#');
        int query = url.indexOf('?');
        if (result.startsWith("?") && query >= 0 && query + 1 < (fragment < 0 ? url.length() : fragment)) {
            result = "&" + result.substring(1);
        }
        return url + result;
    }

    private String rewrite(String url) {
        if (!TextValues.isEmpty(replace)) {
            String[] parts = replace.split(",", 2);
            if (parts.length == 2 && !parts[0].isEmpty()) {
                try {
                    return url.replaceAll(parts[0], parts[1]);
                } catch (IllegalArgumentException | IndexOutOfBoundsException ignored) {
                    // Fall back to legacy PLTV rewriting for invalid rules.
                }
            }
        }
        return url.replace("/PLTV/", "/TVOD/");
    }

    public String format(String url, EpgData data) {
        String result = getSource();
        if (data.isInRange()) return url;
        Matcher matcher = TOKEN.matcher(result);
        StringBuffer formatted = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(formatted, Matcher.quoteReplacement(data.format(matcher.group())));
        result = matcher.appendTail(formatted).toString();
        return isDefault() ? result : format(url, result);
    }
}
