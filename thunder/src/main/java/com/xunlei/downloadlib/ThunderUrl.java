package com.xunlei.downloadlib;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ThunderUrl {

    public static final String MAGNET_QUERY = "magnet:?";
    private static final Pattern MAGNET = Pattern.compile("^magnet:/*\\?");

    private ThunderUrl() {
    }

    public static String normalize(String url) {
        if (url == null) return null;
        int colon = url.indexOf(':');
        if (colon < 0) return url;
        url = url.substring(0, colon).toLowerCase(Locale.ROOT) + url.substring(colon);
        Matcher magnet = MAGNET.matcher(url);
        if (!magnet.find()) return url;
        int fragment = url.indexOf('#', magnet.end());
        int end = fragment < 0 ? url.length() : fragment;
        String[] parameters = url.substring(magnet.end(), end).split("&", -1);
        for (int i = 0; i < parameters.length; i++) parameters[i] = normalizeExactTopic(parameters[i]);
        return MAGNET_QUERY + String.join("&", parameters) + url.substring(end);
    }

    private static String normalizeExactTopic(String parameter) {
        int equals = parameter.indexOf('=');
        if (equals < 0) return parameter;
        try {
            if (!"xt".equals(URLDecoder.decode(parameter.substring(0, equals), "UTF-8"))) return parameter;
            String value = URLDecoder.decode(parameter.substring(equals + 1).replace("+", "%2B"), "UTF-8");
            return "xt=" + URLEncoder.encode(value, "UTF-8").replace("%3A", ":").replace("+", "%20");
        } catch (IllegalArgumentException | UnsupportedEncodingException ignored) {
            return parameter;
        }
    }
}
