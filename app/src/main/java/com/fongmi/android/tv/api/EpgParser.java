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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.Comparator;

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
                result.getList().addAll(readDay(xmlFile(url), live, channel, date, zone).getList());
            } catch (Exception error) {
                HttpCallScope.throwIfCancelled();
                error.printStackTrace();
            }
        }
        result.setList(new ArrayList<>(new LinkedHashSet<>(result.getList())));
        result.getList().sort(Comparator.comparingLong(EpgData::getStartTime));
        return result;
    }

    static Epg readDay(File file, Live live, Channel channel, String date, TimeZone zone) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            return readDay(filePass(input), live, channel, date, zone, false);
        }
    }

    public static Epg readDay(String xml, Live live, Channel channel, String date, TimeZone zone) throws Exception {
        return readDay(xml, live, channel, date, zone, false);
    }

    public static Epg readDay(String xml, Live live, Channel channel, String date, TimeZone zone,
                              boolean allowUnmatchedChannel) throws Exception {
        return readDay((channels, programmes) -> XmlTvReader.read(new StringReader(xml), channels, programmes),
                live, channel, date, zone, allowUnmatchedChannel);
    }

    private static Epg readDay(XmlPass pass, Live live, Channel channel, String date, TimeZone zone,
                               boolean allowUnmatchedChannel) throws Exception {
        refreshFormats();
        Epg result = Epg.create(channel.getTvgId(), date);
        EpgDay day = new EpgDay(date, zone);
        SimpleDateFormat time = createFormat("HH:mm", Locale.getDefault(), zone);
        Map<String, Map<String, Channel>> index = prepareLiveChannels(live, channel);
        SingleChannelGuide fallback = allowUnmatchedChannel ? new SingleChannelGuide(day, time) : null;
        scanXml(pass, index, null, (target, programme) -> {
            if (!channel.getTvgId().equals(target.getTvgId())) return false;
            EpgData item = programmeForDay(programme, day, time);
            if (item == null) return false;
            result.getList().add(item);
            return true;
        }, fallback);
        if (fallback != null && result.getList().isEmpty()) result.getList().addAll(fallback.programmes);
        result.getList().sort(Comparator.comparingLong(EpgData::getStartTime));
        return result;
    }

    private static EpgData programmeForDay(XmlTvReader.Programme programme, EpgDay day, SimpleDateFormat time) {
        EpgData item = getEpgData(programme);
        if (item == null || !day.contains(item)) return null;
        item.setStart(time.format(new Date(item.getStartTime())));
        item.setEnd(time.format(new Date(item.getEndTime())));
        return item;
    }

    private static final class SingleChannelGuide {
        private final EpgDay day;
        private final SimpleDateFormat time;
        private final List<EpgData> programmes = new ArrayList<>();
        private String id;
        private boolean rejected;

        SingleChannelGuide(EpgDay day, SimpleDateFormat time) {
            this.day = day;
            this.time = time;
        }

        void observe(XmlTvReader.Programme programme, boolean knownChannel) {
            if (rejected) return;
            if (knownChannel || (id != null && !id.equals(programme.channel))) {
                rejected = true;
                programmes.clear();
                return;
            }
            id = programme.channel;
            EpgData item = programmeForDay(programme, day, time);
            if (item != null) programmes.add(item);
        }
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
            scanXml(filePass(input), prepareLiveChannels(live, null), srcMap, (channel, programme) -> {
                Date startDate = parse(programme.start);
                Date endDate = parse(programme.stop);
                if (startDate == null || endDate == null) return false;
                if (!isToday(startDate.getTime(), context) && !isToday(endDate.getTime(), context)) return false;
                String key = channel.getTvgId();
                epgMap.computeIfAbsent(key, k -> Epg.create(k, today)).getList().add(getEpgData(startDate, endDate, programme));
                return true;
            }, null);
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

    private static void scanXml(XmlPass pass, Map<String, Map<String, Channel>> index, Map<String, String> logos,
                                BiPredicate<Channel, XmlTvReader.Programme> accept,
                                SingleChannelGuide fallback) throws Exception {
        Map<String, List<XmlTvReader.Channel>> channels = new HashMap<>();
        pass.read(channel -> channels.computeIfAbsent(channel.id, k -> new ArrayList<>()).add(channel), null);
        pass.read(null, programme -> {
            Collection<Channel> targets = findTargetChannels(programme.channel, index, channels);
            if (fallback != null) fallback.observe(programme, !targets.isEmpty());
            for (Channel target : targets) {
                if (!accept.test(target, programme) || logos == null) continue;
                for (XmlTvReader.Channel channel : channels.getOrDefault(programme.channel, Collections.emptyList())) {
                    if (channel.logo.isEmpty()) continue;
                    logos.putIfAbsent(target.getTvgId(), channel.logo);
                    break;
                }
            }
        });
    }

    private static Map<String, Map<String, Channel>> prepareLiveChannels(Live live, Channel selected) {
        List<Channel> channels = new ArrayList<>();
        if (live != null) for (Group group : live.getGroups()) channels.addAll(group.getChannel());
        if (selected != null) channels.add(selected);
        Map<String, Map<String, Channel>> index = new HashMap<>();
        // Register every provider ID before aliases, regardless of playlist order.
        for (Channel channel : channels) {
            if (!channel.hasTvgId()) continue;
            String id = channel.getTvgId();
            index.putIfAbsent(id, Collections.singletonMap(id, channel));
        }
        Set<String> exactIds = new LinkedHashSet<>(index.keySet());
        for (Channel channel : channels) {
            for (String alias : new String[]{channel.getTvgId(), channel.getTvgName(), channel.getName()}) {
                if (alias.isEmpty() || exactIds.contains(alias)) continue;
                index.computeIfAbsent(alias, value -> new HashMap<>()).putIfAbsent(channel.getTvgId(), channel);
            }
        }
        return index;
    }

    private static Collection<Channel> findTargetChannels(String xmlChannelId, Map<String, Map<String, Channel>> liveChannelMap,
                                                           Map<String, List<XmlTvReader.Channel>> xmlChannelMap) {
        Map<String, Channel> matches = liveChannelMap.get(xmlChannelId);
        if (matches != null) return matches.values();
        for (XmlTvReader.Channel channel : xmlChannelMap.getOrDefault(xmlChannelId, Collections.emptyList())) {
            for (String name : channel.names) {
                matches = liveChannelMap.get(name);
                if (matches != null) return matches.values();
            }
        }
        return Collections.emptyList();
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
