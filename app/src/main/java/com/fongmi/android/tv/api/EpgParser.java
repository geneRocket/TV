package com.fongmi.android.tv.api;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Tv;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Trans;

import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EpgParser {

    private static final SimpleDateFormat FORMAT_TIME = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private static final SimpleDateFormat FORMAT_DATE = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat FORMAT_FULL = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault());

    public static boolean start(Live live) throws Exception {
        List<String> xmls = live.getEpgXml();
        if (xmls.isEmpty()) return false;
        boolean success = false;
        for (String url : xmls) {
            try {
                success |= start(live, url);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return success;
    }

    public static boolean start(Live live, String url) throws Exception {
        File file = Path.epg(new File(UrlUtil.path(UrlUtil.uri(url))).getName());
        boolean refresh = shouldRefresh(file);
        if (refresh) Download.create(url, file).sync();
        if (isGzip(file)) readGzip(live, file, refresh);
        else readXml(live, file);
        return true;
    }

    public static Epg getEpg(String xml, String key) throws Exception {
        Tv tv = new Persister().read(Tv.class, xml, false);
        Epg epg = Epg.create(key, FORMAT_DATE.format(getDate(tv)));
        for (Tv.Programme programme : tv.getProgramme()) {
            EpgData item = getEpgData(programme);
            if (item != null) epg.getList().add(item);
        }
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
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        Calendar today = Calendar.getInstance();
        return calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

    private static void readGzip(Live live, File file, boolean refresh) throws Exception {
        File xml = Path.epg(file.getName() + ".xml");
        if (!xml.exists() || refresh) FileUtil.extractGzip(file, xml);
        readXml(live, xml);
    }

    private static void readXml(Live live, File file) throws Exception {
        Map<String, Channel> liveChannelMap = prepareLiveChannels(live);
        Tv tv = new Persister().read(Tv.class, file, false);
        Map<String, List<Tv.Channel>> xmlChannelMap = tv.getChannel().stream().collect(Collectors.groupingBy(Tv.Channel::getId));
        String today = FORMAT_DATE.format(new Date());
        Map<String, Epg> epgMap = new HashMap<>();
        Map<String, String> srcMap = new HashMap<>();
        for (Tv.Programme programme : tv.getProgramme()) {
            Channel channel = findTargetChannel(programme.getChannel(), liveChannelMap, xmlChannelMap);
            if (channel == null) continue;
            Date startDate = parse(programme.getStart());
            Date endDate = parse(programme.getStop());
            if (startDate == null || endDate == null) continue;
            boolean startToday = startDate != null && isToday(startDate.getTime());
            boolean endToday = endDate != null && isToday(endDate.getTime());
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
        live.getGroups().stream().flatMap(group -> group.getChannel().stream()).forEach(channel -> merge(channel, epgMap.get(channel.getTvgId()), srcMap.get(channel.getTvgId())));
    }

    private static Map<String, Channel> prepareLiveChannels(Live live) {
        return live.getGroups().stream()
                .flatMap(group -> group.getChannel().stream())
                .flatMap(channel -> Stream.of(channel.getTvgId(), channel.getTvgName(), channel.getName())
                        .filter(key -> !key.isEmpty())
                        .map(key -> new AbstractMap.SimpleEntry<>(key, channel)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, HashMap::new));
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
            return value == null || value.isEmpty() ? null : FORMAT_FULL.parse(value);
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
        epgData.setStart(FORMAT_TIME.format(startDate));
        epgData.setEnd(FORMAT_TIME.format(endDate));
        epgData.setStartTime(startDate.getTime());
        epgData.setEndTime(endDate == null ? 0 : endDate.getTime());
        if (epgData.getEndTime() < epgData.getStartTime()) epgData.checkDay();
        return epgData;
    }

    private static void merge(Channel channel, Epg epg, String logo) {
        if (epg != null && !epg.getList().isEmpty()) {
            Epg data = channel.getData();
            if (data.getList().isEmpty()) {
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
