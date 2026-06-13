package com.fongmi.android.tv.api;

import android.util.Base64;

import androidx.media3.common.MimeTypes;

import com.fongmi.android.tv.bean.Catchup;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.ClearKey;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LiveParser {

    private static final Pattern CATCHUP_SOURCE = Pattern.compile(".*catchup-source=\"(.?|.+?)\".*");
    private static final Pattern CATCHUP = Pattern.compile(".*catchup=\"(.?|.+?)\".*");
    private static final Pattern TVG_NAME = Pattern.compile(".*tvg-name=\"(.?|.+?)\".*");
    private static final Pattern TVG_LOGO = Pattern.compile(".*tvg-logo=\"(.?|.+?)\".*");
    private static final Pattern TVG_URL = Pattern.compile(".*x-tvg-url=\"(.?|.+?)\".*");
    private static final Pattern GROUP = Pattern.compile(".*group-title=\"(.?|.+?)\".*");
    private static final Pattern NAME = Pattern.compile(".*,(.+?)$");

    private static String extract(String line, Pattern pattern) {
        Matcher matcher = pattern.matcher(line.trim());
        if (matcher.matches()) return matcher.group(1);
        return "";
    }

    private static String extractValue(String line, String token) {
        if (line == null || token == null) return "";
        int index = line.toLowerCase().indexOf(token.toLowerCase());
        if (index < 0) return "";
        return line.substring(index + token.length()).trim().replace("\"", "");
    }

    public static void start(Live live) throws Exception {
        if (live.getGroups().size() > 0) return;
        if (live.getType() == 0) text(live, getText(live));
        if (live.getType() == 1) json(live, getText(live));
        if (live.getType() == 3) spider(live, getText(live));
    }

    public static void text(Live live, String text) {
        int number = 0;
        if (live.getGroups().size() > 0) return;
        text = text.replace("\r\n", "\n");
        if (text.contains("#EXTM3U")) m3u(live, text);
        else txt(live, text);
        for (Group group : live.getGroups()) {
            for (Channel channel : group.getChannel()) {
                channel.setNumber(++number);
                channel.live(live);
            }
        }
    }

    private static void json(Live live, String text) {
        live.getGroups().addAll(Group.arrayFrom(text));
        for (Group group : live.getGroups()) {
            for (Channel channel : group.getChannel()) {
                channel.live(live);
            }
        }
    }

    private static void spider(Live live, String text) throws Exception {
        if (text.isEmpty()) text = live.spider().liveContent(live.getUrl());
        if (Json.valid(text)) json(live, text);
        else text(live, text);
    }

    private static void m3u(Live live, String text) {
        Setting setting = Setting.create();
        Catchup catchup = Catchup.create();
        Channel channel = Channel.create("");
        Map<String, Group> groupMap = new HashMap<>();
        Map<Group, Map<String, Channel>> channelMap = new HashMap<>();
        Scanner scanner = new Scanner(text);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (Thread.interrupted()) break;
            if (setting.find(line)) {
                setting.check(line);
            } else if (line.startsWith("#EXTM3U")) {
                catchup.setType(extract(line, CATCHUP));
                catchup.setSource(extract(line, CATCHUP_SOURCE));
                if (live.getEpg().isEmpty()) live.setEpg(extract(line, TVG_URL));
            } else if (line.startsWith("#EXTINF:")) {
                String groupName = extract(line, GROUP);
                Group group = groupMap.get(groupName);
                if (group == null) {
                    group = Group.create(groupName, live.isPass());
                    live.getGroups().add(group);
                    groupMap.put(groupName, group);
                }
                String channelName = extract(line, NAME);
                Map<String, Channel> channels = channelMap.computeIfAbsent(group, k -> new HashMap<>());
                channel = channels.get(channelName);
                if (channel == null) {
                    channel = Channel.create(channelName);
                    group.getChannel().add(channel);
                    channels.put(channelName, channel);
                }
                channel.setTvgName(extract(line, TVG_NAME));
                channel.setLogo(extract(line, TVG_LOGO));
                Catchup unknown = Catchup.create();
                unknown.setType(extract(line, CATCHUP));
                unknown.setSource(extract(line, CATCHUP_SOURCE));
                channel.setCatchup(Catchup.decide(unknown, catchup));
            } else if (!line.startsWith("#") && line.contains("://")) {
                String[] split = line.split("\\|");
                if (split.length > 1) setting.headers(Arrays.copyOfRange(split, 1, split.length));
                channel.getUrls().add(split[0]);
                setting.copy(channel).clear();
            }
        }
    }

    private static void txt(Live live, String text) {
        Setting setting = Setting.create();
        Map<Group, Map<String, Channel>> channelMap = new HashMap<>();
        Scanner scanner = new Scanner(text);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (Thread.interrupted()) break;
            String[] split = line.split(",");
            int index = line.indexOf(",") + 1;
            if (setting.find(line)) setting.check(line);
            if (line.contains("#genre#")) setting.clear();
            if (line.contains("#genre#")) live.getGroups().add(Group.create(split[0], live.isPass()));
            if (split.length > 1 && live.getGroups().isEmpty()) live.getGroups().add(Group.create());
            if (split.length > 1 && split[1].contains("://")) {
                Group group = live.getGroups().get(live.getGroups().size() - 1);
                String channelName = split[0];
                Map<String, Channel> channels = channelMap.computeIfAbsent(group, k -> new HashMap<>());
                Channel channel = channels.get(channelName);
                if (channel == null) {
                    channel = Channel.create(channelName);
                    group.getChannel().add(channel);
                    channels.put(channelName, channel);
                }
                channel.addUrls(line.substring(index).split("#"));
                setting.copy(channel);
            }
        }
    }

    private static String getText(Live live) {
        return getText(live.getUrl(), live.getHeaders());
    }

    private static String getText(String url, Map<String, String> header) {
        if (url == null) return "";
        Map<String, String> headers = new HashMap<>();
        if (header != null) headers.putAll(header);
        headers.putAll(UrlUtil.getTagHeaders(url));
        String clean = UrlUtil.stripTag(url.trim());
        if (clean.startsWith("file")) return Path.read(clean);
        url = UrlUtil.stripTag(UrlUtil.normalize(url, ""));
        if (url.startsWith("file")) return Path.read(url);
        if (url.startsWith("http")) return OkHttp.string(url, headers);
        if (url.startsWith("assets") || url.startsWith("proxy")) return getText(UrlUtil.convert(url), headers);
        if (url.length() > 0 && url.length() % 4 == 0) return getText(new String(Base64.decode(url, Base64.DEFAULT)), headers);
        return "";
    }

    private static class Setting {

        private String ua;
        private String key;
        private String type;
        private String click;
        private String format;
        private String origin;
        private String referer;
        private Integer parse;
        private Map<String, String> header;

        public static Setting create() {
            return new Setting();
        }

        public boolean find(String line) {
            return line.startsWith("ua") || line.startsWith("parse") || line.startsWith("click") || line.startsWith("header") || line.startsWith("format") || line.startsWith("origin") || line.startsWith("referer") || line.startsWith("#EXTHTTP:") || line.startsWith("#EXTVLCOPT:") || line.startsWith("#KODIPROP:");
        }

        public void check(String line) {
            if (line.startsWith("ua")) ua(line);
            else if (line.startsWith("parse")) parse(line);
            else if (line.startsWith("click")) click(line);
            else if (line.startsWith("header")) header(line);
            else if (line.startsWith("format")) format(line);
            else if (line.startsWith("origin")) origin(line);
            else if (line.startsWith("referer")) referer(line);
            else if (line.startsWith("#EXTHTTP:")) header(line);
            else if (line.startsWith("#EXTVLCOPT:http-origin")) origin(line);
            else if (line.startsWith("#EXTVLCOPT:http-user-agent")) ua(line);
            else if (line.startsWith("#EXTVLCOPT:http-referrer")) referer(line);
            else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_key")) key(line);
            else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_type")) type(line);
            else if (line.startsWith("#KODIPROP:inputstream.adaptive.manifest_type")) format(line);
            else if (line.startsWith("#KODIPROP:inputstream.adaptive.stream_headers")) headers(line);
        }

        public Setting copy(Channel channel) {
            if (ua != null) channel.setUa(ua);
            if (parse != null) channel.setParse(parse);
            if (click != null) channel.setClick(click);
            if (format != null) channel.setFormat(format);
            if (origin != null) channel.setOrigin(origin);
            if (referer != null) channel.setReferer(referer);
            if (header != null) channel.setHeader(header);
            if (key != null && type != null) channel.setDrm(Drm.create(key, type));
            return this;
        }

        private void ua(String line) {
            try {
                if (line.toLowerCase().contains("user-agent=")) ua = extractValue(line, "user-agent=");
                if (line.contains("ua=")) ua = extractValue(line, "ua=");
            } catch (Exception e) {
                ua = null;
            }
        }

        private void referer(String line) {
            try {
                referer = extractValue(line, "referer=");
            } catch (Exception e) {
                referer = null;
            }
        }

        private void parse(String line) {
            try {
                parse = Integer.parseInt(extractValue(line, "parse="));
            } catch (Exception e) {
                parse = null;
            }
        }

        private void click(String line) {
            try {
                click = extractValue(line, "click=");
            } catch (Exception e) {
                click = null;
            }
        }

        private void format(String line) {
            try {
                if (line.startsWith("format=")) format = extractValue(line, "format=");
                if (line.contains("manifest_type=")) format = extractValue(line, "manifest_type=");
                if ("mpd".equals(format) || "dash".equals(format)) format = MimeTypes.APPLICATION_MPD;
                if ("hls".equals(format)) format = MimeTypes.APPLICATION_M3U8;
            } catch (Exception e) {
                format = null;
            }
        }

        private void origin(String line) {
            try {
                origin = extractValue(line, "origin=");
            } catch (Exception e) {
                origin = null;
            }
        }

        private void key(String line) {
            try {
                key = extractValue(line, "license_key=");
                if (!key.startsWith("http")) convert();
            } catch (Exception e) {
                key = null;
            }
        }

        private void type(String line) {
            try {
                type = extractValue(line, "license_type=");
            } catch (Exception e) {
                type = null;
            }
        }

        private void header(String line) {
            try {
                if (line.contains("#EXTHTTP:")) header = Json.toMap(Json.parse(extractValue(line, "#EXTHTTP:")));
                if (line.contains("header=")) header = Json.toMap(Json.parse(extractValue(line, "header=")));
            } catch (Exception e) {
                header = null;
            }
        }

        private void headers(String line) {
            try {
                headers(extractValue(line, "headers=").split("&"));
            } catch (Exception ignored) {
            }
        }

        private void headers(String[] params) {
            if (header == null) header = new HashMap<>();
            for (String param : params) {
                int index = param.indexOf('=');
                if (index <= 0 || index >= param.length() - 1) continue;
                String key = param.substring(0, index).trim();
                String value = param.substring(index + 1).trim().replace("\"", "");
                if (key.isEmpty()) continue;
                header.put(key, value);
            }
        }

        private void convert() {
            try {
                ClearKey.objectFrom(key);
            } catch (Exception e) {
                key = ClearKey.get(key.replace("\"", "").replace("{", "").replace("}", "")).toString();
            }
        }

        private void clear() {
            ua = null;
            key = null;
            type = null;
            parse = null;
            click = null;
            header = null;
            format = null;
            origin = null;
            referer = null;
        }
    }
}
