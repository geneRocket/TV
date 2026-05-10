package com.fongmi.android.tv.bean;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.gson.ExtAdapter;
import com.fongmi.android.tv.gson.FilterAdapter;
import com.fongmi.android.tv.gson.MsgAdapter;
import com.fongmi.android.tv.gson.UrlAdapter;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Trans;
import com.google.gson.JsonElement;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import org.json.JSONObject;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Persister;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Root(name = "rss", strict = false)
public class Result implements Parcelable {

    @Path("class")
    @ElementList(entry = "ty", required = false, inline = true)
    @SerializedName("class")
    private List<Class> types;

    @Path("list")
    @ElementList(entry = "video", required = false, inline = true)
    @SerializedName("list")
    private List<Vod> list;

    @SerializedName("filters")
    @JsonAdapter(FilterAdapter.class)
    private LinkedHashMap<String, List<Filter>> filters;

    @SerializedName("url")
    @JsonAdapter(UrlAdapter.class)
    private Url url;

    @SerializedName("msg")
    @JsonAdapter(MsgAdapter.class)
    private String msg;

    @SerializedName("subs")
    private List<Sub> subs;
    @SerializedName("header")
    private JsonElement header;
    @SerializedName("playUrl")
    private String playUrl;
    @SerializedName("jxFrom")
    private String jxFrom;
    @SerializedName("flag")
    private String flag;
    @SerializedName("danmaku")
    @JsonAdapter(ExtAdapter.class)
    private String danmaku;
    @SerializedName("format")
    private String format;
    @SerializedName("click")
    private String click;
    @SerializedName("js")
    private String js;
    private String keyword;
    @SerializedName("key")
    private String key;
    private String requestId;
    private String requestFlag;
    private String requestToken;
    private String requestTypeId;
    private String requestPage;
    private String requestExtend;
    @SerializedName("page")
    private Integer page;
    @SerializedName("pagecount")
    private Integer pagecount;
    @SerializedName("limit")
    private Integer limit;
    @SerializedName("total")
    private Integer total;
    @SerializedName("parse")
    private Integer parse;
    @SerializedName("code")
    private Integer code;
    @SerializedName("jx")
    private Integer jx;
    @SerializedName("drm")
    private Drm drm;

    public static Result objectFrom(String str) {
        try {
            return App.gson().fromJson(str, Result.class);
        } catch (Exception e) {
            return empty();
        }
    }

    public static Result fromJson(String str) {
        Result result = objectFrom(str);
        return result == null ? empty() : result.trans();
    }

    public static Result fromXml(String str) {
        try {
            return new Persister().read(Result.class, str, false).trans();
        } catch (Exception e) {
            return empty();
        }
    }

    public static Result fromType(int type, String str) {
        return type == 0 ? fromXml(str) : fromJson(str);
    }

    public static Result fromObject(JSONObject object) {
        return object == null ? empty() : objectFrom(object.toString());
    }

    public static Result empty() {
        return new Result();
    }

    public static Result error(String msg) {
        Result result = new Result();
        result.setParse(0);
        result.setMsg(msg);
        return result;
    }

    public static Result folder(Vod item) {
        Result result = new Result();
        Class type = new Class();
        type.setTypeFlag("1");
        type.setTypeId(item.getVodId());
        type.setTypeName(item.getVodName());
        result.setTypes(Arrays.asList(type));
        return result;
    }

    public static Result type(String json) {
        Result result = new Result();
        result.setTypes(Arrays.asList(Class.objectFrom(json)));
        return result.trans();
    }

    public static Result list(List<Vod> items) {
        Result result = new Result();
        result.setList(items);
        return result;
    }

    public static Result vod(Vod item) {
        return list(Arrays.asList(item));
    }

    public Result() {
    }

    public List<Class> getTypes() {
        return types == null ? Collections.emptyList() : types;
    }

    public void setTypes(List<Class> types) {
        if (types.size() > 0) this.types = types;
    }

    public List<Vod> getList() {
        return list == null ? Collections.emptyList() : list;
    }

    public void setList(List<Vod> list) {
        this.list = list;
    }

    public LinkedHashMap<String, List<Filter>> getFilters() {
        return filters == null ? new LinkedHashMap<>() : filters;
    }

    public Url getUrl() {
        return url == null ? Url.create() : url;
    }

    public void setUrl(Url url) {
        this.url = url;
    }

    public void setUrl(String url) {
        this.url = getUrl().replace(url);
    }

