package com.fongmi.android.tv.subtitle;

import okhttp3.HttpUrl;

/** Public user-facing search URLs. R3 login/verification stays on its own website. */
public final class SubtitleWebsites {
    private SubtitleWebsites() {}
    public static String subhd(String query) {
        return HttpUrl.parse(SubhdSubtitleParser.BASE).newBuilder().addPathSegment("search")
                .addPathSegment(SubtitleQueries.clean(query)).build().toString();
    }
    public static String r3(String query) {
        return HttpUrl.parse("https://r3sub.com/search.php").newBuilder()
                .addQueryParameter("s",SubtitleQueries.clean(query)).build().toString();
    }
}
