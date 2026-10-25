package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.Subtitle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import okhttp3.HttpUrl;
import java.util.*;

/** SubHD search is public; download verification is completed by the user on the website. */
public final class SubhdSubtitleParser {
    public static final String BASE = "https://subhd.me";
    private SubhdSubtitleParser() {}
    public static SubtitleGateway.Page search(String html, String keyword, int page) {
        Document document = Jsoup.parse(html);
        if (!document.select("#challenge-form, .cf-error-details").isEmpty()
                || document.title().toLowerCase(Locale.ROOT).contains("just a moment")) {
            throw new IllegalArgumentException("SubHD requires website verification");
        }
        List<Subtitle> items = new ArrayList<>(); Set<String> urls = new HashSet<>();
        for (Element link : document.select(".view-text a[href]")) {
            String href = link.attr("href");
            if (!href.matches("/a/[A-Za-z0-9]+")) continue;
            String url = HttpUrl.parse(BASE).resolve(href).toString();
            if (!urls.add(url)) continue;
            Element card = link.closest(".clearfix");
            Element title = card == null ? null : card.selectFirst("a.align-middle");
            String name = link.text().trim();
            if (name.isEmpty()) continue;
            if (title != null && !name.contains(title.text())) name = title.text() + " · " + name;
            Subtitle item = new Subtitle(name, url, false);
            item.setSource("SubHD"); item.setExternal(true);
            if (title != null) item.setMovieTitle(title.text().trim());
            if (card != null) item.setLanguage(card.select(".f11 .fw-bold").text());
            items.add(item);
        }
        if (items.isEmpty() && document.selectFirst("#sn") == null) throw new IllegalArgumentException("Unrecognized SubHD search page");
        int total = -1; boolean next = false;
        for (Element link : document.select(".pagination a[href]")) {
            if (link.hasClass("disabled") || link.parent().hasClass("disabled")) continue;
            String href = link.attr("href");
            if (!href.startsWith("/search/")) continue;
            String tail = href.substring(href.lastIndexOf('/') + 1);
            if (!tail.matches("\\d{1,5}")) continue;
            int number = Integer.parseInt(tail);
            total = Math.max(total, number); next |= number > page;
        }
        items.sort(Comparator.comparingInt(item -> SubtitleRanking.score(item.getName(), keyword)));
        return new SubtitleGateway.Page(items, false, next, total);
    }
}
