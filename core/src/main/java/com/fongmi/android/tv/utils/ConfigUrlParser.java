package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Parses user supplied configuration URL lists into stable, de-duplicated entries. */
public final class ConfigUrlParser {

    private static final String SEPARATOR = "[\\n\\r,，;；|]+";

    private ConfigUrlParser() {
    }

    public static List<String> parse(String value) {
        List<String> urls = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) return urls;
        Set<String> unique = new LinkedHashSet<>();
        for (String item : value.split(SEPARATOR)) {
            String url = item.trim();
            if (!url.isEmpty() && unique.add(url)) urls.add(url);
        }
        return urls;
    }
}
