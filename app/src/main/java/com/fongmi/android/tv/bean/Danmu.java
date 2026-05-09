package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.github.catvod.utils.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.core.Persister;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

@Root(name = "i", strict = false)
public class Danmu {

    private static final int DEFAULT_TYPE = 1;
    private static final int DEFAULT_SIZE = 25;
    private static final int DEFAULT_COLOR = 16777215;

    @ElementList(entry = "d", required = false, inline = true)
    private List<Data> data;

    public static Danmu from(String str) {
        if (TextUtils.isEmpty(str)) return new Danmu();
        String text = str.trim();
        if (text.startsWith("{") || text.startsWith("[")) return fromJson(text);
        Danmu danmu = fromXml(str);
        if (!danmu.getData().isEmpty()) return danmu;
        danmu = fromGenericXml(str);
        if (!danmu.getData().isEmpty()) return danmu;
        return fromJson(str);
    }

    public static Danmu fromXml(String str) {
        try {
            return new Persister().read(Danmu.class, str);
        } catch (Exception e) {
            return new Danmu();
        }
    }

    private static Danmu fromGenericXml(String str) {
        Danmu danmu = new Danmu();
        danmu.data = new ArrayList<>();
        if (TextUtils.isEmpty(str) || !str.trim().startsWith("<")) return danmu;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(str)));
            collectXml(document.getDocumentElement(), danmu.data);
        } catch (Exception ignored) {
        }
        return danmu;
    }

    private static Danmu fromJson(String str) {
        Danmu danmu = new Danmu();
        danmu.data = new ArrayList<>();
        if (TextUtils.isEmpty(str)) return danmu;
        try {
            collect(Json.parse(str.trim()), danmu.data);
        } catch (Exception ignored) {
        }
        return danmu;
    }

    private static void collect(JsonElement element, List<Data> data) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) collect(item, data);
        } else if (element.isJsonObject()) {
            collectObject(element.getAsJsonObject(), data);
        } else if (element.isJsonPrimitive()) {
            addPrimitive(element.getAsString(), data);
        }
    }

    private static void collectObject(JsonObject object, List<Data> data) {
        Data item = parseObject(object);
        if (item != null) {
            data.add(item);
            return;
        }
        for (String key : new String[]{"comments", "danmaku", "danmus", "danmakus", "items", "list", "data", "result", "barrage_list", "barrageList", "bulletInfos", "bulletInfo"}) {
            JsonElement value = object.get(key);
            if (value != null) collect(value, data);
        }
    }

    private static Data parseObject(JsonObject object) {
        String param = first(object, "p", "param");
        String text = first(object, "m", "text", "content", "message", "msg", "comment", "body");
        if (!TextUtils.isEmpty(param) && !TextUtils.isEmpty(text)) return Data.create(param, text);
        if (TextUtils.isEmpty(text)) return null;
        String time = first(object, "time", "stime", "showTime", "show_time", "timepoint", "timePoint", "time_offset", "timeOffset", "position", "pos", "progress");
        if (TextUtils.isEmpty(time)) return null;
        String type = first(object, "type", "mode");
        String size = first(object, "size", "font", "fontSize", "fontsize");
        String color = first(object, "color", "colour", "fontColor", "font_color", "contentStyle");
        return Data.create(buildParam(time, type, size, color), text);
    }

    private static void collectXml(Element element, List<Data> data) {
        Data item = parseXmlElement(element);
        if (item != null) data.add(item);
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) collectXml((Element) child, data);
        }
    }

    private static Data parseXmlElement(Element element) {
        if ("d".equalsIgnoreCase(element.getTagName()) && !TextUtils.isEmpty(element.getAttribute("p"))) {
            return Data.create(element.getAttribute("p"), element.getTextContent());
        }
        String text = firstXml(element, "text", "content", "message", "msg", "comment", "body");
        if (TextUtils.isEmpty(text)) text = leafText(element);
        if (TextUtils.isEmpty(text)) return null;
        String time = firstXml(element, "time", "stime", "showTime", "show_time", "playTime", "play_time", "timepoint", "timePoint", "time_offset", "timeOffset", "position", "pos", "progress");
        if (TextUtils.isEmpty(time)) return null;
        String type = firstXml(element, "type", "mode");
        String size = firstXml(element, "size", "font", "fontSize", "fontsize");
        String color = firstXml(element, "color", "colour", "fontColor", "font_color");
        return Data.create(buildParam(time, type, size, color), text);
    }

    private static String firstXml(Element element, String... keys) {
        for (String key : keys) {
            String value = element.getAttribute(key);
            if (!TextUtils.isEmpty(value)) return value;
            value = directChildText(element, key);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private static String directChildText(Element element, String name) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) continue;
            if (!name.equalsIgnoreCase(((Element) child).getTagName())) continue;
            String value = child.getTextContent();
            return value == null ? "" : value.trim();
        }
        return "";
    }

    private static String leafText(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) if (children.item(i) instanceof Element) return "";
        return element.getTextContent() == null ? "" : element.getTextContent().trim();
    }

    private static void addPrimitive(String value, List<Data> data) {
        if (TextUtils.isEmpty(value)) return;
        String[] parts = value.split(",", 5);
        if (parts.length < 5) return;
        data.add(Data.create(buildParam(parts[0], parts[1], DEFAULT_SIZE, parts[2]), parts[4]));
    }

    private static String first(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = Json.safeString(object, key);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private static String buildParam(String time, String type, String size, String color) {
        return buildParam(time, type, parseInt(size, DEFAULT_SIZE), color);
    }

    private static String buildParam(String time, String type, int size, String color) {
        return String.format(Locale.US, "%s,%d,%d,%d", normalizeTime(time), normalizeType(type), size, normalizeColor(color));
    }

    private static String normalizeTime(String value) {
        float time = parseFloat(value, 0);
        if (time > 1000) time /= 1000f;
        return String.valueOf(time);
    }

    private static int normalizeType(String value) {
        int type = parseInt(value, DEFAULT_TYPE);
        if (type == 6) return 1;
        if (type == 3) return 1;
        return type;
    }

    private static int normalizeColor(String value) {
        if (TextUtils.isEmpty(value)) return DEFAULT_COLOR;
        String text = value.trim();
        try {
            if (text.startsWith("#")) return Integer.parseInt(text.substring(1), 16);
            if (text.startsWith("0x") || text.startsWith("0X")) return Integer.parseInt(text.substring(2), 16);
            return Integer.parseInt(text);
        } catch (Exception e) {
            return DEFAULT_COLOR;
        }
    }

    private static int parseInt(String value, int def) {
        try {
            return TextUtils.isEmpty(value) ? def : (int) Float.parseFloat(value.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static float parseFloat(String value, float def) {
        try {
            return TextUtils.isEmpty(value) ? def : Float.parseFloat(value.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public List<Data> getData() {
        return data == null ? Collections.emptyList() : data;
    }

    public static class Data {

        @Attribute(name = "p", required = false)
        public String param;

        @Text(required = false)
        public String text;

        public static Data create(String param, String text) {
            Data data = new Data();
            data.param = param;
            data.text = text;
            return data;
        }

        public String getParam() {
            return TextUtils.isEmpty(param) ? "" : param;
        }

        public String getText() {
            return TextUtils.isEmpty(text) ? "" : text;
        }
    }
}
