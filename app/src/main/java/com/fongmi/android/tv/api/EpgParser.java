package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.utils.Path;
import com.github.catvod.net.HttpCallScope;
import com.github.catvod.utils.Trans;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.Comparator;
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
        readXml(live, xmlFile(url));
        return true;
    }

    private static File xmlFile(String url) throws Exception {
        HttpCallScope.throwIfCancelled();
        File file = Path.epg(cacheName(url));
        boolean refresh = shouldRefresh(file);
        if (refresh) Download.create(url, file).sync();
        if (!isGzip(file)) return file;
        File xml = Path.epg(file.getName() + ".xml");
        prepareGzip(file, xml, refresh);
        return xml;
    }

    static String cacheName(String url) {
        return ByteString.encodeUtf8(url.trim()).sha256().hex() + ".epg";
    }

    public static Epg getHistory(Live live, Channel channel, String date, TimeZone zone) throws Exception {
        Epg result = Epg.create(channel.getTvgId(), date);
        for (String url : live.getEpgXml()) {
            try {
                result.getList().addAll(readHistory(xmlFile(url), live, channel, date, zone).getList());
            } catch (Exception error) {
                HttpCallScope.throwIfCancelled();
                error.printStackTrace();
            }
        }
        result.setList(new ArrayList<>(new LinkedHashSet<>(result.getList())));
        result.getList().sort(Comparator.comparingLong(EpgData::getStartTime));
        return result;
    }

    static Epg readHistory(File file, Live live, Channel channel, String date, TimeZone zone) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            return readHistory(filePass(input), live, channel, date, zone);
        }
    }

    public static Epg readHistory(String xml, Live live, Channel channel, String date, TimeZone zone) throws Exception {
        return readHistory((channels, programmes) -> XmlTvReader.read(new StringReader(xml), channels, programmes),
                live, channel, date, zone);
    }

    private static Epg readHistory(XmlPass pass, Live live, Channel channel, String date, TimeZone zone) throws Exception {
        refreshFormats();
        Epg result = Epg.create(channel.getTvgId(), date);
        EpgDay day = new EpgDay(date, zone);
        SimpleDateFormat time = createFormat("HH:mm", Locale.getDefault(), zone);
        Map<String, Channel> index = prepareLiveChannels(live);
        index.putIfAbsent(channel.getTvgId(), channel);
        scanXml(pass, index, null, (target, programme) -> {
            if (!channel.getTvgId().equals(target.getTvgId())) return false;
            EpgData item = getEpgData(programme);
            if (item == null || !day.contains(item)) return false;
            item.setStart(time.format(new Date(item.getStartTime())));
            item.setEnd(time.format(new Date(item.getEndTime())));
            result.getList().add(item);
            return true;
        });
        result.getList().sort(Comparator.comparingLong(EpgData::getStartTime));
        return result;
    }

    public static Epg getEpg(String xml, String key) throws Exception {
        HttpCallScope.throwIfCancelled();
        refreshFormats();
        Epg epg = Epg.create(key, "");
        String date = XmlTvReader.read(new StringReader(xml), null, programme -> {
            EpgData item = getEpgData(programme);
            if (item != null) epg.getList().add(item);
        });
        Date parsed = parse(date);
        epg.setDate(FORMAT_DATE.get().format(parsed == null ? new Date() : parsed));
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
        Map<String, Epg> epgMap = new HashMap<>();
        Map<String, String> srcMap = new HashMap<>();
        String today = FORMAT_DATE.get().format(new Date());
        try (FileInputStream input = new FileInputStream(file)) {
            scanXml(filePass(input), prepareLiveChannels(live), srcMap, (channel, programme) -> {
                Date startDate = parse(programme.start);
                Date endDate = parse(programme.stop);
                if (startDate == null || endDate == null) return false;
                if (!isToday(startDate.getTime(), context) && !isToday(endDate.getTime(), context)) return false;
                String key = channel.getTvgId();
                epgMap.computeIfAbsent(key, k -> Epg.create(k, today)).getList().add(getEpgData(startDate, endDate, programme));
                return true;
            });
        }
        for (Group group : live.getGroups()) {
            for (Channel channel : group.getChannel()) {
                HttpCallScope.throwIfCancelled();
                merge(channel, epgMap.get(channel.getTvgId()), srcMap.get(channel.getTvgId()));
            }
        }
    }

    private interface XmlPass {
        void read(XmlTvReader.Consumer<XmlTvReader.Channel> channels, XmlTvReader.Consumer<XmlTvReader.Programme> programmes) throws Exception;
    }

    private static XmlPass filePass(FileInputStream input) {
        return (channels, programmes) -> {
            // Rewind the same descriptor even if another request replaced the cache file.
            input.getChannel().position(0);
            XmlTvReader.read(new BufferedInputStream(input), channels, programmes);
        };
    }

    private static void scanXml(XmlPass pass, Map<String, Channel> index, Map<String, String> logos,
                                BiPredicate<Channel, XmlTvReader.Programme> accept) throws Exception {
        Map<String, List<XmlTvReader.Channel>> channels = new HashMap<>();
        pass.read(channel -> channels.computeIfAbsent(channel.id, k -> new ArrayList<>()).add(channel), null);
        pass.read(null, programme -> {
            Channel target = findTargetChannel(programme.channel, index, channels);
            if (target == null || !accept.test(target, programme)) return;
            if (logos == null || !channels.containsKey(programme.channel)) return;
            for (XmlTvReader.Channel channel : channels.get(programme.channel)) {
                if (channel.logo.isEmpty()) continue;
                logos.putIfAbsent(target.getTvgId(), channel.logo);
                break;
            }
        });
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

    private static Channel findTargetChannel(String xmlChannelId, Map<String, Channel> liveChannelMap, Map<String, List<XmlTvReader.Channel>> xmlChannelMap) {
        Channel direct = liveChannelMap.get(xmlChannelId);
        if (direct != null) return direct;
        List<XmlTvReader.Channel> channels = xmlChannelMap.get(xmlChannelId);
        if (channels == null) return null;
        for (XmlTvReader.Channel channel : channels) {
            for (String name : channel.names) {
                if (liveChannelMap.containsKey(name)) return liveChannelMap.get(name);
            }
        }
        return null;
    }

    private static Date parse(String value) {
        try {
            return value == null || value.isEmpty() ? null : FORMAT_FULL.get().parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static EpgData getEpgData(XmlTvReader.Programme programme) {
        return getEpgData(parse(programme.start), parse(programme.stop), programme);
    }

    private static EpgData getEpgData(Date startDate, Date endDate, XmlTvReader.Programme programme) {
        if (startDate == null || endDate == null) return null;
        EpgData epgData = new EpgData();
        epgData.setTitle(Trans.s2t(programme.title));
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