    public String getMsg() {
        return TextUtils.isEmpty(msg) ? "" : msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public List<Sub> getSubs() {
        return subs == null ? new ArrayList<>() : subs;
    }

    public JsonElement getHeader() {
        return header;
    }

    public void setHeader(JsonElement header) {
        if (getHeader() == null) this.header = header;
    }

    public String getPlayUrl() {
        return TextUtils.isEmpty(playUrl) ? "" : playUrl;
    }

    public void setPlayUrl(String playUrl) {
        this.playUrl = playUrl;
    }

    public String getJxFrom() {
        return TextUtils.isEmpty(jxFrom) ? "" : jxFrom;
    }

    public String getFlag() {
        return TextUtils.isEmpty(flag) ? "" : flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getDanmaku() {
        return TextUtils.isEmpty(danmaku) ? "" : danmaku;
    }

    public List<Danmaku> getDanmakus() {
        return Danmaku.arrayFrom(getDanmaku());
    }

    public void setDanmaku(String danmaku) {
        this.danmaku = danmaku;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getClick() {
        return TextUtils.isEmpty(click) ? "" : click;
    }

    public void setClick(String click) {
        this.click = click;
    }

    public String getJs() {
        return TextUtils.isEmpty(js) ? "" : js;
    }

    public void setJs(String js) {
        this.js = js;
    }

    public String getKeyword() {
        return TextUtils.isEmpty(keyword) ? "" : keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getKey() {
        return TextUtils.isEmpty(key) ? "" : key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getRequestId() {
        return TextUtils.isEmpty(requestId) ? "" : requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getRequestFlag() {
        return TextUtils.isEmpty(requestFlag) ? "" : requestFlag;
    }

    public void setRequestFlag(String requestFlag) {
        this.requestFlag = requestFlag;
    }

    public String getRequestToken() {
        return TextUtils.isEmpty(requestToken) ? "" : requestToken;
    }

    public void setRequestToken(String requestToken) {
        this.requestToken = requestToken;
    }

    public String getRequestTypeId() {
        return TextUtils.isEmpty(requestTypeId) ? "" : requestTypeId;
    }

    public void setRequestTypeId(String requestTypeId) {
        this.requestTypeId = requestTypeId;
    }

    public String getRequestPage() {
        return TextUtils.isEmpty(requestPage) ? "" : requestPage;
    }

    public void setRequestPage(String requestPage) {
        this.requestPage = requestPage;
    }

    public String getRequestExtend() {
        return TextUtils.isEmpty(requestExtend) ? "" : requestExtend;
    }

    public void setRequestExtend(String requestExtend) {
        this.requestExtend = requestExtend;
    }

    public Integer getPageCount() {
        if (pagecount != null && pagecount > 0) return pagecount;
        if (limit != null && limit > 0 && total != null && total >= 0) return (total + limit - 1) / limit;
        return 0;
    }

    public Integer getParse(Integer def) {
        return parse == null ? def : parse;
    }

    public Integer getParse() {
        return getParse(0);
    }

    public void setParse(Integer parse) {
        this.parse = parse;
    }

    public Integer getCode() {
        return code == null ? 0 : code;
    }

    public Integer getJx() {
        return jx == null ? 0 : jx;
    }

    public Drm getDrm() {
        return drm;
    }

    public void setDrm(Drm drm) {
        this.drm = drm;
    }

    public boolean hasMsg() {
        return getMsg().length() > 0;
    }

    public String getRealUrl() {
        return UrlUtil.stripTag(getPlayUrl() + getUrl().v());
    }

    public Map<String, String> getHeaders() {
        Map<String, String> headers = fixHeaders(Json.toMap(getHeader()));
        headers.putAll(UrlUtil.getTagHeaders(getPlayUrl() + getUrl().v()));
        return headers;
    }

    private Map<String, String> fixHeaders(Map<String, String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) result.put(UrlUtil.fixHeader(entry.getKey()), entry.getValue());
        return result;
    }

    public Style getStyle(Style style) {
        return getList().isEmpty() ? Style.rect() : getList().get(0).getStyle(style);
    }

    public Result clear() {
        getList().clear();
        return this;
    }

    public Result copyForVod() {
        Result result = new Result();
        if (this.types != null) {
            result.types = new ArrayList<>(this.types.size());
            for (Class item : this.types) result.types.add(item == null ? null : item.copy());
        }
        if (this.filters != null) {
            result.filters = new LinkedHashMap<>();
            for (Map.Entry<String, List<Filter>> entry : this.filters.entrySet()) {
                result.filters.put(entry.getKey(), Filter.copy(entry.getValue()));
            }
        }
        return result;
    }

    public Result trans() {
        if (Trans.pass()) return this;
        for (Class type : getTypes()) type.trans();
        for (Vod vod : getList()) vod.trans();
        for (Sub sub : getSubs()) sub.trans();
        return this;
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeList(this.types);
        dest.writeTypedList(this.list);
        if (this.filters == null) {
            dest.writeInt(-1);
            return;
        }
        dest.writeInt(this.filters.size());
        for (Map.Entry<String, List<Filter>> entry : this.filters.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeTypedList(entry.getValue());
        }
    }

    protected Result(Parcel in) {
        this.types = new ArrayList<>();
        in.readList(this.types, Class.class.getClassLoader());
        this.list = in.createTypedArrayList(Vod.CREATOR);
        int filterSize = in.readInt();
        if (filterSize >= 0) {
            this.filters = new LinkedHashMap<>();
            for (int i = 0; i < filterSize; i++) {
                String key = in.readString();
                List<Filter> value = in.createTypedArrayList(Filter.CREATOR);
                this.filters.put(key, value == null ? new ArrayList<>() : value);
            }
        }
    }

    public static final Creator<Result> CREATOR = new Creator<>() {
        @Override
        public Result createFromParcel(Parcel source) {
            return new Result(source);
        }

        @Override
        public Result[] newArray(int size) {
            return new Result[size];
        }
    };
}
