package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses user supplied configuration URL lists into stable, de-duplicated entries. */
public final class ConfigUrlParser {

    private static final Pattern SEPARATOR = Pattern.compile("[\\n\\r]+|[,，;；|]+(?=\\s*[a-zA-Z][a-zA-Z0-9+.-]*://)");
    private static final Pattern EMPTY = Pattern.compile("[\\s,，;；|]*");

    private ConfigUrlParser() {
    }

    public static List<String> parse(String value) {
        List<String> urls = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) return urls;
        Set<String> unique = new LinkedHashSet<>();
        for (String item : SEPARATOR.split(value)) {
            String url = item.trim();
            if (!EMPTY.matcher(url).matches() && unique.add(url)) urls.add(url);
        }
        return urls;
    }
}
