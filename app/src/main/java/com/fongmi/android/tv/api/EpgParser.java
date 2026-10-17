package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Tv;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.utils.Path;
import com.github.catvod.net.HttpCallScope;
import com.github.catvod.utils.Trans;

import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import okio.ByteString;

public class EpgParser {

    private static final ThreadLocal<SimpleDateFormat> FORMAT_TIME = ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm", Locale.getDefault()));
    private static final ThreadLocal<SimpleDateFormat> FORMAT_DATE = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()));
    private static final ThreadLocal<SimpleDateFormat> FORMAT_FULL = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault()));
    private static final ThreadLocal<FormatContext> FORMAT_CONTEXT = new ThreadLocal<>();

    static void refreshFormats() {
        Locale locale = Locale.getDefault();
        TimeZone zone = TimeZone.getDefault();
        FormatContext previous = FORMAT_CONTEXT.get();
        if (previous != null && previous.locale.equals(locale) && previous.zone.equals(zone)) return;
        FORMAT_TIME.set(createFormat("HH:mm", locale, zone));
        FORMAT_DATE.set(createFormat("yyyy-MM-dd", locale, zone));
        FORMAT_FULL.set(createFormat("yyyyMMddHHmmss Z", locale, zone));
        FORMAT_CONTEXT.set(new FormatContext(locale, zone));
    }

    private static SimpleDateFormat createFormat(String pattern, Locale locale, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
        format.setTimeZone((TimeZone) zone.clone());
        return format;
    }

    private static final class FormatContext {
        final Locale locale;
        final TimeZone zone;
        FormatContext(Locale locale, TimeZone zone) { this.locale = locale; this.zone = zone; }
    }

    public static boolean start(Live live) throws Exception {
        HttpCallScope.throwIfCancelled();
        List<String> xmls = live.getEpgXml();
        if (xmls.isEmpty()) return false;
        boolean success = false;
        for (String url : xmls) {
            try {
                success |= start(live, url);
            } catch (Exception e) {
                HttpCallScope.throwIfCancelled();
                e.printStackTrace();
            }
        }
        return success;
    }

    public static boolean start(Live live, String url) throws Exception {
        HttpCallScope.throwIfCancelled();
        File file = Path.epg(cacheName(url));
        boolean refresh = shouldRefresh(file);
        if (refresh) Download.create(url, file).sync();
        if (isGzip(file)) readGzip(live, file, refresh);
        else readXml(live, file);
        return true;
    }

    static String cacheName(String url) {
        return ByteString.encodeUtf8(url.trim()).sha256().hex() + ".epg";
    }

    public static Epg getEpg(String xml, String key) throws Exception {
        HttpCallScope.throwIfCancelled();
        refreshFormats();
        Tv tv = new Persister().read(Tv.class, xml, false);
        Epg epg = Epg.create(key, FORMAT_DATE.get().format(getDate(tv)));
        for (Tv.Programme programme : tv.getProgramme()) {
            HttpCallScope.throwIfCancelled();
            EpgData item = getEpgData(programme);
            if (item != null) epg.getList().add(item);
        }
        HttpCallScope.throwIfCancelled();
        return epg;
    }

    private static boolean shouldRefresh(File file) {
        return !file.exists() || !isToday(file.lastModified()) || System.currentTimeMillis() - file.lastModified() > TimeUnit.HOURS.toMillis(6);
    }

    private static boolean isGzip(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return (fis.read() | (fis.read() << 8)) == 0x8B1F;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isToday(long millis) {
        return isToday(millis, new FormatContext(Locale.getDefault(), TimeZone.getDefault()));
    }

    private static boolean isToday(long millis, FormatContext context) {
        Calendar calendar = Calendar.getInstance(context.zone, context.locale);
        calendar.setTimeInMillis(millis);
        Calendar today = Calendar.getInstance(context.zone, context.locale);
        return calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

    private static void readGzip(Live live, File file, boolean refresh) throws Exception {
        File xml = Path.epg(file.getName() + ".xml");
        prepareGzip(file, xml, refresh);
        readXml(live, xml);
    }

    static void prepareGzip(File file, File xml, boolean refresh) throws IOException {
        HttpCallScope.throwIfCancelled();
        if (xml.exists() && !refresh && xml.lastModified() >= file.lastModified()) return;
        if (FileUtil.tryExtractGzip(file, xml)) return;
        HttpCallScope.throwIfCancelled();
        // Keep the working XML, but force the next attempt to fetch a new archive.
        file.delete();
        throw new IOException("Unable to decompress EPG cache");
    }

    private static void readXml(Live live, File file) throws Exception {
        HttpCallScope.throwIfCancelled();
        refreshFormats();
        FormatContext context = FORMAT_CONTEXT.get();
        Map<String, Channel> liveChannelMap = prepareLiveChannels(live);
        Tv tv = new Persister().read(Tv.class, file, false);
        HttpCallScope.throwIfCancelled();
        Map<String, List<Tv.Channel>> xmlChannelMap = tv.getChannel().stream().collect(Collectors.groupingBy(Tv.Channel::getId));
        String today = FORMAT_DATE.get().format(new Date());
        Map<String, Epg> epgMap = new HashMap<>();
        Map<String, String> srcMap = new HashMap<>();
        for (Tv.Programme programme : tv.getProgramme()) {
            HttpCallScope.throwIfCancelled();
            Channel channel = findTargetChannel(programme.getChannel(), liveChannelMap, xmlChannelMap);
            if (channel == null) continue;
            Date startDate = parse(programme.getStart());
            Date endDate = parse(programme.getStop());
            if (startDate == null || endDate == null) continue;
            boolean startToday = startDate != null && isToday(startDate.getTime(), context);
            boolean endToday = endDate != null && isToday(endDate.getTime(), context);
            if (!startToday && !endToday) continue;
            String key = channel.getTvgId();
            epgMap.computeIfAbsent(key, k -> Epg.create(k, today)).getList().add(getEpgData(startDate, endDate, programme));
            List<Tv.Channel> xmlChannels = xmlChannelMap.get(programme.getChannel());
            if (xmlChannels != null) {
                for (Tv.Channel item : xmlChannels) {
                    if (item.hasSrc()) {
                        srcMap.putIfAbsent(key, item.getSrc());
                        break;
                    }
                }
            }
        }
        for (Group group : live.getGroups()) {
            for (Channel channel : group.getChannel()) {
                HttpCallScope.throwIfCancelled();
                merge(channel, epgMap.get(channel.getTvgId()), srcMap.get(channel.getTvgId()));
            }
        }
    }

    private static Map<String, Channel> prepareLiveChannels(Live live) {
        List<Channel> channels = live.getGroups().stream()
                .flatMap(group -> group.getChannel().stream())
                .collect(Collectors.toList());
        Map<String, Channel> index = new HashMap<>();
        // Register every provider ID before aliases, regardless of playlist order.
        for (Channel channel : channels) {
            if (channel.hasTvgId()) index.putIfAbsent(channel.getTvgId(), channel);
        }
        for (Channel channel : channels) {
            for (String alias : new String[]{channel.getTvgId(), channel.getTvgName(), channel.getName()}) {
                if (!alias.isEmpty()) index.putIfAbsent(alias, channel);
            }
        }
        return index;
    }

    private static Channel findTargetChannel(String xmlChannelId, Map<String, Channel> liveChannelMap, Map<String, List<Tv.Channel>> xmlChannelMap) {
        Channel direct = liveChannelMap.get(xmlChannelId);
        if (direct != null) return direct;
        List<Tv.Channel> channels = xmlChannelMap.get(xmlChannelId);
        if (channels == null) return null;
        for (Tv.Channel channel : channels) {
            for (Tv.DisplayName name : channel.getDisplayName()) {
                if (liveChannelMap.containsKey(name.getText())) return liveChannelMap.get(name.getText());
            }
        }
        return null;
    }

    private static Date getDate(Tv tv) {
        Date date = parse(tv.getDate());
        return date == null ? new Date() : date;
    }

    private static Date parse(String value) {
        try {
            return value == null || value.isEmpty() ? null : FORMAT_FULL.get().parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static EpgData getEpgData(Tv.Programme programme) {
        return getEpgData(parse(programme.getStart()), parse(programme.getStop()), programme);
    }

    private static EpgData getEpgData(Date startDate, Date endDate, Tv.Programme programme) {
        if (startDate == null || endDate == null) return null;
        EpgData epgData = new EpgData();
        epgData.setTitle(Trans.s2t(programme.getTitle()));
        epgData.setStart(FORMAT_TIME.get().format(startDate));
        epgData.setEnd(FORMAT_TIME.get().format(endDate));
        epgData.setStartTime(startDate.getTime());
        epgData.setEndTime(endDate == null ? 0 : endDate.getTime());
        if (epgData.getEndTime() < epgData.getStartTime()) epgData.checkDay();
        return epgData;
    }

    private static void merge(Channel channel, Epg epg, String logo) {
        if (epg != null && !epg.getList().isEmpty()) {
            Epg data = channel.getData();
            if (data.getList().isEmpty() || !data.equal(epg.getDate())) {
                channel.setData(epg);
            } else {
                Epg item = Epg.create(channel.getTvgId(), epg.getDate());
                item.setList(new java.util.ArrayList<>(new LinkedHashSet<>(data.getList())));
                item.getList().addAll(epg.getList());
                item.setList(new java.util.ArrayList<>(new LinkedHashSet<>(item.getList())));
                channel.setData(item);
            }
        }
        if (logo != null && channel.getLogo().isEmpty()) channel.setLogo(logo);
    }
}
