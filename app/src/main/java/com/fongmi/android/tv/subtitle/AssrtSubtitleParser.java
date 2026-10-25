package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.Subtitle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import okhttp3.HttpUrl;
import java.util.*;
import java.util.regex.*;

/** ASSRT wire format only; no HTTP, lifecycle or UI state. */
public final class AssrtSubtitleParser {
    public static final String BASE = "https://secure.assrt.net";
    public static final String DOWNLOAD = "https://assrt.net";
    private static final String ASSRT_DOWNLOAD_URL = DOWNLOAD;
    private static final Pattern REGEX_SHOOTER_FILE_ONCLICK = Pattern.compile("onthefly\\(\"(\\d+)\",\"(\\d+)\",\"([\\s\\S]*)\"\\)");
    private AssrtSubtitleParser() {}

    public static SubtitleGateway.Page search(String html, String keyword, int page) {
        Document doc = Jsoup.parse(html);
        int total = parsePagesTotal(doc);
        return new SubtitleGateway.Page(parseSearchResults(doc, keyword), true, hasNextPage(doc, page, total), total);
    }

    public static SubtitleGateway.Page details(String html, String pageUrl) {
        Document doc = Jsoup.parse(html);
        List<Subtitle> items = new ArrayList<>();
        Elements files = doc.select("#detail-filelist .waves-effect");
        if (!files.isEmpty()) {
            for (Element item : files) {
                Matcher matcher = REGEX_SHOOTER_FILE_ONCLICK.matcher(item.attr("onclick"));
                if (!matcher.find()) continue;
                HttpUrl url = HttpUrl.parse(BASE).newBuilder().addPathSegment("download")
                        .addPathSegment(matcher.group(1)).addPathSegment("-")
                        .addPathSegment(matcher.group(2)).addPathSegment(matcher.group(3)).build();
                Element name = item.selectFirst("#filelist-name");
                items.add(new Subtitle(name == null ? matcher.group(3) : name.text(), url.toString(), false));
            }
        } else {
            Subtitle direct = parseDirectSubtitle(doc, pageUrl);
            if (direct == null) throw new IllegalArgumentException("No subtitle download found");
            items.add(direct);
        }
        return new SubtitleGateway.Page(items, false, false, -1);
    }

    private static List<Subtitle> parseSearchResults(Document doc, String keyword) {
        Elements items = doc.select(".resultcard .sublist_box_title a.introtitle, .resultcard a.introtitle, a.introtitle");
        List<Subtitle> data = new ArrayList<>();
        Set<String> urls = new HashSet<>();
        for (Element item : items) {
            String href = item.attr("href");
            if (com.fongmi.android.tv.utils.TextValues.isEmpty(href)) continue;
            String url = assrtUrl(ASSRT_DOWNLOAD_URL, href);
            if (!urls.add(url)) continue;
            String name = item.attr("title");
            if (com.fongmi.android.tv.utils.TextValues.isEmpty(name)) name = item.text();
            if (com.fongmi.android.tv.utils.TextValues.isEmpty(name)) continue;
            Subtitle one = new Subtitle();
            one.setName(name.trim());
            one.setUrl(url);
            one.setIsZip(true);
            one.setSource("ASSRT");
            Element card = item.closest(".resultcard");
            if (card != null) {
                Matcher languages = Pattern.compile("语言[：:]\\s*(.*?)(?:来源|制作|校订|上传|日期|$)").matcher(card.text());
                if (languages.find()) one.setLanguage(languages.group(1).trim());
            }
            data.add(one);
        }
        Collections.sort(data, Comparator.comparingInt(item -> SubtitleRanking.score(item.getName(), keyword)));
        return data;
    }

    private static int parsePagesTotal(Document doc) {
        Elements pages = doc.select(".pagelinkcard a, .pagination a");
        for (int i = pages.size() - 1; i >= 0; i--) {
            String[] values = pages.get(i).text().split("/", 2);
            if (values.length != 2) continue;
            try {
                return Integer.parseInt(values[1].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private static boolean hasNextPage(Document doc, int currentPage, int total) {
        if (total > 0) return currentPage < total;
        for (Element item : doc.select(".pagelinkcard a, .pagination a")) {
            String text = item.text().trim();
            if (text.contains("下一") || text.equalsIgnoreCase("next") || text.equals(">")) return !item.hasClass("disabled");
        }
        return false;
    }

    public static Subtitle parseDirectSubtitle(Document document, String pageUrl) {
        Element item = document.selectFirst(".download a#btn_download");
        if (item == null) return null;
        String href = item.attr("href");
        if (href.isEmpty()) return null;
        HttpUrl page = pageUrl == null ? null : HttpUrl.parse(pageUrl);
        HttpUrl url = page == null ? null : page.resolve(href);
        if (url == null) return null;
        String name = url.pathSegments().get(url.pathSegments().size() - 1);
        String file = name.toLowerCase(Locale.ROOT);
        String lower = href.toLowerCase(Locale.ROOT);
        boolean knownFile = file.endsWith(".srt") || file.endsWith(".ass") || file.endsWith(".ssa")
                || file.endsWith(".vtt") || file.endsWith(".ttml") || file.endsWith(".xml")
                || file.endsWith(".dfxp") || file.endsWith(".scc");
        // Preserve existing query-based download endpoints such as ?format=srt.
        boolean legacyEndpoint = lower.endsWith("srt") || lower.endsWith("ass") || lower.endsWith("scc") || lower.endsWith("ttml");
        if (!knownFile && !legacyEndpoint) return null;
        Subtitle subtitle = new Subtitle();
        subtitle.setUrl(url.toString());
        subtitle.setName(name);
        subtitle.setIsZip(false);
        return subtitle;
    }

    private static String assrtUrl(String base, String href) {
        if (href == null || href.isEmpty()) return "";
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        if (href.startsWith("//")) return "https:" + href;
        if (href.startsWith("/")) return base + href;
        return base + "/" + href;
    }

    public static String normalizeAssrtLocation(String requestUrl, String location) {
        if (requestUrl == null || location == null || location.trim().isEmpty()) return "";
        HttpUrl base = HttpUrl.parse(requestUrl);
        HttpUrl target = base == null ? null : base.resolve(location.trim());
        return target == null ? "" : target.toString();
    }
}
