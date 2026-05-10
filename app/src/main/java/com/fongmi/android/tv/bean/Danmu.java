package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
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
            Data item = parseArray(element.getAsJsonArray());
            if (item != null) {
                data.add(item);
                return;
            }
            for (JsonElement child : element.getAsJsonArray()) collect(child, data);
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
        for (String key : new String[]{"comments", "danmuku", "danmukuList", "danmaku", "danmakus", "danmus", "danmu", "danmuList", "danmu_list", "items", "list", "rows", "data", "result", "barrage", "barrages", "barrage_list", "barrageList", "bulletInfos", "bulletInfo"}) {
            JsonElement value = object.get(key);
            if (value != null) collect(value, data);
        }
    }

    private static Data parseArray(JsonArray array) {
        if (array.size() < 4 || !isPrimitive(array, 0) || !isPrimitive(array, 1)) return null;
        String time = at(array, 0);
        if (!isNumber(time)) return null;
        String type = at(array, 1);
        String text;
        String size;
        String color;
        if (array.size() >= 5 && !isNumber(type)) {
            color = at(array, 2);
            size = array.size() >= 7 ? at(array, array.size() - 1) : at(array, 3);
            text = at(array, 4);
        } else if (array.size() >= 5) {
            size = at(array, 2);
            color = at(array, 3);
            text = at(array, 4);
        } else {
            size = String.valueOf(DEFAULT_SIZE);
            color = at(array, 2);
            text = at(array, 3);
        }
        if (TextUtils.isEmpty(text)) return null;
        return Data.create(buildParam(time, type, size, color), text);
    }

    private static Data parseObject(JsonObject object) {
        String param = first(object, "p", "param");
        String text = first(object, "m", "text", "content", "message", "msg", "comment", "body", "value", "word", "danmaku", "danmu", "dm");
        if (!TextUtils.isEmpty(param) && !TextUtils.isEmpty(text)) return Data.create(param, text);
        if (TextUtils.isEmpty(text)) return null;
        TimeField time = firstTime(object);
        if (TextUtils.isEmpty(time.value)) return null;
        String type = first(object, "type", "mode", "positionType");
        String size = first(object, "size", "font", "fontSize", "fontsize");
        String color = first(object, "color", "colour", "fontColor", "font_color", "contentStyle");
        return Data.create(buildParam(time.value, time.milliseconds, type, size, color), text);
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
        TimeField time = firstTime(element);
        if (TextUtils.isEmpty(time.value)) return null;
        String type = firstXml(element, "type", "mode");
        String size = firstXml(element, "size", "font", "fontSize", "fontsize");
        String color = firstXml(element, "color", "colour", "fontColor", "font_color");
        return Data.create(buildParam(time.value, time.milliseconds, type, size, color), text);
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
        String[] parts = value.split(",", 9);
        if (parts.length < 5) return;
        if (!isNumber(parts[1])) {
            String size = parts.length >= 7 ? parts[parts.length - 1] : parts[3];
            data.add(Data.create(buildParam(parts[0], parts[1], size, parts[2]), parts[4]));
        } else {
            String text = parts.length >= 9 ? parts[8] : parts[4];
            data.add(Data.create(buildParam(parts[0], parts[1], parts[2], parts[3]), text));
        }
    }

    private static String first(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = Json.safeString(object, key);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private static TimeField firstTime(JsonObject object) {
        for (String key : new String[]{"time", "stime", "showTime", "show_time", "playTime", "play_time", "timepoint", "timePoint", "videoTime", "at", "ts"}) {
            String value = Json.safeString(object, key);
            if (!TextUtils.isEmpty(value)) return new TimeField(value, false);
        }
        for (String key : new String[]{"time_offset", "timeOffset", "position", "pos", "progress", "vpos"}) {
            String value = Json.safeString(object, key);
            if (!TextUtils.isEmpty(value)) return new TimeField(value, true);
        }
        return TimeField.EMPTY;
    }

    private static TimeField firstTime(Element element) {
        for (String key : new String[]{"time", "stime", "showTime", "show_time", "playTime", "play_time", "timepoint", "timePoint"}) {
            String value = firstXml(element, key);
            if (!TextUtils.isEmpty(value)) return new TimeField(value, false);
        }
        for (String key : new String[]{"time_offset", "timeOffset", "position", "pos", "progress", "vpos"}) {
            String value = firstXml(element, key);
            if (!TextUtils.isEmpty(value)) return new TimeField(value, true);
        }
        return TimeField.EMPTY;
    }

    private static String buildParam(String time, String type, String size, String color) {
        return buildParam(time, false, type, size, color);
    }

    private static String buildParam(String time, boolean milliseconds, String type, String size, String color) {
        return buildParam(time, milliseconds, type, parseSize(size), color);
    }

    private static String buildParam(String time, boolean milliseconds, String type, int size, String color) {
        return String.format(Locale.US, "%s,%d,%d,%d", normalizeTime(time, milliseconds), normalizeType(type), size, normalizeColor(color));
    }

    private static String normalizeTime(String value, boolean milliseconds) {
        float time = parseFloat(value, 0);
        if (milliseconds) time /= 1000f;
        return String.valueOf(time);
    }

    private static int normalizeType(String value) {
        if (!TextUtils.isEmpty(value)) {
            String text = value.trim().toLowerCase(Locale.US);
            if (text.contains("top")) return 5;
            if (text.contains("bottom") || text.contains("btm")) return 4;
            if (text.contains("right") || text.contains("scroll") || text.contains("normal") || text.contains("rtl")) return 1;
        }
        int type = parseInt(value, DEFAULT_TYPE);
        if (type == 6) return 1;
        if (type == 3) return 1;
        return type;
    }

    private static int normalizeColor(String value) {
        if (TextUtils.isEmpty(value)) return DEFAULT_COLOR;
        String text = value.trim();
        try {
            if (text.startsWith("#")) {
                String hex = text.substring(1);
                if (hex.length() == 3) hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
                return Integer.parseInt(hex, 16);
            }
            if (text.startsWith("0x") || text.startsWith("0X")) return (int) (Long.parseLong(text.substring(2), 16) & 0x00ffffff);
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

    private static int parseSize(String value) {
        if (TextUtils.isEmpty(value)) return DEFAULT_SIZE;
        String text = value.trim().toLowerCase(Locale.US);
        if (text.endsWith("px")) text = text.substring(0, text.length() - 2);
        return parseInt(text, DEFAULT_SIZE);
    }

    private static float parseFloat(String value, float def) {
        try {
            return TextUtils.isEmpty(value) ? def : Float.parseFloat(value.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean isNumber(String value) {
        if (TextUtils.isEmpty(value)) return false;
        try {
            Float.parseFloat(value.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isPrimitive(JsonArray array, int index) {
        return array.size() > index && array.get(index) != null && array.get(index).isJsonPrimitive();
    }

    private static String at(JsonArray array, int index) {
        return isPrimitive(array, index) ? array.get(index).getAsString() : "";
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

    private static class TimeField {

        private static final TimeField EMPTY = new TimeField("", false);

        private final String value;
        private final boolean milliseconds;

        private TimeField(String value, boolean milliseconds) {
            this.value = value;
            this.milliseconds = milliseconds;
        }
    }
}
